package com.fengsheng.gm

import com.fengsheng.Image
import java.util.function.Function

class winrate : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>) = Image.getWinRateImage()
}