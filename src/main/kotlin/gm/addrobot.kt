package com.fengsheng.gm

import com.fengsheng.Game
import com.fengsheng.Player
import com.fengsheng.RobotPlayer
import com.fengsheng.Statistics
import java.util.function.Function

class addrobot : Function<Map<String, String?>, String> {
    override fun apply(form: Map<String, String?>): String {
        return try {
            var count = if (form.containsKey("count")) form["count"]!!.toInt() else 0
            count = if (count > 0) count else 99
            synchronized(Game::class.java) {
                val g = Game.newGame
                for (i in 0 until count) {
                    if (g.isStarted) break
                    val robotPlayer: Player = RobotPlayer()
                    robotPlayer.playerName = Player.randPlayerName()
                    robotPlayer.game = g
                    robotPlayer.game!!.onPlayerJoinRoom(
                        robotPlayer,
                        Statistics.totalPlayerGameCount
                    )
                }
            }
            "{\"msg\": \"success\"}"
        } catch (e: NumberFormatException) {
            "{\"error\": \"invalid arguments\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"invalid arguments\"}"
        }
    }
}