package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.count
import com.fengsheng.card.countTrueCard
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.skill.changeGameResult
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
        val sweeper = // 清道夫
            players.find { (it.alive || it.dieJustNow) && it.identity == Black && it.secretTask == Sweeper }
        val pioneer = diedQueue.find { it.identity == Black && it.secretTask == Pioneer } // 先行者
        var declaredWinner = ArrayList<Player>()
        var winner: MutableList<Player> = ArrayList()
        if (whoseTurn === killer && diedQueue.any { it.messageCards.countTrueCard() >= 2 }) {
            declaredWinner.add(killer)
            winner.add(killer)
        }
        if (sweeper != null && diedQueue.any { it.messageCards.count(Red) <= 1 && it.messageCards.count(Blue) <= 1 }) {
            declaredWinner.add(sweeper)
            winner.add(sweeper)
        }
        if (pioneer != null && pioneer.messageCards.countTrueCard() >= 1) {
            declaredWinner.add(pioneer)
            winner.add(pioneer)
        }
        if (declaredWinner.isNotEmpty() && stealer != null && stealer === whoseTurn) {
            declaredWinner = arrayListOf(stealer)
            winner = arrayListOf(stealer)
        }
        whoseTurn.game!!.changeGameResult(whoseTurn, declaredWinner, winner)
        if (declaredWinner.isNotEmpty()) {
            val declaredWinners = declaredWinner.toTypedArray()
            val winners = winner.toTypedArray()
            log.info("${declaredWinners.contentToString()}宣告胜利，胜利者有${winners.contentToString()}")
            whoseTurn.game!!.allPlayerSetRoleFaceUp()
            whoseTurn.game!!.end(declaredWinner, winner)
            return ResolveResult(null, false)
        }
        if (players.all { !it.alive }) {
            log.info("全部死亡，游戏结束")
            whoseTurn.game!!.allPlayerSetRoleFaceUp()
            whoseTurn.game!!.end(emptyList(), emptyList())
            return ResolveResult(null, false)
        }
        if (whoseTurn.game!!.checkOnlyOneAlivePlayer(whoseTurn))
            return ResolveResult(null, false)
        return ResolveResult(WaitForDieGiveCard(whoseTurn, diedQueue, afterDieResolve), true)
    }

    companion object {
        private val log = Logger.getLogger(CheckKillerWin::class.java)
    }
}