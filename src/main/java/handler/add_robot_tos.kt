package com.fengsheng.handler

import com.fengsheng.*
import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.protos.Errcode
import com.fengsheng.protos.Errcode.error_code_toc
import com.fengsheng.protos.Fengsheng

org.apache.log4j.Logger
class add_robot_tos : AbstractProtoHandler<Fengsheng.add_robot_tos?>() {
    override fun handle0(player: HumanPlayer, pb: Fengsheng.add_robot_tos?) {
        synchronized(Game::class.java) {
            if (player.game.isStarted) {
                log.error("room is already full")
                return
            }
            if (player.game.players.size > 5) {
                player.send(error_code_toc.newBuilder().setCode(Errcode.error_code.robot_not_allowed).build())
                return
            }
            val count: PlayerGameCount = Statistics.Companion.getInstance().getPlayerGameCount(player.playerName)
            if (count == null || count.winCount <= 0) {
                val now = System.currentTimeMillis()
                val startTrialTime: Long = Statistics.Companion.getInstance().getTrialStartTime(player.device)
                if (startTrialTime != 0L && now - 5 * 24 * 3600 * 1000 >= startTrialTime) {
                    player.send(error_code_toc.newBuilder().setCode(Errcode.error_code.robot_not_allowed).build())
                    return
                }
                Statistics.Companion.getInstance().setTrialStartTime(player.device, now)
            }
            val robotPlayer: Player = RobotPlayer()
            robotPlayer.playerName = Player.Companion.randPlayerName()
            robotPlayer.game = player.game
            robotPlayer.game.onPlayerJoinRoom(robotPlayer, Statistics.Companion.getInstance().getTotalPlayerGameCount())
        }
    }

    companion object {
        private val log = Logger.getLogger(add_robot_tos::class.java)
    }
}