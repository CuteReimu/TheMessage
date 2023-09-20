package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.skill.SkillId

/**
 * 当情报被置入情报区后
 * @param whoseTurn 谁的回合
 * @param afterResolve 结算完这个阶段后结算什么
 * @param bySkill 是否是因为角色技能置入情报区的
 */
data class OnAddMessageCard(
    val whoseTurn: Player,
    val afterResolve: Fsm,
    val bySkill: Boolean = true,
) : Fsm {
    override fun resolve(): ResolveResult {
        val result = whoseTurn.game!!.dealListeningSkill(whoseTurn.location)
        if (result != null) return result
        whoseTurn.game!!.players.forEach { it!!.resetSkillUseCount(SkillId.XIAN_FA_ZHI_REN) }
        return ResolveResult(afterResolve, true)
    }
}