package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.RobotPlayer
import com.fengsheng.Statistics
import com.fengsheng.protos.Errcode
import com.fengsheng.protos.Errcode.error_code_toc
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class add_robot_tos : AbstractProtoHandler<Fengsheng.add_robot_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.add_robot_tos) {
        if (r.game!!.isStarted) {
            log.error("game is already started")
            return
        }
        if (r.game!!.players.size > 5) {
            r.send(error_code_toc.newBuilder().setCode(Errcode.error_code.robot_not_allowed).build())
            return
        }
        val count = Statistics.getPlayerGameCount(r.playerName)
        if (count.winCount <= 0) {
            val now = System.currentTimeMillis()
            val startTrialTime: Long = Statistics.getTrialStartTime(r.device!!)
            if (startTrialTime != 0L && now - 5 * 24 * 3600 * 1000 >= startTrialTime) {
                r.send(error_code_toc.newBuilder().setCode(Errcode.error_code.robot_not_allowed).build())
                return
            }
            Statistics.setTrialStartTime(r.device!!, now)
        }
        val robotPlayer: Player = RobotPlayer()
        robotPlayer.playerName = Player.randPlayerName()
        robotPlayer.game = r.game
        robotPlayer.game!!.onPlayerJoinRoom(robotPlayer, Statistics.totalPlayerGameCount)
    }

    companion object {
        private val log = Logger.getLogger(add_robot_tos::class.java)
    }
}