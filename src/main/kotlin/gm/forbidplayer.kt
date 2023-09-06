package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class forbidplayer : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            val hours = form["hour"]!!.toInt()
            if (hours <= 0) return "{\"error\": \"参数错误\"}"
            if (Statistics.forbidPlayer(name, hours))
                "{\"result\": \"已将${name}封禁${hours}小时\"}"
            else "{\"result\": \"找不到玩家\"}"
        } catch (e: NumberFormatException) {
            "{\"error\": \"参数错误\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}