package com.fengsheng.gm

import com.fengsheng.Statistics
import java.net.URLDecoder
import java.util.function.Function

class resetpwd : Function<Map<String, String?>, String> {
    override fun apply(form: Map<String, String?>): String {
        return try {
            val name = URLDecoder.decode(form["name"]!!, Charsets.UTF_8)
            if (Statistics.resetPassword(name))
                "{\"msg\": \"重置成功\"}"
            else
                "{\"msg\": \"玩家不存在\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}