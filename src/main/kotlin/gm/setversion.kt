package com.fengsheng.gm

import com.fengsheng.Config
import java.util.function.Function

class setversion : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["version"]!!
            Config.ClientVersion.set(name.toInt())
            Config.save()
            "{\"result\": true}"
        } catch (e: NumberFormatException) {
            "{\"error\": \"参数错误\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}
