package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class ranklist : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>) = Statistics.rankListImage.get()
}
