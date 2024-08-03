package com.fengsheng.handler

import com.fengsheng.*
import com.fengsheng.protos.Fengsheng

class AddRobotTos : AbstractProtoHandler<Fengsheng.add_robot_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.add_robot_tos) {
        if (r.game!!.isStarted) {
            return
        }
        val emptyPosition = r.game!!.players.count { it == null }
        if (emptyPosition == 0) {
            return
        }
        if (!Config.IsGmEnable) {
//            val score = Statistics.getScore(r.playerName) ?: 0
//            if (score <= 0) {
//                val now = System.currentTimeMillis()
//                val startTrialTime = Statistics.getTrialStartTime(r.playerName)
//                if (startTrialTime == 0L) {
//                    Statistics.setTrialStartTime(r.playerName, now)
//                } else if (now - 3 * 24 * 3600 * 1000 >= startTrialTime) {
//                    r.sendErrorMessage("您已被禁止添加机器人，多参与群内活动即可解锁")
//                    return
//                }
//            }
            val humanCount = r.game!!.players.count { it is HumanPlayer }
            if (humanCount <= 1 && emptyPosition == 1 && Statistics.getEnergy(r.playerName) <= 0) {
                r.sendErrorMessage("你的精力不足，不能进行人机局。签到或者与朋友一起游戏可获得精力。")
                return
            }
        }
        val robotPlayer = RobotPlayer()
        robotPlayer.playerName = Player.randPlayerName(r.game!!)
        robotPlayer.game = r.game
        robotPlayer.game!!.onPlayerJoinRoom(robotPlayer, Statistics.totalPlayerGameCount.random())
    }
}
