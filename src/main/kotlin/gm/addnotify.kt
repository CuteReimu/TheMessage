package com.fengsheng.gm

import com.fengsheng.MiraiPusher
import java.util.function.Function

class addnotify : Function<Map<String, String>, String> {
    override fun apply(form: Map<String, String>): String {
        return try {
            val qq = form["qq"]!!.toLong()
            val onStart = (form["when"]?.toInt() ?: 0) == 0
            val result = MiraiPusher.addIntoNotifyQueue(qq, onStart)
            "{\"result\": $result}"
        } catch (e: NumberFormatException) {
            "{\"error\": \"参数错误\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}