package com.fengsheng.skill

import com.fengsheng.Player
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red

/**
 * 秦圆圆技能【比翼双飞】：你的回合中，当一名男性角色胜利时，你可以与他一同胜利，且令与他同阵营的其他角色无法胜利。
 */
class BiYiShuangFei : InitialSkill, ChangeGameResultSkill {
    override val skillId = SkillId.BI_YI_SHUANG_FEI

    override fun changeGameResult(
        r: Player,
        whoseTurn: Player,
        declaredWinners: List<Player>,
        winners: MutableList<Player>
    ) {
        r.roleFaceUp || return
        r === whoseTurn || return // 自己的回合才能发动技能
        !winners.any { it === r } || return // 自己没赢才能发动技能
        val target = declaredWinners.filter { it.isMale }.randomOrNull() ?: return
        if (target.identity == Red || target.identity == Blue)
            winners.removeIf { it !== target && it.identity == target.identity }
        winners.add(whoseTurn)
    }
}