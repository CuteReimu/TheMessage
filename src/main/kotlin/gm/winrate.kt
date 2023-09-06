package com.fengsheng.gm

import com.fengsheng.WinRate
import java.util.function.Function

class winrate : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>) = WinRate.getWinRateImage()
}