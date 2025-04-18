package com.fengsheng.gm

import com.fengsheng.Image
import java.util.function.Function

class Frequency : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        val (frequency, hours) = Image.getFrequency()
        return gson.toJson(mapOf(
            "data" to frequency,
            "hours" to hours,
        ))!!
    }
}
