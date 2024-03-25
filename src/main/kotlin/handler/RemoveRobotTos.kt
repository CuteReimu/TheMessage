package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.RobotPlayer
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.leaveRoomToc
import com.fengsheng.send
import org.apache.logging.log4j.kotlin.logger

class RemoveRobotTos : AbstractProtoHandler<Fengsheng.remove_robot_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.remove_robot_tos) {
        if (r.game!!.isStarted) {
            logger.error("game is already started")
            r.sendErrorMessage("游戏已经开始了")
            return
        }
        val players = r.game!!.players
        val index = players.indexOfLast { it is RobotPlayer }
        if (index >= 0) {
            val robotPlayer = players[index]!!
            r.game!!.players = r.game!!.players.toMutableList().apply { set(index, null) }
            logger.info("${robotPlayer.playerName}离开了房间")
            val reply = leaveRoomToc { position = robotPlayer.location }
            players.send { reply }
            r.game!!.cancelStartTimer()
        }
    }
}
