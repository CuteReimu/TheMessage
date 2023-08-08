package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.countTrueCard
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.color.Has_No_Identity
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.skill.SkillId
import com.fengsheng.skill.SkillId.WEI_SHENG
import org.apache.log4j.Logger

/**
 * 判断镇压者获胜条件
 *
 * @param whoseTurn       谁的回合
 * @param diedQueue       死亡顺序
 * @param afterDieResolve 死亡结算后的下一个动作
 */
data class CheckKillerWin(val whoseTurn: Player, val diedQueue: List<Player>, val afterDieResolve: Fsm) : Fsm {
    override fun resolve(): ResolveResult {
        if (diedQueue.isEmpty()) {
            whoseTurn.game!!.players.forEach { it!!.dieJustNow = false }
            return ResolveResult(afterDieResolve, true)
        }
        val players = whoseTurn.game!!.players.filterNotNull().filter { !it.lose }
        val killer = players.find { it.identity == Black && it.secretTask == Killer } // 镇压者
        val stealer = players.find { it.identity == Black && it.secretTask == Stealer } // 簒夺者
        val sweeper = players.find { it.identity == Black && it.secretTask == Sweeper } // 清道夫
        var declaredWinner = ArrayList<Player>()
        var winner = ArrayList<Player>()
        if (whoseTurn === killer) {
            if (diedQueue.any { it.messageCards.countTrueCard() >= 2 }) {
                declaredWinner.add(killer)
                winner.add(killer)
            }
        } else if (whoseTurn === sweeper) {
            if (diedQueue.any { it.messageCards.countTrueCard() <= 1 }) {
                declaredWinner.add(sweeper)
                winner.add(sweeper)
            }
        }
        val pioneer = diedQueue.find { it.identity == Black && it.secretTask == Pioneer } // 先行者
        if (pioneer != null && pioneer.messageCards.countTrueCard() >= 1) {
            declaredWinner.add(pioneer)
            winner.add(pioneer)
        }
        if (declaredWinner.isNotEmpty() && stealer != null && stealer === whoseTurn) {
            declaredWinner = arrayListOf(stealer)
            winner = arrayListOf(stealer)
        }
        if (declaredWinner.isNotEmpty()) {
            if (winner.any { it.findSkill(WEI_SHENG) != null && it.roleFaceUp })
                winner.addAll(players.filter { it.identity == Has_No_Identity })
            if (whoseTurn.findSkill(SkillId.ZUO_YOU_FENG_YUAN) != null && whoseTurn.roleFaceUp && whoseTurn !in winner &&
                declaredWinner.size == 1 && declaredWinner[0].isMale
            ) winner = arrayListOf(declaredWinner[0], whoseTurn)
            val declaredWinners = declaredWinner.toTypedArray()
            val winners = winner.toTypedArray()
            log.info("${declaredWinners.contentToString()}宣告胜利，胜利者有${winners.contentToString()}")
            whoseTurn.game!!.allPlayerSetRoleFaceUp()
            for (p in players) p.notifyWin(declaredWinners, winners)
            whoseTurn.game!!.end(winner)
            return ResolveResult(null, false)
        }
        if (players.all { !it.alive }) {
            log.info("全部死亡，游戏结束")
            whoseTurn.game!!.allPlayerSetRoleFaceUp()
            for (p in players) {
                p.notifyWin(arrayOf(), arrayOf())
            }
            whoseTurn.game!!.end(emptyList())
            return ResolveResult(null, false)
        }
        return ResolveResult(DieSkill(whoseTurn, diedQueue, whoseTurn, afterDieResolve), true)
    }

    companion object {
        private val log = Logger.getLogger(CheckKillerWin::class.java)
    }
}