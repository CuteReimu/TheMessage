package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.RobotPlayer
import com.fengsheng.protos.Fengsheng
import org.apache.logging.log4j.kotlin.logger

class remove_robot_tos : AbstractProtoHandler<Fengsheng.remove_robot_tos>() {
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
            players[index] = null
            logger.info("${robotPlayer.playerName}离开了房间")
            val reply = Fengsheng.leave_room_toc.newBuilder().setPosition(robotPlayer.location).build()
            for (p in players)
                (p as? HumanPlayer)?.send(reply)
            r.game!!.cancelStartTimer()
        }
    }

    companion object {
    }
}
