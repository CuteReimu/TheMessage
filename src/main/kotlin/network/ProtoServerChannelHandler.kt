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
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.apache.log4j.Logger
import java.lang.reflect.InvocationTargetException
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class ProtoServerChannelHandler : SimpleChannelInboundHandler<ByteBuf>() {
    override fun channelActive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        log.info(
            "session connected: ${channel.id().asShortText()} ${channel.remoteAddress()}"
        )
        val player = HumanPlayer(channel)
        if (playerCache.putIfAbsent(channel.id().asLongText(), player) != null) {
            log.error("already assigned channel id: ${channel.id().asLongText()}")
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        log.info(
            "session closed: " + channel.id().asShortText() + " " + channel.remoteAddress()
        )
        val player = playerCache.remove(channel.id().asLongText())
        if (player == null) {
            log.error("already unassigned channel id: " + channel.id().asLongText())
            return
        }
        val game = player.game ?: return
        var reply: GeneratedMessageV3? = null
        synchronized(Game::class.java) {
            if (game.isStarted) {
                GameExecutor.post(game) {
                    for (p in game.players) {
                        if (p is HumanPlayer && p.isActive) return@post
                    }
                    game.end(null)
                }
            } else {
                log.info(player.playerName + "离开了房间")
                game.players[player.location] = null
                Game.deviceCache.remove(player.device!!, player)
                reply = leave_room_toc.newBuilder().setPosition(player.location).build()
            }
        }
        if (reply != null) {
            for (p in game.players) {
                (p as? HumanPlayer)?.send(reply!!)
            }
        }
    }

    @Throws(Exception::class)
    public override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        val msgLen = msg.readableBytes()
        if (msgLen < 2) {
            log.error("incorrect msgLen: " + msg.readableBytes())
            ctx.close()
            return
        }
        val id = msg.readShortLE()
        val protoInfo = ProtoInfoMap[id]
        if (protoInfo == null) {
            log.error("incorrect msg id: $id")
            ctx.close()
            return
        }
        val buf = ByteArray(msgLen - 2)
        msg.readBytes(buf)
        val message = protoInfo.parser.parseFrom(buf) as GeneratedMessageV3
        if (id != heartMsgId && id != autoPlayMsgId) {
            log.debug(
                "recv@%s len: %d %s | %s".formatted(
                    ctx.channel().id().asShortText(), msgLen - 2, protoInfo.name,
                    printer.printToString(message).replace("\n *".toRegex(), " ")
                )
            )
        }
        val player = playerCache[ctx.channel().id().asLongText()]!!
        if (!player.limiter.allow()) {
            log.error("recv msg too fast: " + ctx.channel().id().asShortText())
            ctx.close()
            return
        }
        val handler = protoInfo.handler
        handler.handle(player, message)
    }

    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause is SocketException && "Connection reset" == cause.message) return
        super.exceptionCaught(ctx, cause)
    }

    private data class ProtoInfo(val name: String, val parser: Parser<*>, val handler: ProtoHandler)

    companion object {
        private val log = Logger.getLogger(ProtoServerChannelHandler::class.java)
        private val printer = TextFormat.printer().escapingNonAscii(false)
        private val ProtoInfoMap = HashMap<Short, ProtoInfo>()
        private val playerCache: ConcurrentMap<String, HumanPlayer> = ConcurrentHashMap()
        private val heartMsgId: Short = stringHash("heart_tos")
        private val autoPlayMsgId: Short = stringHash("auto_play_tos")
        fun exchangePlayer(oldPlayer: HumanPlayer, newPlayer: HumanPlayer) {
            oldPlayer.channel = newPlayer.channel
            if (playerCache.put(
                    newPlayer.channel.id().asLongText(),
                    oldPlayer
                ) == null
            ) {
                log.error(
                    "channel [id: " + newPlayer.channel.id().asLongText() + "] not exists"
                )
            }
        }

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
                    if (ProtoInfoMap.putIfAbsent(id, ProtoInfo(name, parser, handler)) != null) {
                        throw RuntimeException("Duplicate message meta register by id: $id")
                    }
                } catch (ignored: ClassNotFoundException) {
                }

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