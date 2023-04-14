package com.fengsheng.handler

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.RobotPlayer
import com.fengsheng.protos.Fengsheng.leave_room_toc
import com.fengsheng.protos.Fengsheng.remove_robot_tos
import org.apache.log4j.Logger

class remove_robot_tos : AbstractProtoHandler<remove_robot_tos>() {
    override fun handle0(r: HumanPlayer, pb: remove_robot_tos) {
        if (r.game!!.isStarted) {
            log.error("game is already started")
            return
        }
    }

    companion object {
        fun Game.removeOneRobot() {
            val index = players.indexOfLast { it is RobotPlayer }
            if (index >= 0) {
                val robotPlayer = players[index]!!
                players[index] = null
                log.info("${robotPlayer.playerName}离开了房间")
                val reply = leave_room_toc.newBuilder().setPosition(robotPlayer.location).build()
                for (p in players) {
                    if (p is HumanPlayer) {
                        p.send(reply)
                    }
                }
            }
        }

        private val log = Logger.getLogger(remove_robot_tos::class.java)
    }
}
