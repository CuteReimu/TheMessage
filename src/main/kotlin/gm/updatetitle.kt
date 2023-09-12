package com.fengsheng.gm

import com.fengsheng.Game
import com.fengsheng.Statistics
import java.util.function.Function

class updatetitle : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            if (name.contains(",") || name.contains("·")) return "{\"error\": \"名字中含有非法字符\"}"
            val title = form.getOrDefault("title", "")
            if (name.contains(",") || name.contains("·")) return "{\"error\": \"称号中含有非法字符\"}"
            val result = Statistics.updateTitle(name, title)
            Game.playerNameCache[name]?.playerTitle = title
            "{\"result\": $result}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}