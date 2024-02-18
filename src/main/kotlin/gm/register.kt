package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class register : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            if (name.length > 12) return "{\"error\": \"名字太长\"}"
            if (invalidString.any { it in name }) return "{\"error\": \"名字中含有非法字符\"}"
            if ("名字" in name) return "{\"error\": \"不能含有“名字”二字\"}"
            val result = Statistics.register(name)
            Statistics.setTrialStartTime(name, System.currentTimeMillis())
            "{\"result\": $result}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }

    companion object {
        private val invalidString = listOf(",", "·", "{", "$", "}")
    }
}