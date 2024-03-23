package com.fengsheng.gm

import com.fengsheng.Game
import com.fengsheng.Statistics
import java.util.function.Function

class updatetitle : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            val title = form.getOrDefault("title", "")
            if (title.length > 12) return "{\"error\": \"称号太长\"}"
            if (invalidString.any { it in name }) return "{\"error\": \"称号中含有非法字符\"}"
            val result = Statistics.updateTitle(name, title)
            if (result) Game.playerNameCache[name]?.playerTitle = title
            "{\"result\": $result}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }

    companion object {
        private val invalidString = listOf(",", "·", "{", "$", "}")
    }
}
