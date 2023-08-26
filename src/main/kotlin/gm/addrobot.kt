package com.fengsheng.gm

import com.fengsheng.*
import java.util.function.Function
import kotlin.math.min

class addrobot : Function<Map<String, String>, String> {
    override fun apply(form: Map<String, String>): String {
        return try {
            var count = form["count"]?.toInt() ?: 0
            count = if (count > 0) count else 99
            val g = Game.newGame
            GameExecutor.post(g) {
                count = min(count, g.players.count { it == null })
                for (i in 0 until count) {
                    if (g.isStarted) break
                    val robotPlayer: Player = RobotPlayer()
                    robotPlayer.playerName = Player.randPlayerName(g)
                    robotPlayer.game = g
                    g.onPlayerJoinRoom(robotPlayer, Statistics.totalPlayerGameCount.random())
                }
            }
            "{\"msg\": \"success\"}"
        } catch (e: NumberFormatException) {
            "{\"error\": \"参数错误\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}