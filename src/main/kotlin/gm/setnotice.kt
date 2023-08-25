package com.fengsheng.gm

import com.fengsheng.Config
import java.net.URLDecoder
import java.util.function.Function

class setnotice : Function<Map<String, String?>, String> {
    override fun apply(form: Map<String, String?>): String {
        return try {
            val notice = URLDecoder.decode(form["notice"]!!, Charsets.UTF_8)
            Config.Notice.set(notice)
            Config.save()
            "{\"result\": true}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}