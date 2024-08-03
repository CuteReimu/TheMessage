package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class Addenergy : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            val energy = form["energy"]!!.toInt()
            val playerInfo = Statistics.getPlayerInfo(name) ?: return "{\"error\": \"玩家不存在\"}"
            val forbidLeft = playerInfo.forbidUntil - System.currentTimeMillis()
            if (forbidLeft > 0) {
                "{\"result\": false}"
            } else {
                val result = Statistics.addEnergy(name, energy, true)
                "{\"result\": $result}"
            }
        } catch (e: NumberFormatException) {
            "{\"error\": \"参数错误\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}
