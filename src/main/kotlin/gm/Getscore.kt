package com.fengsheng.gm

import com.fengsheng.QQPusher
import com.fengsheng.ScoreFactory
import com.fengsheng.Statistics
import com.fengsheng.protos.Common.secret_task.*
import java.util.function.Function

class Getscore : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            val playerInfo = Statistics.getPlayerInfo(name)
            if (playerInfo == null) {
                "{\"result\": \"${name}已身死道消\"}"
            } else {
                val winRateSum = "%.2f%%".format(ScoreFactory.getAllWinRate())
                val rbWinRateSum = "%.2f%%".format(ScoreFactory.getRBWinRate())
                val blackWinRateSum = "%.2f%%".format(ScoreFactory.getBlackWinRate())
                val score = playerInfo.scoreWithDecay
                val rank = ScoreFactory.getRankNameByScore(score)
                val total = playerInfo.gameCount
                val winRate =
                    if (playerInfo.gameCount == 0) "0.00%"
                    else "%.2f%%".format(playerInfo.winCount * 100.0 / total)
                val energy = playerInfo.energy
                val rbGameCount = playerInfo.rbGameCount
                val rbWinRate =
                    if (rbGameCount == 0) "0.00%"
                    else "%.2f%%".format(playerInfo.rbWinCount * 100.0 / rbGameCount)
                val blackGameCount = playerInfo.blackGameCount
                val blackWinRate =
                    if (blackGameCount == 0) "0.00%"
                    else "%.2f%%".format(playerInfo.blackWinCount * 100.0 / blackGameCount)
                var s = "$name·$rank·$score，总场次：$total，胜率：$winRate\\n"
                s += "-----------------------------------\\n"
                s += "身份\\t 胜率\\t 平均胜率\\t 场次\\n"
                s += "总计\\t $winRate\\t $winRateSum\\t $total\\n"
                s += "军潜\\t $rbWinRate\\t $rbWinRateSum\\t $rbGameCount\\n"
                s += "神秘人\\t $blackWinRate\\t $blackWinRateSum\\t $blackGameCount\\n"
                listOf(Killer to "镇压者", Stealer to "篡夺者", Collector to "双面间谍",
                    Mutator to "诱变者", Pioneer to "先行者", Disturber to "搅局者", Sweeper to "清道夫"
                ).forEach { (secretTask, taskName) ->
                    val winCount1 = playerInfo.blacksWinCount[secretTask] ?: 0
                    val gameCount1 = playerInfo.blacksGameCount[secretTask] ?: 0
                    val winRate1 =
                        if (gameCount1 == 0) "0.00%"
                        else "%.2f%%".format(winCount1 * 100.0 / gameCount1)
                    val winRateSum1 = "%.2f%%".format(ScoreFactory.getBlackWinRate(secretTask))
                    s += "$taskName\\t $winRate1\\t $winRateSum1\\t $gameCount1\\n"
                }
                s += "-----------------------------------\\n"
                s += "剩余精力：$energy"
                if (playerInfo.score != score) s += "（长期不打会掉分，打一场即可全部恢复）"
                val history = QQPusher.getHistory(name)
                if (history.isNotEmpty())
                    s += "\\n\\n最近${history.size}场战绩\\n" + history.joinToString(separator = "\\n")
                "{\"result\": \"$s\"}"
            }
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}
