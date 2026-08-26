package com.fengsheng.gm

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.Statistics
import org.apache.logging.log4j.kotlin.logger
import java.util.function.Function

class Removeplayer : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            if (Game.gameCache.any { (_, game) ->
                    GameExecutor.call(game) {
                        game.players.any { it?.playerName == name }
                    }
                }) {
                gson.toJson(mapOf("error" to "玩家在游戏中，无法删除"))
            } else {
                val p = Statistics.removePlayer(name)
                if (p != null) {
                    logger.info("删除玩家$name，分数${p.score}，场次${p.winCount}/${p.gameCount}，精力${p.energy}")
                    gson.toJson(mapOf("result" to true))
                } else {
                    gson.toJson(mapOf("error" to "玩家不存在"))
                }
            }
        } catch (e: NullPointerException) {
            gson.toJson(mapOf("error" to "参数错误"))
        }
    }
}
