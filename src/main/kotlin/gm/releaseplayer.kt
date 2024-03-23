package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class releaseplayer : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            if (Statistics.releasePlayer(name))
                "{\"result\": \"${name}已解封\"}"
            else "{\"result\": \"找不到玩家\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}
