package com.fengsheng

import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.Statistics.Record
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.color.Has_No_Identity
import com.fengsheng.protos.Common.secret_task
import com.fengsheng.protos.Common.secret_task.*
import org.apache.logging.log4j.kotlin.Logging
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.round

object ScoreFactory : Logging {
    private val rankString = listOf("I", "II", "III", "IV", "V")

    fun getRankNameByScore(score: Int): String {
        return when {
            score < 60 -> "\uD83E\uDD49" + rankString[2 - score / 20]
            score < 240 -> "\uD83E\uDD48" + rankString[2 - (score - 60) / 60]
            score < 360 -> "\uD83E\uDD47" + rankString[4 - (score - 240) / 60]
            score < 600 -> "\uD83E\uDD47" + rankString[2 - (score - 360) / 80]
            score < 1000 -> "\uD83D\uDC8D" + rankString[4 - (score - 600) / 80]
            score < 1500 -> "\uD83D\uDCA0" + rankString[4 - (score - 1000) / 100]
            score < 2000 -> "\uD83D\uDC51" + rankString[4 - (score - 1500) / 100]
            else -> "\uD83D\uDC51" + rankString[0]
        }
    }

    fun getRankStringNameByScore(score: Int): String {
        return when {
            score < 60 -> "青铜" + rankString[2 - score / 20]
            score < 240 -> "白银" + rankString[2 - (score - 60) / 60]
            score < 360 -> "黄金" + rankString[4 - (score - 240) / 60]
            score < 600 -> "黄金" + rankString[2 - (score - 360) / 80]
            score < 1000 -> "铂金" + rankString[4 - (score - 600) / 80]
            score < 1500 -> "钻石" + rankString[4 - (score - 1000) / 100]
            score < 2000 -> "大师" + rankString[4 - (score - 1500) / 100]
            else -> "大师" + rankString[0]
        }
    }

    private val protectScore = (362 downTo 0).filter {
        when (it % 60) {
            59, 0, 1, 2 -> true
            else -> it <= 180 && it % 10 == 0
        }
    }

    infix fun Int.addScore(delta: Int): Int {
        return if (delta >= 0) this + delta
        else {
            val newScore = if (this >= 60) this + delta else (this + delta).coerceAtLeast(this - 2)
            for (v in protectScore) {
                if (this > v) return newScore.coerceAtLeast(v)
            }
            newScore.coerceAtLeast(0)
        }
    }

    fun Player.calScore(players: List<Player>, winners: List<Player>, delta: Int): Int {
        class Score(var value: Double) {
            var positiveMultiple = 0.0
            var negativeMultiple = 1.0
            operator fun timesAssign(multiple: Double) {
                if (multiple >= 1.0) positiveMultiple += multiple - 1.0 // 加分加算
                else negativeMultiple *= multiple.coerceAtLeast(0.01) // 减分乘算
            }

            operator fun divAssign(v: Int) {
                value /= v
            }

            fun toInt(): Int {
                var v = value * (negativeMultiple + positiveMultiple)
                if (value > 0.0) v = v.coerceAtLeast(1.0)
                else if (value < 0.0) v = v.coerceAtMost(-1.0)
                return ceil(round(v * 10.0) / 10.0).toInt()
            }
        }

        val score: Score
        if (winners.any { it === this }) { // 赢了
            score = Score(players.size.let { if (it <= 6) 7.0 * (it - 3) else 12.0 * (it - 5) })
            if (originIdentity == Black) {
                val index = originSecretTask.number + 3
                playerCountCount.computeIfPresent(players.size.coerceAtMost(8)) { _, list ->
                    val rate =
                        if (originSecretTask == Mutator && list[Mutator.number + 3].rate < list[Collector.number + 3].rate)
                            list[Collector.number + 3].rate // 如果诱变者胜率低于双面间谍，则取双重间谍的胜率
                        else if (originSecretTask == Sweeper && list[Sweeper.number + 3].rate > list[Mutator.number + 3].rate)
                            list[Mutator.number + 3].rate // 如果清道夫胜率高于诱变者，则取诱变者的胜率
                        else if (originIdentity == Black && originSecretTask != Mutator)
                            list[index].rate.coerceAtMost(list[0].rate) // 诱变者以外的神秘人加分不能少于阵营方
                        else list[index].rate
                    if (list[index].gameCount > 0) score *= list[0].rate / rate.coerceIn(8.0..50.0) // 胜率有效范围在8%至50%
                    list
                }
            }
            if (identity == Has_No_Identity) score /= winners.count { it.identity == Has_No_Identity }.coerceAtLeast(1)
            score *= 1 + delta / 100.0
        } else {
            score = Score(if (players.size <= 6) -7.0 else -12.0)
            if (originIdentity == Black) {
                val index = originSecretTask.number + 3
                playerCountCount.computeIfPresent(players.size.coerceAtMost(8)) { _, list ->
                    val rate =
                        if (originSecretTask == Mutator && list[Mutator.number + 3].rate < list[Collector.number + 3].rate)
                            list[Collector.number + 3].rate // 如果诱变者胜率低于双面间谍，则取双重间谍的胜率
                        else if (originSecretTask == Sweeper && list[Sweeper.number + 3].rate > list[Mutator.number + 3].rate)
                            list[Mutator.number + 3].rate // 如果清道夫胜率高于诱变者，则取诱变者的胜率
                        else if (originIdentity == Black && originSecretTask != Mutator)
                            list[index].rate.coerceAtMost(list[0].rate) // 诱变者以外的神秘人加分不能少于阵营方
                        else list[index].rate
                    if (list[index].gameCount > 0)
                        score *= (100.0 - list[0].rate) / (100.0 - rate.coerceIn(8.0..50.0)) // 胜率有效范围在8%至50%
                    list
                }
            }
            score *= 1 + delta / 100.0
        }
        return score.toInt()
    }

    fun addWinCount(records: List<Record>) {
        fun MutableList<PlayerGameCount>.inc(index: Int? = null, isWinner: Boolean) {
            this[0] = this[0].inc(isWinner)
            if (index != null) {
                this[2] = this[2].inc(isWinner)
                this[index] = this[index].inc(isWinner)
            } else {
                this[1] = this[1].inc(isWinner)
            }
        }

        playerCountCount.computeIfPresent(records.size) { _, list ->
            records.forEach {
                val index = if (it.identity == Black) it.task.number + 3 else null
                list.inc(index, it.isWinner)
            }
            list
        }
    }

    fun load() {
        fun IntArray.inc(index: Int? = null) {
            this[0]++
            if (index != null) {
                this[2]++
                this[index]++
            } else {
                this[1]++
            }
        }

        val playerCountAppearCount = TreeMap<Int, IntArray>()
        val playerCountWinCount = TreeMap<Int, IntArray>()
        try {
            FileInputStream("stat.csv").use { `is` ->
                BufferedReader(InputStreamReader(`is`)).use { reader ->
                    var line: String?
                    while (true) {
                        line = reader.readLine()
                        if (line == null) break
                        val a = line.split(Regex(",")).dropLastWhile { it.isEmpty() }
                        val playerCount = a[4].toInt()
                        val playerCountAppear = playerCountAppearCount.computeIfAbsent(playerCount) { IntArray(10) }
                        val playerCountWin = playerCountWinCount.computeIfAbsent(playerCount) { IntArray(10) }
                        val index =
                            if ("Black" == a[2]) secret_task.valueOf(a[3]).number + 3
                            else null
                        playerCountAppear.inc(index)
                        if (a[1].toBoolean()) playerCountWin.inc(index)
                    }
                }
            }
        } catch (_: FileNotFoundException) {
            // Ignored
        }

        fun Int.parseSecretTask() = when (this) {
            0 -> "全部"
            1 -> "军潜"
            2 -> "神秘人"
            else -> secret_task.forNumber(this - 3)!!.toString()
        }

        (5..9).forEach { count ->
            playerCountCount[count] = MutableList(10) {
                val winCount = playerCountWinCount[count]?.get(it) ?: 0
                val gameCount = playerCountAppearCount[count]?.get(it) ?: 0
                PlayerGameCount(winCount, gameCount).apply {
                    logger.info("${count}人局${it.parseSecretTask()}场次${gameCount}胜率${"%.2f".format(rate)}%")
                }
            }
        }
    }

    private val playerCountCount = ConcurrentHashMap<Int, MutableList<PlayerGameCount>>()
}
