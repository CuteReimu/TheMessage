package com.fengsheng

import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.Statistics.Record
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.color.Has_No_Identity
import com.fengsheng.protos.Common.secret_task
import com.fengsheng.protos.Common.secret_task.Disturber
import org.apache.log4j.Logger
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

object ScoreFactory {
    private val subRankNames = arrayOf("I", "II", "III", "IV", "V")

    fun getRankNameByScore(score: Int) = when {
        score < 180 -> "青铜${subRankNames[2 - score / 60]}"
        score < 360 -> "白银${subRankNames[2 - (score - 180) / 60]}"
        score < 680 -> "黄金${subRankNames[3 - (score - 360) / 80]}"
        score < 1000 -> "铂金${subRankNames[3 - (score - 680) / 80]}"
        score < 1500 -> "钻石${subRankNames[4 - (score - 1000) / 100]}"
        score < 2000 -> "大师${subRankNames[4 - (score - 1500) / 100]}"
        else -> "登顶"
    }

    infix fun Int.addScore(delta: Int) = when {
        delta >= 0 -> this + delta
        this < 180 -> this // 青铜输了不减分
        this < 360 -> (this + delta).coerceAtLeast(this / 60 * 60) // 白银不会掉段
        else -> (this + delta).coerceAtLeast(360) // 黄金以上不会掉到白银
    }

    fun Player.calScore(winners: List<Player>): Int {
        val players = game!!.players
        if (winners.isEmpty() || winners.size == players.size) return 0
        val totalWinners = winners.sumOf { Statistics.getScore(it.playerName) }
        val totalPlayers = players.sumOf { Statistics.getScore(it!!.playerName) }
        val totalLoser = totalPlayers - totalWinners
        val delta = totalLoser.toDouble() / (players.size - winners.size) - totalWinners.toDouble() / winners.size
        var score: Double
        if (winners.any { it === this }) { // 赢了
            score = players.size.let { if (it <= 6) 7.0 * (it - 3) else 12.0 * (it - 5) }
            if (originIdentity == Black) {
                val index = if (players.size <= 6 && secretTask != Disturber) secretTask.number + 3 else 2
                playerCountCount.computeIfPresent(players.size) { _, array ->
                    if (array[index].gameCount > 0) score *= array[0].rate / array[index].rate
                    array
                }
            }
            if (identity == Has_No_Identity) score /= winners.count { it.identity == Has_No_Identity }.coerceAtLeast(1)
            score = (score * (1 + delta / 10.0)).coerceAtLeast(1.0)
        } else {
            score = if (players.size <= 6) -7.0 else -12.0
            if (originIdentity == Black) {
                val index = if (players.size <= 6 && secretTask != Disturber) secretTask.number + 3 else 2
                playerCountCount.computeIfPresent(players.size) { _, array ->
                    if (array[index].gameCount > 0) score *= (1 - array[0].rate) / (1 - array[index].rate)
                    array
                }
            }
            score = (score * (1 + delta / 10.0)).coerceAtMost(-1.0)
        }
        return ceil(score).toInt()
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