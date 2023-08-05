package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.RobotPlayer
import com.fengsheng.Statistics
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class add_robot_tos : AbstractProtoHandler<Fengsheng.add_robot_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.add_robot_tos) {
        if (r.game!!.isStarted) {
            log.error("the game has already started")
            r.sendErrorMessage("游戏已经开始了")
            return
        }
        val humanCount = r.game!!.players.count { it is HumanPlayer }
        val emptyCount = r.game!!.players.count { it == null }
        if (humanCount >= 2 && emptyCount == 1) {
            r.sendErrorMessage("禁止添加机器人")
            return
        }
//        val count = Statistics.getPlayerGameCount(r.playerName)
//        if (count.winCount <= 0) {
//            val now = System.currentTimeMillis()
//            val startTrialTime: Long = Statistics.getTrialStartTime(r.device!!)
//            if (startTrialTime != 0L && now - 5 * 24 * 3600 * 1000 >= startTrialTime) {
//                r.sendErrorMessage("禁止添加机器人")
//                return
//            }
//            Statistics.setTrialStartTime(r.device!!, now)
//        }
        val robotPlayer: Player = RobotPlayer()
        robotPlayer.playerName = Player.randPlayerName(r.game!!)
        robotPlayer.game = r.game
        robotPlayer.game!!.onPlayerJoinRoom(robotPlayer, Statistics.totalPlayerGameCount.random())
    }

    companion object {
        private val log = Logger.getLogger(add_robot_tos::class.java)
    }
}