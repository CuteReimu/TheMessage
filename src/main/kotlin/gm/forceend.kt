package com.fengsheng.gm

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import java.util.function.Function

class forceend : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            Game.gameCache.values.toList().forEach {
                GameExecutor.post(it) { it.end(null, null, true) }
            }
            "{\"result\": true}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}