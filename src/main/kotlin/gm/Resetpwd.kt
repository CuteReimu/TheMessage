package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class Resetpwd : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            if (Statistics.resetPassword(name))
                "{\"result\": \"重置成功\"}"
            else
                "{\"result\": \"玩家不存在\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}
