package com.fengsheng.handler

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.RobotPlayer
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.leave_room_toc

org.apache.log4j.Logger
class remove_robot_tos : AbstractProtoHandler<Fengsheng.remove_robot_tos?>() {
    override fun handle0(player: HumanPlayer, pb: Fengsheng.remove_robot_tos?) {
        val players = player.game.players
        var robotPlayer: RobotPlayer? = null
        synchronized(Game::class.java) {
            for (p in player.game.players) {
                if (p is RobotPlayer) {
                    robotPlayer = p
                    players[p.location()] = null
                    break
                }
            }
        }
        if (robotPlayer != null) {
            log.info(robotPlayer!!.playerName + "离开了房间")
            val reply = leave_room_toc.newBuilder().setPosition(robotPlayer!!.location()).build()
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