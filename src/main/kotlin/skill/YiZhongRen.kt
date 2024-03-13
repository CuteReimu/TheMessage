package com.fengsheng.skill

import com.fengsheng.Player
import com.fengsheng.addPlayer
import com.fengsheng.card.count
import com.fengsheng.containsPlayer
import com.fengsheng.protos.Common.color.Red

/**
 * CP小九技能【意中人】：只要“韩梅”在场，你摸牌阶段多摸一张牌。若“韩梅”拥有3张或更多红色情报且未宣胜，你与其单独胜利。
 */
class YiZhongRen : ChangeDrawCardCountSkill, WinSkill {
    override val skillId = SkillId.YI_ZHONG_REN

    override val isInitialSkill = true

    override fun changeDrawCardCount(player: Player, oldCount: Int) =
        if (player.game!!.players.any(::isHanMei)) oldCount + 1 else oldCount

    override fun checkWin(
        r: Player,
        whoseTurn: Player,
        declaredWinner: MutableMap<Int, Player>,
        winner: MutableMap<Int, Player>
    ) {
        for (p in r.game!!.players) {
            if (p == null) continue
            isHanMei(p) && p.messageCards.count(Red) >= 3 && !declaredWinner.containsPlayer(p) || continue
            declaredWinner.addPlayer(r)
            winner.addPlayer(r)
            winner.addPlayer(p)
        }
    }
}

fun isHanMei(p: Player?) = p!!.alive && p.roleFaceUp && p.roleName.endsWith("韩梅")
