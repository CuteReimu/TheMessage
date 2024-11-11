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
            if (humanCount <= 1 && emptyPosition == 1) {
                if (Statistics.getEnergy(r.playerName) <= 0) {
                    r.sendErrorMessage("""你的精力不足，不能进行人机局。你可以在群里输入"精力系统"了解。""")
                    return
                }
                if (Game.gameCache.count { (_, v) -> // 未开始或有空位的房间（含本房间）大于1，则禁止开局
                        !v.isStarted || v.players.any { it !is HumanPlayer }
                    } > 1) {
                    r.sendErrorMessage("""有其他玩家正在进行游戏，请等待他们一起吧！""")
                    return
                }
            }
        }
        val robotPlayer = RobotPlayer()
        robotPlayer.playerName = Player.randPlayerName(r.game!!)
        robotPlayer.game = r.game
        robotPlayer.game!!.onPlayerJoinRoom(robotPlayer, Statistics.totalPlayerGameCount.random())
    }
}
