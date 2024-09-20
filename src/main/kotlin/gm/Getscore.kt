package com.fengsheng.gm
import com.fengsheng.QQPusher
import com.fengsheng.ScoreFactory
import com.fengsheng.Statistics
import com.fengsheng.protos.Common
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.function.Function

class Getscore : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            val playerInfo = Statistics.getPlayerInfo(name)
            if (playerInfo == null) {
                "{\"result\": \"${name}已身死道消\"}"
            } else {
                fun IntArray.inc(index: Int? = null) {
                    this[0]++
                    if (index != null) {
                        this[2]++
                        this[index]++
                    } else {
                        this[1]++
                    }
                }
                fun <K> HashMap<K, IntArray>.sum(index: Int): Int {
                    var sum = 0
                    this.forEach { sum += it.value[index] }
                    return sum
                }
                val gameCount = HashMap<Common.role, IntArray>()
                val winCount = HashMap<Common.role, IntArray>()
                FileInputStream("stat.csv").use { `is` ->
                    BufferedReader(InputStreamReader(`is`)).use { reader ->
                        var line: String?
                        while (true) {
                            line = reader.readLine()
                            if (line == null) break
                            val a = line.split(Regex(",")).dropLastWhile { it.isEmpty() }
                            val role = Common.role.valueOf(a[0])
                            val appear = gameCount.computeIfAbsent(role) { IntArray(10) }
                            val win = winCount.computeIfAbsent(role) { IntArray(10) }
                            val index =
                                if ("Black" == a[2]) Common.secret_task.valueOf(a[3]).number + 3
                                else null
                            appear.inc(index)
                            if (a[1].toBoolean()) win.inc(index)
                        }
                    }
                }
                val winRateSum =
                    if (gameCount.sum(0) == 0) "0.00%"
                    else "%.2f%%".format(winCount.sum(0) * 100.0 / gameCount.sum(0))
                val rbWinRateSum =
                    if (gameCount.sum(1) == 0) "0.00%"
                    else "%.2f%%".format(winCount.sum(1) * 100.0 / gameCount.sum(1))
                val blackWinRateSum =
                    if (gameCount.sum(2) == 0) "0.00%"
                    else "%.2f%%".format(winCount.sum(2) * 100.0 / gameCount.sum(2))
                val killerWinRateSum =
                    if (gameCount.sum(3) == 0) "0.00%"
                    else "%.2f%%".format(winCount.sum(3) * 100.0 / gameCount.sum(3))
                val stealerWinRateSum =
                    if (gameCount.sum(4) == 0) "0.00%"
                    else "%.2f%%".format(winCount.sum(4) * 100.0 / gameCount.sum(4))
                val collectorWinRateSum =
                    if (gameCount.sum(5) == 0) "0.00%"
                    else "%.2f%%".format(winCount.sum(5) * 100.0 / gameCount.sum(5))
                val mutatorWinRateSum =
                    if (gameCount.sum(6) == 0) "0.00%"
                    else "%.2f%%".format(winCount.sum(6) * 100.0 / gameCount.sum(6))
                val pioneerWinRateSum =
                    if (gameCount.sum(7) == 0) "0.00%"
                    else "%.2f%%".format(winCount.sum(7) * 100.0 / gameCount.sum(7))
                val disturberWinRateSum =
                    if (gameCount.sum(8) == 0) "0.00%"
                    else "%.2f%%".format(winCount.sum(8) * 100.0 / gameCount.sum(8))
                val sweeperWinRateSum =
                    if (gameCount.sum(9) == 0) "0.00%"
                    else "%.2f%%".format(winCount.sum(9) * 100.0 / gameCount.sum(9))

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
                val killerGameCount = playerInfo.killerGameCount
                val killerWinRate =
                    if (killerGameCount == 0) "0.00%"
                    else "%.2f%%".format(playerInfo.killerWinCount * 100.0 / killerGameCount)
                val stealerGameCount = playerInfo.stealerGameCount
                val stealerWinRate =
                    if (stealerGameCount == 0) "0.00%"
                    else "%.2f%%".format(playerInfo.stealerWinCount * 100.0 / stealerGameCount)
                val collectorGameCount = playerInfo.collectorGameCount
                val collectorWinRate =
                    if (collectorGameCount == 0) "0.00%"
                    else "%.2f%%".format(playerInfo.collectorWinCount * 100.0 / collectorGameCount)
                val mutatorGameCount = playerInfo.mutatorGameCount
                val mutatorWinRate =
                    if (mutatorGameCount == 0) "0.00%"
                    else "%.2f%%".format(playerInfo.mutatorWinCount * 100.0 / mutatorGameCount)
                val pioneerGameCount = playerInfo.pioneerGameCount
                val pioneerWinRate =
                    if (pioneerGameCount == 0) "0.00%"
                    else "%.2f%%".format(playerInfo.pioneerWinCount * 100.0 / pioneerGameCount)
                val disturberGameCount = playerInfo.disturberGameCount
                val disturberWinRate =
                    if (disturberGameCount == 0) "0.00%"
                    else "%.2f%%".format(playerInfo.disturberWinCount * 100.0 / disturberGameCount)
                val sweeperGameCount = playerInfo.sweeperGameCount
                val sweeperWinRate =
                    if (sweeperGameCount == 0) "0.00%"
                    else "%.2f%%".format(playerInfo.sweeperWinCount * 100.0 / sweeperGameCount)
                var s1 = "$name·$rank·$score，总场次：$total，胜率：$winRate\n"
                var s2 = "-----------------------------------\n"
                var s3 = "身份\t 胜率\t 平均胜率\t 场次\n"
                val s4 = "总计\t $winRate\t $winRateSum\t $total\n"
                var s5 = "军潜\t $rbWinRate\t $rbWinRateSum\t $rbGameCount\n"
                var s6 = "神秘人\t $blackWinRate\t $blackWinRateSum\t $blackGameCount\n"
                var s7 = "镇压者\t $killerWinRate\t $killerWinRateSum\t $killerGameCount\n"
                var s8 = "篡夺者\t $stealerWinRate\t $stealerWinRateSum\t $stealerGameCount\n"
                var s9 = "双面间谍\t $collectorWinRate\t $collectorWinRateSum\t $collectorGameCount\n"
                var s10 = "诱变者\t $mutatorWinRate\t $mutatorWinRateSum\t $mutatorGameCount\n"
                var s11 = "先行者\t $pioneerWinRate\t $pioneerWinRateSum\t $pioneerGameCount\n"
                var s12 = "搅局者\t $disturberWinRate\t $disturberWinRateSum\t $disturberGameCount\n"
                var s13 = "清道夫\t $sweeperWinRate\t $sweeperWinRateSum\t $sweeperGameCount\n"
                var s14 = "-----------------------------------\n"
                var s15 = "剩余精力：$energy"
                var s = s1 + s2 + s3 + s4 + s5 + s6 + s7 + s8 + s9 + s10 + s11 + s12 + s13 + s14 + s15
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
