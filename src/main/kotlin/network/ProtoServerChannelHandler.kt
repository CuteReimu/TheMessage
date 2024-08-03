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
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.apache.commons.text.CaseUtils.toCamelCase
import org.apache.logging.log4j.kotlin.logger
import java.lang.reflect.InvocationTargetException
import java.net.SocketException

/**
 * @suppress 不再使用普通tcp连接，请改用[WebSocketServerChannelHandler]
 */
@Deprecated(level = DeprecationLevel.WARNING, message = "不再使用普通tcp连接")
class ProtoServerChannelHandler : SimpleChannelInboundHandler<ByteBuf>() {
    override fun channelActive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        logger.info(
            "session connected: ${channel.id().asShortText()} ${channel.remoteAddress()}"
        )
        val player = HumanPlayer(channel) { protoName: String, buf: ByteArray ->
            val byteBuf = PooledByteBufAllocator.DEFAULT.ioBuffer(buf.size + 4, buf.size + 4)
            byteBuf.writeShortLE(buf.size + 2)
            byteBuf.writeShortLE(stringHash(protoName).toInt())
            byteBuf.writeBytes(buf)
        }
        if (Game.playerCache.putIfAbsent(channel.id().asLongText(), player) != null) {
            logger.error("already assigned channel id: ${channel.id().asLongText()}")
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        logger.info(
            "session closed: " + channel.id().asShortText() + " " + channel.remoteAddress()
        )
        val player = Game.playerCache.remove(channel.id().asLongText())
        if (player == null) {
            logger.error("already unassigned channel id: " + channel.id().asLongText())
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

    @Throws(Exception::class)
    public override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        val msgLen = msg.readableBytes()
        if (msgLen < 2) {
            logger.error("incorrect msgLen: " + msg.readableBytes())
            ctx.close()
            return
        }
        val id = msg.readShortLE()
        val protoInfo = ProtoInfoMap[id]
        if (protoInfo == null) {
            logger.error("incorrect msg id: $id")
            ctx.close()
            return
        }
        val buf = ByteArray(msgLen - 2)
        msg.readBytes(buf)
        val message = protoInfo.parser.parseFrom(buf) as GeneratedMessage
        if (id != heartMsgId && id != autoPlayMsgId) {
            logger.debug(
                "recv@${ctx.channel().id().asShortText()} len: ${msgLen - 2} ${protoInfo.name} | " +
                    printer.print(message).replace("\n *".toRegex(), " ")
            )
        }
        val player = Game.playerCache[ctx.channel().id().asLongText()]!!
        if (!player.limiter.allow()) {
            logger.error("recv msg too fast: ${ctx.channel().id().asShortText()}")
            ctx.close()
            return
        }
        val handler = protoInfo.handler
        handler.handle(player, message)
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
        private val ProtoInfoMap = HashMap<Short, ProtoInfo>()
        private val heartMsgId: Short = stringHash("heart_tos")
        private val autoPlayMsgId: Short = stringHash("auto_play_tos")

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
                val id = stringHash(name)
                if (id.toInt() == 0) {
                    throw RuntimeException("message meta require 'ID' field: $name")
                }
                val className = "${protoCls.name}$$name"
                val cls = protoCls.classLoader.loadClass(className)
                val parser = cls.getDeclaredMethod("parser").invoke(null) as Parser<*>
                try {
                    val handlerClassName = toCamelCase(name, true, '_')
                    val handlerClass = protoCls.classLoader.loadClass("com.fengsheng.handler.$handlerClassName")
                    val handler = handlerClass.getDeclaredConstructor().newInstance() as ProtoHandler
                    if (ProtoInfoMap.putIfAbsent(id, ProtoInfo(name, parser, handler)) != null) {
                        throw RuntimeException("Duplicate message meta register by id: $id")
                    }
                } catch (ignored: ClassNotFoundException) {
                }
            }
        }

        fun stringHash(s: String): Short {
            var hash = 0
            for (c in s.toByteArray()) {
                val i = if (c >= 0) c.toInt() else 256 + c
                hash = (hash + (hash shl 5) + i + (i shl 7)).toShort().toInt()
            }
            return hash.toShort()
        }
    }
}
