package com.fengsheng.handler

import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger

abstract class AbstractProtoHandler<T : GeneratedMessageV3> : ProtoHandler {
    protected abstract fun handle0(r: HumanPlayer, pb: T)
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        // 因为player.setGame()只会join_room_tos调用，所以一定和这里的player.getGame()在同一线程，所以无需加锁
        val game = player.game
        if (game == null) {
            log.error("player didn't join room, current msg: " + message.descriptorForType.name)
            player.sendErrorMessage("找不到房间")
        } else {
            GameExecutor.post(game) {
                player.clearTimeoutCount()
                @Suppress("UNCHECKED_CAST")
                handle0(player, message as T)
            }
        }
    }

    companion object {
        private val log = Logger.getLogger(AbstractProtoHandler::class.java)
    }
}