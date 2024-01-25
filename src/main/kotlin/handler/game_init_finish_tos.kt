package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.phase.WaitForSelectRole
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger

class game_init_finish_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        if (player.isLoadingRecord) {
            player.displayRecord()
        } else if (player.isReconnecting) {
            player.reconnect()
        } else {
            val game = player.game
            if (game == null) {
                logger.error("can not find game")
                player.sendErrorMessage("找不到房间")
                return
            }
            val fsm = game.fsm as? WaitForSelectRole ?: return
            fsm.notifySelectRole(player)
        }
    }
}