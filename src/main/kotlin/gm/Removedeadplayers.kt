package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class Removedeadplayers : Function<Map<String, String>, Any> {
    override fun apply(t: Map<String, String>): Any {
        val count = Statistics.removeDeadPlayers()
        return gson.toJson(mapOf("count" to count))
    }
}
