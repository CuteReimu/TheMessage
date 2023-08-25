package com.fengsheng.gm

import com.fengsheng.skill.RoleCache
import java.net.URLDecoder
import java.util.function.Function

class releaserole : Function<Map<String, String?>, String> {
    override fun apply(form: Map<String, String?>): String {
        return try {
            val name = URLDecoder.decode(form["name"]!!, Charsets.UTF_8)
            val result = RoleCache.releaseRole(name)
            "{\"result\": $result}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}