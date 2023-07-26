package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class resetpwd : Function<Map<String, String?>, String> {
    override fun apply(form: Map<String, String?>): String {
        return try {
            val name = form["name"]!!
            if (Statistics.resetPassword(name))
                "{\"msg\": \"success\"}"
            else
                "{\"error\": \"player not exists\"}"
        } catch (e: NumberFormatException) {
            "{\"error\": \"invalid arguments\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"invalid arguments\"}"
        }
    }
}