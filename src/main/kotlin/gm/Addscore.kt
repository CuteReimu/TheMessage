package gm

import com.fengsheng.Statistics
import com.fengsheng.gm.gson
import java.util.function.Function

class Addscore : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val name = form["name"]!!
            val score = form["score"]!!.toInt()
            val newScore = Statistics.gmAddScore(name, score)
            gson.toJson(mapOf("result" to newScore))
        } catch (e: NumberFormatException) {
            gson.toJson(mapOf("error" to "参数错误"))
        } catch (e: NullPointerException) {
            gson.toJson(mapOf("error" to "参数错误"))
        }
    }
}
