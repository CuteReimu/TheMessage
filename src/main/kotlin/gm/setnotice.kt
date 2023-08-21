package com.fengsheng.gm

import com.fengsheng.Config
import java.util.function.Function

class setnotice : Function<Map<String, String?>, String> {
    override fun apply(form: Map<String, String?>): String {
        return try {
            val notice = form["notice"]!!
            Config.Notice.set(notice)
            Config.save()
            "{\"result\": true}"
        } catch (e: NumberFormatException) {
            "{\"error\": \"invalid arguments\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"invalid arguments\"}"
        }
    }
}