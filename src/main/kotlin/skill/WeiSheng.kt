package com.fengsheng.skill

import com.fengsheng.Player
import com.fengsheng.protos.Common.color.Has_No_Identity

/**
 * 顾小梦技能【尾声】：你获得胜利时，没有身份牌的玩家与你一同获得胜利。
 */
class WeiSheng : InitialSkill, ChangeGameResultSkill {
    override val skillId = SkillId.WEI_SHENG

    override fun changeGameResult(
        r: Player,
        whoseTurn: Player,
        declaredWinners: MutableList<Player>,
        winners: MutableList<Player>
    ) {
        r.roleFaceUp || return
        winners.any { it === r } || return
        r.game!!.players.forEach { if (it!!.identity == Has_No_Identity) winners.add(it) }
    }
}