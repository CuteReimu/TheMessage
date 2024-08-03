package com.fengsheng.gm

import com.fengsheng.QQPusher
import java.util.function.Function

class Addnotify : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val qq = form["qq"]!!.toLong()
            val onStart = (form["when"]?.toInt() ?: 0) == 0
            val result = QQPusher.addIntoNotifyQueue(qq, onStart)
            "{\"result\": $result}"
        } catch (e: NumberFormatException) {
            "{\"error\": \"参数错误\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}
