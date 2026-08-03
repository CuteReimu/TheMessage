package com.fengsheng.gm

import com.fengsheng.QQPusher
import java.util.function.Function

class AddGroupMessage : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        val msg = form["msg"]!!
        QQPusher.groupMessages.add(msg)
        return """{"result": "ok"}"""
    }
}
