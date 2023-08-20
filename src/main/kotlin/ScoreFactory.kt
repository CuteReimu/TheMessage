package com.fengsheng

import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.Statistics.Record
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.color.Has_No_Identity
import com.fengsheng.protos.Common.secret_task
import com.fengsheng.protos.Common.secret_task.*
import org.apache.log4j.Logger
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.round

object ScoreFactory {
    fun getRankNameByScore(score: Int): String {
        fun String.multiple(times: Int): String {
            val sb = StringBuilder()
            repeat(times) { sb.append(this) }
            return sb.toString()
        }
        return when {
            score < 180 -> "\uD83E\uDD49".multiple(score / 60 + 1)
            score < 360 -> "\uD83E\uDD48".multiple((score - 180) / 60 + 1)
            score < 680 -> "\uD83E\uDD47".multiple((score - 360) / 80 + 1)
            score < 1000 -> "\uD83D\uDC8D".multiple((score - 680) / 80 + 1)
            score < 1500 -> "\uD83D\uDCA0".multiple((score - 1000) / 100 + 1)
            score < 2000 -> "\uD83D\uDC51".multiple((score - 1500) / 100 + 1)
            else -> "\uD83D\uDC51".multiple(5)
        }
    }

    infix fun Int.addScore(delta: Int) = when {
        delta >= 0 -> this + delta
        this < 180 -> this // 青铜输了不减分
        this < 360 -> (this + delta).coerceAtLeast(this / 60 * 60) // 白银不会掉段
        else -> (this + delta).coerceAtLeast(360) // 黄金以上不会掉到白银
    }

    private val oldTasks = listOf(Killer, Stealer, Collector)
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
                val index = if (players.size <= 6 || originSecretTask in oldTasks) secretTask.number + 3 else 2
                playerCountCount.computeIfPresent(players.size.coerceAtMost(8)) { _, array ->
                    if (array[index].gameCount > 0) score *= array[0].rate / array[index].rate
                    array
                }
            }
            if (identity == Has_No_Identity) score /= winners.count { it.identity == Has_No_Identity }.coerceAtLeast(1)
            score *= 1 + delta / 100.0
        } else {
            score = Score(if (players.size <= 6) -7.0 else -12.0)
            if (originIdentity == Black) {
                val index = if (players.size <= 6 || originSecretTask in oldTasks) secretTask.number + 3 else 2
                playerCountCount.computeIfPresent(players.size.coerceAtMost(8)) { _, array ->
                    if (array[index].gameCount > 0) score *= (100.0 - array[0].rate) / (100.0 - array[index].rate)
                    array
                }
            }
            score *= 1 + delta / 100.0
        }
        return score.toInt()
    }

    fun addWinCount(records: List<Record>) {
        fun Array<PlayerGameCount>.inc(index: Int? = null, isWinner: Boolean) {
            this[0] = this[0].inc(isWinner)
            if (index != null) {
                this[2] = this[2].inc(isWinner)
                this[index] = this[index].inc(isWinner)
            } else {
                this[1] = this[1].inc(isWinner)
            }
        }

        playerCountCount.computeIfPresent(records.size) { _, array ->
            records.forEach {
                val index = if (it.identity == Black) it.task.number + 3 else null
                array.inc(index, it.isWinner)
            }
            array
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
        FileInputStream("stat.csv").use { `is` ->
            BufferedReader(InputStreamReader(`is`)).use { reader ->
                var line: String?
                while (true) {
                    line = reader.readLine()
                    if (line == null) break
                    val a = line.split(Regex(",")).dropLastWhile { it.isEmpty() }.toTypedArray()
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

        fun Int.parseSecretTask() = when (this) {
            0 -> "全部"
            1 -> "军潜"
            2 -> "神秘人"
            else -> secret_task.forNumber(this - 3)!!.toString()
        }

        (5..9).forEach { count ->
            playerCountCount[count] = Array(9) {
                val winCount = playerCountWinCount[count]?.get(it) ?: 0
                val gameCount = playerCountAppearCount[count]?.get(it) ?: 0
                PlayerGameCount(winCount, gameCount).apply {
                    log.info("${count}人局${it.parseSecretTask()}胜率${"%.2f".format(rate)}%")
                }
            }
        }
    }

    private val playerCountCount = ConcurrentHashMap<Int, Array<PlayerGameCount>>()
    private val log = Logger.getLogger(ScoreFactory::class.java)
}