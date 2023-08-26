package com.fengsheng.gm

import com.fengsheng.ScoreFactory
import com.fengsheng.Statistics
import java.util.function.Function

class ranklist : Function<Map<String, String>, String> {
    override fun apply(form: Map<String, String>): String {
        val l1 = Statistics.getAllPlayerInfo().filter { it.score > 0 }.sortedByDescending { it.score }
        val l = if (l1.size > 10) l1.subList(0, 10) else l1
        var i = 0
        val s = l.joinToString(separator = "\n") {
            val name = it.name.replace("\"", "\\\"")
            val rank = ScoreFactory.getRankNameByScore(it.score)
            "第${++i}名：${name}·${rank}·${it.score}"
        }
        return "{\"result\": \"$s\"}"
    }
}