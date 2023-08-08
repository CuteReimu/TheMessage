package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.RobotPlayer
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class remove_robot_tos : AbstractProtoHandler<Fengsheng.remove_robot_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.remove_robot_tos) {
        if (r.game!!.isStarted) {
            log.error("game is already started")
            r.sendErrorMessage("游戏已经开始了")
            return
        }
        val players = r.game!!.players
        val index = players.indexOfLast { it is RobotPlayer }
        if (index >= 0) {
            val robotPlayer = players[index]!!
            players[index] = null
            log.info("${robotPlayer.playerName}离开了房间")
            val reply = Fengsheng.leave_room_toc.newBuilder().setPosition(robotPlayer.location).build()
            for (p in players) {
                if (p is HumanPlayer) {
                    p.send(reply)
                }
            }
        }
    }

    companion object {
        private val log = Logger.getLogger(remove_robot_tos::class.java)
    }
}
