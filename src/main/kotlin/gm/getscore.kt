package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class getscore : Function<Map<String, String?>, String> {
    override fun apply(form: Map<String, String?>): String {
        return try {
            val name = form["name"]!!
            "{\"score\": ${Statistics.getScore(name)}}"
        } catch (e: NumberFormatException) {
            "{\"error\": \"invalid arguments\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"invalid arguments\"}"
        }
    }
}