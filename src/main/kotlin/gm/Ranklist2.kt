package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class Ranklist2 : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return gson.toJson(mapOf("result" to Statistics.rankList100.get()))
    }
}
