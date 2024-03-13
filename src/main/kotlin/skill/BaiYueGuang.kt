package com.fengsheng.skill

import com.fengsheng.Player
import com.fengsheng.addPlayer
import com.fengsheng.card.count
import com.fengsheng.containsPlayer
import com.fengsheng.protos.Common.color.Blue

/**
 * CP韩梅技能【白月光】：只要“小九”在场，你摸牌阶段多摸一张牌。若“小九”拥有3张或更多蓝色情报且未宣胜，你与其单独胜利。
 */
class BaiYueGuang : ChangeDrawCardCountSkill, WinSkill {
    override val skillId = SkillId.BAI_YUE_GUANG

    override val isInitialSkill = true

    override fun changeDrawCardCount(player: Player, oldCount: Int) =
        if (player.game!!.players.any(::isXiaoJiu)) oldCount + 1 else oldCount

    override fun checkWin(
        r: Player,
        whoseTurn: Player,
        declaredWinner: MutableMap<Int, Player>,
        winner: MutableMap<Int, Player>
    ) {
        for (p in r.game!!.players) {
            if (p == null) continue
            isXiaoJiu(p) && p.messageCards.count(Blue) >= 3 && !declaredWinner.containsPlayer(p) || continue
            declaredWinner.addPlayer(r)
            winner.addPlayer(r)
            winner.addPlayer(p)
        }
    }
}

fun isXiaoJiu(p: Player?) = p!!.alive && p.roleFaceUp && p.roleName.endsWith("小九")
