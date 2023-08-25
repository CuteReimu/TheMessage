package com.fengsheng.gm

import com.fengsheng.MiraiPusher
import java.net.URLDecoder
import java.util.function.Function

class addnotify : Function<Map<String, String?>, String> {
    override fun apply(form: Map<String, String?>): String {
        return try {
            val qq = URLDecoder.decode(form["qq"]!!, Charsets.UTF_8).toLong()
            val onStart = URLDecoder.decode(form.getOrDefault("when", "0"), Charsets.UTF_8).toInt() == 0
            val result =
                if (onStart) MiraiPusher.notifyQueueOnStart.offer(qq)
                else MiraiPusher.notifyQueueOnEnd.offer(qq)
            "{\"result\": $result}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}