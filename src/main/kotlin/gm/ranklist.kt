package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class ranklist : Function<Map<String, String>, String> {
    override fun apply(form: Map<String, String>): String {
        return "{\"result\": \"${Statistics.rankList.get()}\"}"
    }
}