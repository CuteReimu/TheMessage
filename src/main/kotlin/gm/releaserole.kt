package com.fengsheng.gm

import com.fengsheng.skill.RoleCache
import java.util.function.Function

class releaserole : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            val result = RoleCache.releaseRole(name)
            "{\"result\": $result}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}