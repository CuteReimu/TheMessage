package com.fengsheng.gm

import com.fengsheng.Config
import java.util.function.Function

class setnotice : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val notice = form["notice"]!!
            Config.Notice.set(notice)
            Config.save()
            "{\"result\": true}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}