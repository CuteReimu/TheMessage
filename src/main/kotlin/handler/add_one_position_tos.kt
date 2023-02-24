package com.fengsheng.handler

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.RobotPlayer
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.leave_room_toc
import org.apache.log4j.Logger

class add_one_position_tos : AbstractProtoHandler<Fengsheng.add_one_position_tos?>() {
    override fun handle0(player: HumanPlayer, pb: Fengsheng.add_one_position_tos?) {
        synchronized(Game::class.java) {
            if (player.game.isStarted) {
                log.error("game already started")
                return
            }
            val players = player.game.players
            if (players.size >= 9) return
            val newPlayers = arrayOfNulls<Player>(players.size + 1)
            System.arraycopy(players, 0, newPlayers, 0, players.size)
            player.game.players = newPlayers
            for (p in players) {
                if (p is HumanPlayer) p.send(Fengsheng.add_one_position_toc.getDefaultInstance())
            }
            if (newPlayers.size > 5) {
                for (i in newPlayers.indices) {
                    if (newPlayers[i] is RobotPlayer) {
                        log.info(robotPlayer.getPlayerName() + "离开了房间")
                        val reply = leave_room_toc.newBuilder().setPosition(robotPlayer.location()).build()
                        newPlayers[i] = null
                        for (p in players) {
                            if (p is HumanPlayer) {
                                p.send(reply)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val log = Logger.getLogger(add_one_position_tos::class.java)
    }
}