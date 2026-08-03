package com.fengsheng.gm

import com.fengsheng.QQPusher
import java.util.function.Function

class GetGroupMessages : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        val messages = ArrayList<String>()
        while (true) {
            val message = QQPusher.groupMessages.poll() ?: break
            messages.add(message)
        }
        return gson.toJson(messages)
    }
}
