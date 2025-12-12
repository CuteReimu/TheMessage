package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class Register : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            if (name.length > 12) return gson.toJson(mapOf("error" to "名字太长"))
            if (invalidString.any { it in name }) return gson.toJson(mapOf("error" to "名字中含有非法字符"))
            if ("名字" in name) return gson.toJson(mapOf("error" to "不能含有“名字”二字"))
            val result = Statistics.register(name)
            gson.toJson(mapOf("result" to result))
        } catch (e: NullPointerException) {
            gson.toJson(mapOf("error" to "参数错误"))
        }
    }

    companion object {
        private val invalidString = listOf(",", "·", "{", "$", "}", " ", "\t", "\n", "\r")
    }
}
