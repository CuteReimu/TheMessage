package com.fengsheng.gm

import com.fengsheng.ScoreFactory
import com.fengsheng.Statistics
import java.util.function.Function

class Getscore : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            val playerInfo = Statistics.getPlayerInfo(name)
            if (playerInfo == null) {
                "{\"result\": \"${name}已身死道消\"}"
            } else {
                val score = playerInfo.scoreWithDecay
                val rank = ScoreFactory.getRankNameByScore(score)
                val total = playerInfo.gameCount
                val winRate =
                    if (playerInfo.gameCount == 0) "0.00%"
                    else "%.2f%%".format(playerInfo.winCount * 100.0 / total)
                val energy = playerInfo.energy
                var s = "$name·$rank·$score，总场次：$total，胜率：$winRate，精力：$energy"
                if (playerInfo.score != score) s += "（长期不打会掉分，打一场即可全部恢复）"
                "{\"result\": \"$s\"}"
            }
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}
