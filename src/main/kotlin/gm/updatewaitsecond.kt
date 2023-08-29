package com.fengsheng.gm

import com.fengsheng.Config
import java.util.function.Function

class updatewaitsecond : Function<Map<String, String>, String> {
    override fun apply(form: Map<String, String>): String {
        return try {
            val second = form["second"]!!.toInt()
            if (second <= 0) {
                "{\"result\": false}"
            } else {
                Config.WaitingSecond.set(second)
                Config.save()
                "{\"result\": true}"
            }
        } catch (e: NumberFormatException) {
            "{\"error\": \"参数错误\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}