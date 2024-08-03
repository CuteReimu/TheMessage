package com.fengsheng.network

import akka.actor.ActorRef
import com.fengsheng.*
import com.fengsheng.handler.ProtoHandler
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Role
import com.fengsheng.protos.leaveRoomToc
import com.google.protobuf.Descriptors
import com.google.protobuf.GeneratedMessage
import com.google.protobuf.Parser
import com.google.protobuf.util.JsonFormat
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import org.apache.commons.text.CaseUtils.toCamelCase
import org.apache.logging.log4j.kotlin.logger
import java.lang.reflect.InvocationTargetException
import java.net.SocketException

class WebSocketServerChannelHandler : SimpleChannelInboundHandler<WebSocketFrame>() {
    @Throws(Exception::class)
    public override fun channelRead0(ctx: ChannelHandlerContext, webSocketFrame: WebSocketFrame) {
        if (webSocketFrame !is BinaryWebSocketFrame) {
            logger.debug("仅支持二进制消息，不支持文本消息")
            throw UnsupportedOperationException("${webSocketFrame.javaClass.name} frame types not supported")
        }
        val msg = webSocketFrame.content()
        val protoNameLen = msg.readShortLE()
        if (msg.readableBytes() < protoNameLen) {
            logger.error("incorrect proto name length: $protoNameLen")
            ctx.close()
            return
        }
        val protoNameBuf = ByteArray(protoNameLen.toInt())
        msg.readBytes(protoNameBuf)
        val protoName = String(protoNameBuf)
        val protoInfo = ProtoInfoMap[protoName]
        if (protoInfo == null) {
            logger.error("incorrect msg, proto name: $protoName")
            ctx.close()
            return
        }
        val buf = ByteArray(msg.readableBytes())
        msg.readBytes(buf)
        val message = protoInfo.parser.parseFrom(buf) as GeneratedMessage
        if ("heart_tos" != protoName && "auto_play_tos" != protoName) {
            logger.debug(
                "recv@%s len: %d %s | %s".format(
                    ctx.channel().id().asShortText(), buf.size, protoName,
                    printer.print(message).replace("\n *".toRegex(), " ")
                )
            )
        }
        val player = Game.playerCache[ctx.channel().id().asLongText()]
        if (!player!!.limiter.allow()) {
            logger.error("recv msg too fast: ${ctx.channel().id().asShortText()}")
            ctx.close()
            return
        }
        protoInfo.handler.handle(player, message)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        logger.info("session connected: ${channel.id().asShortText()} ${channel.remoteAddress()}")
        val player = HumanPlayer(channel, true) { protoName: String, buf: ByteArray ->
            val protoNameBuf = protoName.toByteArray()
            val totalLen = 2 + protoNameBuf.size + buf.size
            val byteBuf = Unpooled.buffer(totalLen)
            byteBuf.writeShortLE(protoNameBuf.size)
            byteBuf.writeBytes(protoNameBuf)
            byteBuf.writeBytes(buf)
            BinaryWebSocketFrame(byteBuf)
        }
        if (Game.playerCache.putIfAbsent(channel.id().asLongText(), player) != null) {
            logger.error("already assigned channel id: ${channel.id().asLongText()}")
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        logger.info("session closed: ${channel.id().asShortText()} ${channel.remoteAddress()}")
        val player = Game.playerCache.remove(channel.id().asLongText())
        if (player == null) {
            logger.error("already unassigned channel id: ${channel.id().asLongText()}")
            return
        }
        val game = player.game ?: return
        GameExecutor.post(game) {
            if (player.game !== game) return@post
            if (game.isStarted) {
                if (game.players.all { it !is HumanPlayer || !it.isActive } && Config.IsGmEnable)
                    game.end(null, null)
                else player.notifyPlayerUpdateStatus()
            } else {
                logger.info("${player.playerName}离开了房间")
                game.players = game.players.toMutableList().apply { set(player.location, null) }
                player.game = null
                Game.playerNameCache.remove(player.playerName, player)
                val reply = leaveRoomToc { position = player.location }
                game.players.send { reply }
                game.cancelStartTimer()
                if (game.players.all { it !is HumanPlayer })
                    game.actorRef.tell(StopGameActor(game), ActorRef.noSender())
            }
        }
    }

    @Deprecated("Deprecated in Java")
    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause is SocketException && "Connection reset" == cause.message) return
        super.exceptionCaught(ctx, cause)
    }

    private data class ProtoInfo(val name: String, val parser: Parser<*>, val handler: ProtoHandler)

    companion object {
        private val printer = JsonFormat.printer().alwaysPrintFieldsWithNoPresence()
        private val ProtoInfoMap = HashMap<String, ProtoInfo>()

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
                val className = "${protoCls.name}$$name"
                val cls = protoCls.classLoader.loadClass(className)
                val parser = cls.getDeclaredMethod("parser").invoke(null) as Parser<*>
                try {
                    val handlerClassName = toCamelCase(name, true, '_')
                    val handlerClass = protoCls.classLoader.loadClass("com.fengsheng.handler.$handlerClassName")
                    val handler = handlerClass.getDeclaredConstructor().newInstance() as ProtoHandler
                    if (ProtoInfoMap.putIfAbsent(name, ProtoInfo(name, parser, handler)) != null) {
                        throw RuntimeException("Duplicate message meta register by name: $name")
                    }
                } catch (ignored: ClassNotFoundException) {
                }
            }
        }
    }
}
