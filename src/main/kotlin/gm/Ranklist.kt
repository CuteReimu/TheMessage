package com.fengsheng.gm

import com.fengsheng.Statistics
import java.util.function.Function

class Ranklist : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        val seasonRank = kotlin.runCatching { form["season_rank"]!!.toBoolean() }.getOrElse { false }
        return if (!seasonRank) Statistics.rankListImage.get()
        else Statistics.getSeasonRankList()
    }
}
