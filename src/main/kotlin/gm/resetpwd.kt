package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class resetpwd : Function<Map<String, String?>, String> {
    override fun apply(form: Map<String, String?>): String {
        return try {
            val name = form["name"]!!
            if (Statistics.resetPassword(name))
                "{\"msg\": \"成功\"}"
            else
                "{\"error\": \"玩家不存在\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}