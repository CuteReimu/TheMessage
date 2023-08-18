package com.fengsheng.gm

import com.fengsheng.ScoreFactory
import com.fengsheng.Statistics
import java.util.function.Function

class getscore : Function<Map<String, String?>, String> {
    override fun apply(form: Map<String, String?>): String {
        return try {
            val name = form["name"]!!
            val score = Statistics.getScore(name)
            if (score == null) "{\"result\": \"查无此人\"}"
            else "{\"result\": \"name ${ScoreFactory.getRankNameByScore(score)} $score\"}"
        } catch (e: NumberFormatException) {
            "{\"error\": \"invalid arguments\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"invalid arguments\"}"
        }
    }
}