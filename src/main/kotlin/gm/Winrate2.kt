package com.fengsheng.gm

import com.fengsheng.Image
import java.util.function.Function

class Winrate2 : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>) = gson.toJson(Image.getWinRateJson())!!
}
