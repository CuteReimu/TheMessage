package com.fengsheng.network

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.handler.ProtoHandler
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.leave_room_toc
import com.fengsheng.protos.Role
import com.google.protobuf.Descriptors
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.Parser
import com.google.protobuf.TextFormat
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import org.apache.log4j.Logger
import java.lang.reflect.InvocationTargetException
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap

class WebSocketServerChannelHandler : SimpleChannelInboundHandler<WebSocketFrame>() {
    @Throws(Exception::class)
    public override fun channelRead0(ctx: ChannelHandlerContext, webSocketFrame: WebSocketFrame) {
        log.debug("收到消息$webSocketFrame")
        if (webSocketFrame !is BinaryWebSocketFrame) {
            log.debug("仅支持二进制消息，不支持文本消息")
            throw UnsupportedOperationException("${webSocketFrame.javaClass.name} frame types not supported")
        }
        val msg = webSocketFrame.content()
        val protoNameLen = msg.readShortLE()
        if (msg.readableBytes() < protoNameLen) {
            log.error("incorrect proto name length: $protoNameLen")
            ctx.close()
            return
        }
        val protoNameBuf = ByteArray(protoNameLen.toInt())
        msg.readBytes(protoNameBuf)
        val protoName = String(protoNameBuf)
        val protoInfo = ProtoInfoMap[protoName]
        if (protoInfo == null) {
            log.error("incorrect msg, proto name: $protoName")
            ctx.close()
            return
        }
        val buf = ByteArray(msg.readableBytes())
        msg.readBytes(buf)
        val message = protoInfo.parser.parseFrom(buf) as GeneratedMessageV3
        if ("heart_tos" != protoName && "auto_play_tos" != protoName) {
            log.debug(
                "recv@%s len: %d %s | %s".format(
                    ctx.channel().id().asShortText(), buf.size, protoName,
                    printer.printToString(message).replace("\n *".toRegex(), " ")
                )
            )
        }
        val player = playerCache[ctx.channel().id().asLongText()]
        if (!player!!.limiter.allow()) {
            log.error("recv msg too fast: ${ctx.channel().id().asShortText()}")
            ctx.close()
            return
        }
        protoInfo.handler.handle(player, message)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        log.info("session connected: ${channel.id().asShortText()} ${channel.remoteAddress()}")
        val player = HumanPlayer(channel) { protoName: String, buf: ByteArray ->
            val protoNameBuf = protoName.toByteArray()
            val totalLen = 2 + protoNameBuf.size + buf.size
            val byteBuf = Unpooled.buffer(totalLen)
            byteBuf.writeShortLE(protoNameBuf.size)
            byteBuf.writeBytes(protoNameBuf)
            byteBuf.writeBytes(buf)
            BinaryWebSocketFrame(byteBuf)
        }
        if (playerCache.putIfAbsent(channel.id().asLongText(), player) != null) {
            log.error("already assigned channel id: ${channel.id().asLongText()}")
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        log.info("session closed: ${channel.id().asShortText()} ${channel.remoteAddress()}")
        val player = playerCache.remove(channel.id().asLongText())
        if (player == null) {
            log.error("already unassigned channel id: " + channel.id().asLongText())
            return
        }
        val game = player.game ?: return
        GameExecutor.post(game) {
            if (game.isStarted) {
                if (game.players.all { it !is HumanPlayer || !it.isActive })
                    game.end(null)
            } else {
                log.info("${player.playerName}离开了房间")
                game.players[player.location] = null
                Game.deviceCache.remove(player.device!!, player)
                val reply = leave_room_toc.newBuilder().setPosition(player.location).build()
                game.players.forEach { (it as? HumanPlayer)?.send(reply) }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause is SocketException && "Connection reset" == cause.message) return
        @Suppress("DEPRECATION")
        super.exceptionCaught(ctx, cause)
    }

    private data class ProtoInfo(val name: String, val parser: Parser<*>, val handler: ProtoHandler)

    companion object {
        private val log = Logger.getLogger(WebSocketServerChannelHandler::class.java)
        private val printer = TextFormat.printer().escapingNonAscii(false)
        private val ProtoInfoMap = HashMap<String, ProtoInfo>()
        private val playerCache = ConcurrentHashMap<String, HumanPlayer>()

        init {
            try {
                initProtocols(Fengsheng::class.java)
                initProtocols(Role::class.java)
            } catch (e: InvocationTargetException) {
                throw RuntimeException(e)
            } catch (e: NoSuchMethodException) {
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            } catch (e: ClassNotFoundException) {
                throw RuntimeException(e)
            } catch (e: InstantiationException) {
                throw RuntimeException(e)
            }
        }

        @Throws(
            NoSuchMethodException::class,
            InvocationTargetException::class,
            IllegalAccessException::class,
            ClassNotFoundException::class,
            InstantiationException::class
        )
        private fun initProtocols(protoCls: Class<*>) {
            val descriptor = protoCls.getDeclaredMethod("getDescriptor").invoke(null) as Descriptors.FileDescriptor
            for (d in descriptor.messageTypes) {
                val name = d.name
                if (!name.endsWith("_tos")) continue
                val id: Short = stringHash(name)
                if (id.toInt() == 0) {
                    throw RuntimeException("message meta require 'ID' field: $name")
                }
                val className = protoCls.name + "$" + name
                val cls = protoCls.classLoader.loadClass(className)
                val parser = cls.getDeclaredMethod("parser").invoke(null) as Parser<*>
                try {
                    val handlerClass = protoCls.classLoader.loadClass("com.fengsheng.handler.$name")
                    val handler = handlerClass.getDeclaredConstructor().newInstance() as ProtoHandler
                    if (ProtoInfoMap.putIfAbsent(name, ProtoInfo(name, parser, handler)) != null) {
                        throw RuntimeException("Duplicate message meta register by id: $id")
                    }
                } catch (ignored: ClassNotFoundException) {
                }
            }
        }

        fun exchangePlayer(oldPlayer: HumanPlayer, newPlayer: HumanPlayer) {
            oldPlayer.channel = newPlayer.channel
            if (playerCache.put(newPlayer.channel.id().asLongText(), oldPlayer) == null) {
                log.error("channel [id: ${newPlayer.channel.id().asLongText()}] not exists")
            }
        }

        private fun stringHash(s: String): Short {
            var hash = 0
            for (c in s.toByteArray()) {
                val i = if (c >= 0) c.toInt() else 256 + c
                hash = (hash + (hash shl 5) + i + (i shl 7)).toShort().toInt()
            }
            return hash.toShort()
        }
    }
}