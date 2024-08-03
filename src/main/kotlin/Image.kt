package com.fengsheng

import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.Statistics.PlayerInfo
import com.fengsheng.protos.Common
import com.fengsheng.skill.RoleCache
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

object Image {
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm").apply {
        timeZone = TimeZone.getTimeZone("GMT+8:00")
    }

    private class Row(val roleName: String, val totalCount: Int, val winRates: DoubleArray)

    private class Gradient(
        values: Collection<Double>,
        val minColor: Color = Color(99, 190, 123),
        val aveColor: Color = Color.WHITE,
        val maxColor: Color = Color(245, 105, 104)
    ) {
        val average = values.average()
        val min = values.minOrNull() ?: Double.NaN
        val max = values.maxOrNull() ?: Double.NaN
        fun getColor(value: Double): Color {
            if (average.isNaN()) return Color.WHITE
            return when {
                value == average -> aveColor
                value <= min -> minColor
                value >= max -> maxColor
                value < average -> {
                    val red = aveColor.red - (aveColor.red - minColor.red) * (average - value) / (average - min)
                    val green = aveColor.green - (aveColor.green - minColor.green) * (average - value) / (average - min)
                    val blue = aveColor.blue - (aveColor.blue - minColor.blue) * (average - value) / (average - min)
                    Color(red.toInt(), green.toInt(), blue.toInt())
                }

                else -> {
                    val red = aveColor.red - (aveColor.red - maxColor.red) * (value - average) / (max - average)
                    val green = aveColor.green - (aveColor.green - maxColor.green) * (value - average) / (max - average)
                    val blue = aveColor.blue - (aveColor.blue - maxColor.blue) * (value - average) / (max - average)
                    Color(red.toInt(), green.toInt(), blue.toInt())
                }
            }
        }
    }

    private const val CELL_W = 66
    private const val CELL_H = 18
    private val font = Font("宋体", 0, CELL_H - 3)

    fun getWinRateImage(): BufferedImage {
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

        val columns = listOf(
            "角色", "场次", "总胜率", "军潜", "神秘人",
            "镇压者", "簒夺者", "双面间谍", "诱变者", "先行者", "搅局者", "清道夫",
        )
        val appearCount = HashMap<Common.role, IntArray>()
        val winCount = HashMap<Common.role, IntArray>()
        FileInputStream("stat.csv").use { `is` ->
            BufferedReader(InputStreamReader(`is`)).use { reader ->
                var line: String?
                while (true) {
                    line = reader.readLine()
                    if (line == null) break
                    val a = line.split(Regex(",")).dropLastWhile { it.isEmpty() }
                    val role = Common.role.valueOf(a[0])
                    val appear = appearCount.computeIfAbsent(role) { IntArray(10) }
                    val win = winCount.computeIfAbsent(role) { IntArray(10) }
                    val index =
                        if ("Black" == a[2]) Common.secret_task.valueOf(a[3]).number + 3
                        else null
                    appear.inc(index)
                    if (a[1].toBoolean()) win.inc(index)
                }
            }
        }
        val lines = ArrayList<Row>()
        for ((key, value) in appearCount) {
            lines.add(Row(
                RoleCache.getRoleName(key) ?: "",
                value[0],
                DoubleArray(value.size) { i ->
                    winCount[key]!![i] * 100.0 / value[i]
                }
            ))
        }
        lines.sortByDescending { it.winRates.firstOrNull() ?: 0.0 }

        val img =
            BufferedImage(CELL_W * 12 + 1 + font.size, CELL_H * (lines.size + 2) + 1, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(img.minX, img.minY, img.width, img.height)
        g.color = Color.BLACK
        g.font = font
        columns.forEachIndexed { i, s ->
            if (i == 0)
                g.drawString(s, 3, CELL_H - 3)
            else
                g.drawString(s, i * CELL_W + 3 + font.size, CELL_H - 3)
        }
        g.drawString("全部", 3, CELL_H * 2 - 3)
        g.drawString(appearCount.sum(0).toString(), CELL_W + 3 + font.size, CELL_H * 2 - 3)
        val g1 = Gradient((1 until columns.size - 2).mapNotNull { i ->
            val winSum = winCount.sum(i)
            val appearSum = appearCount.sum(i)
            if (appearSum != 0) winSum * 100.0 / appearSum
            else null
        })
        for (i in 0 until columns.size - 2) {
            val winSum = winCount.sum(i)
            val appearSum = appearCount.sum(i)
            if (appearSum != 0) {
                if (i > 0) {
                    g.color = g1.getColor(winSum * 100.0 / appearSum)
                    g.fillRect(CELL_W * (i + 2) + font.size, CELL_H, CELL_W, CELL_H)
                    g.color = Color.BLACK
                }
                g.drawString(
                    "%.2f%%".format(winSum * 100.0 / appearSum),
                    CELL_W * (i + 2) + 3 + font.size,
                    CELL_H * 2 - 3
                )
            }
        }
        val g2 = Gradient(lines.map { it.totalCount.toDouble() })
        val gn = List(columns.size - 2) {
            Gradient(lines.mapNotNull { line -> line.winRates[it].let { w -> if (w.isNaN()) null else w } })
        }
        lines.forEachIndexed { index, line ->
            val row = index + 2
            g.drawString(line.roleName, 3, (row + 1) * CELL_H - 3)
            g.color = g2.getColor(line.totalCount.toDouble())
            g.fillRect(CELL_W + font.size, row * CELL_H, CELL_W, CELL_H)
            g.color = Color.BLACK
            g.drawString(line.totalCount.toString(), CELL_W + 3 + font.size, (row + 1) * CELL_H - 3)
            line.winRates.forEachIndexed { i, v ->
                if (!v.isNaN()) {
                    val col = i + 2
                    g.color = gn[i].getColor(v)
                    g.fillRect(col * CELL_W + font.size, row * CELL_H, CELL_W, CELL_H)
                    g.color = Color.BLACK
                    g.drawString("%.2f%%".format(v), col * CELL_W + 3 + font.size, (row + 1) * CELL_H - 3)
                }
            }
        }
        g.color = Color(205, 204, 200)
        repeat(lines.size + 3) {
            g.drawLine(0, it * CELL_H, img.width, it * CELL_H)
        }
        repeat(columns.size + 1) { i ->
            if (i == 0)
                g.drawLine(0, 0, 0, img.height)
            else
                g.drawLine(i * CELL_W + font.size, 0, i * CELL_W + font.size, img.height)
        }
        g.dispose()
        return img
    }

    fun genRankListImage(lines: List<PlayerInfo>): BufferedImage {
        val img =
            BufferedImage(CELL_W * 8 + 1 + font.size * 4, CELL_H * (lines.size + 1) + 1, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(img.minX, img.minY, img.width, img.height)
        g.color = Color.BLACK
        g.font = font
        val aveColor = Color(250, 180, 180)
        val g0 = Gradient(lines.indices.map { 50.0 - it })
        lines.forEachIndexed { index, line ->
            g.color = g0.getColor(50.0 - index)
            g.fillRect(0, (index + 1) * CELL_H, CELL_W * 2 + font.size * 2, CELL_H)
            g.color = Color.BLACK
            g.drawString("第${index + 1}名", 3, (index + 2) * CELL_H - 3)
            g.drawString(line.name, CELL_W + 3, (index + 2) * CELL_H - 3)
        }
        g.color = Color.WHITE
        g.fillRect(CELL_W * 2 + font.size * 2, 0, img.width - (CELL_W * 2 + font.size * 2), img.height)
        g.color = Color.BLACK
        g.drawString("段位", CELL_W * 2 + font.size * 2 + 3, CELL_H - 3)
        g.drawString("分数", CELL_W * 3 + font.size * 2 + 3, CELL_H - 3)
        g.drawString("赛季场次", CELL_W * 4 + font.size * 2 + 3, CELL_H - 3)
        g.drawString("赛季胜率", CELL_W * 5 + font.size * 2 + 3, CELL_H - 3)
        g.drawString("最近一局", CELL_W * 6 + font.size * 2 + 3, CELL_H - 3)
        g.drawString("精力", CELL_W * 7 + font.size * 4 + 3, CELL_H - 3)
        val g1 = Gradient(lines.map { it.score.toDouble() }, minColor = Color.WHITE, aveColor = aveColor)
        val g2 = Gradient(lines.map { it.gameCount.toDouble() }, minColor = Color.WHITE, aveColor = aveColor)
        val g3 = Gradient(lines.filter { it.gameCount > 0 }
            .map { PlayerGameCount(it.winCount, it.gameCount).rate.coerceIn(8.0..50.0) })
        val g4 = Gradient(lines.mapNotNull { if (it.lastTime == 0L) null else it.lastTime.toDouble() })
        val g5 = Gradient(lines.map { it.energy.toDouble() }, minColor = Color.WHITE, aveColor = aveColor)
        lines.forEachIndexed { index, line ->
            val rank = ScoreFactory.getRankStringNameByScore(line.score)
            g.color = g1.getColor(line.score.toDouble())
            g.fillRect(CELL_W * 2 + font.size * 2, (index + 1) * CELL_H, CELL_W * 2, CELL_H)
            g.color = Color.BLACK
            g.drawString(rank, CELL_W * 2 + font.size * 2 + 3, (index + 2) * CELL_H - 3)
            g.drawString(line.score.toString(), CELL_W * 3 + font.size * 2 + 3, (index + 2) * CELL_H - 3)
            val count = PlayerGameCount(line.winCount, line.gameCount)
            g.color = g2.getColor(count.gameCount.toDouble())
            g.fillRect(CELL_W * 4 + font.size * 2, (index + 1) * CELL_H, CELL_W, CELL_H)
            g.color = Color.BLACK
            g.drawString(count.gameCount.toString(), CELL_W * 4 + font.size * 2 + 3, (index + 2) * CELL_H - 3)
            if (line.gameCount > 0) {
                g.color = g3.getColor(count.rate)
                g.fillRect(CELL_W * 5 + font.size * 2, (index + 1) * CELL_H, CELL_W, CELL_H)
                g.color = Color.BLACK
                g.drawString("%.2f%%".format(count.rate), CELL_W * 5 + font.size * 2 + 3, (index + 2) * CELL_H - 3)
            }
            if (line.lastTime > 0) {
                g.color = g4.getColor(line.lastTime.toDouble())
                g.fillRect(CELL_W * 6 + font.size * 2, (index + 1) * CELL_H, CELL_W + font.size * 2, CELL_H)
                g.color = Color.BLACK
                val dateString = dateFormat.format(Date(line.lastTime))
                g.drawString(dateString, CELL_W * 6 + font.size * 2 + 3, (index + 2) * CELL_H - 3)
            }
            g.color = g5.getColor(line.energy.toDouble())
            g.fillRect(CELL_W * 7 + font.size * 4, (index + 1) * CELL_H, CELL_W + font.size * 2, CELL_H)
            g.color = Color.BLACK
            g.drawString(line.energy.toString(), CELL_W * 7 + font.size * 4 + 3, (index + 2) * CELL_H - 3)
        }
        g.color = Color(205, 204, 200)
        repeat(lines.size + 2) {
            g.drawLine(0, it * CELL_H, img.width, it * CELL_H)
        }
        repeat(9) { i ->
            if (i < 2)
                g.drawLine(i * CELL_W, 0, i * CELL_W, img.height)
            else if (i < 7)
                g.drawLine(i * CELL_W + font.size * 2, 0, i * CELL_W + font.size * 2, img.height)
            else
                g.drawLine(i * CELL_W + font.size * 4, 0, i * CELL_W + font.size * 4, img.height)
        }
        g.dispose()
        return img
    }
}
