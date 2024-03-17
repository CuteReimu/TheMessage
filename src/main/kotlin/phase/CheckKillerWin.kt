package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.card.count
import com.fengsheng.card.countTrueCard
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.unknownWaitingToc
import com.fengsheng.skill.changeGameResult
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 判断镇压者获胜条件
 *
 * @param whoseTurn       谁的回合
 * @param diedQueue       死亡顺序
 * @param afterDieResolve 死亡结算后的下一个动作
 */
data class CheckKillerWin(val whoseTurn: Player, val diedQueue: List<Player>, val afterDieResolve: Fsm) : Fsm {
    override fun resolve(): ResolveResult {
        if (diedQueue.isEmpty())
            return ResolveResult(afterDieResolve, true)
        val players = whoseTurn.game!!.players.filterNotNull().filter { !it.lose }
        val killer = players.find { it.identity == Black && it.secretTask == Killer } // 镇压者
        val stealer = players.find { it.identity == Black && it.secretTask == Stealer } // 簒夺者
        val sweeper = // 清道夫
            players.find { (it.alive || it.dieJustNow) && it.identity == Black && it.secretTask == Sweeper }
        val pioneer = diedQueue.find { it.identity == Black && it.secretTask == Pioneer } // 先行者
        var declaredWinner = ArrayList<Player>()
        var winner = ArrayList<Player>()
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
        if (declaredWinner.isNotEmpty() && stealer != null && stealer === whoseTurn && !winner.any { it === stealer }) {
            declaredWinner = arrayListOf(stealer)
            winner = arrayListOf(stealer)
        }
        if (declaredWinner.isNotEmpty()) {
            whoseTurn.game!!.changeGameResult(whoseTurn, declaredWinner, winner)
            logger.info("${declaredWinner.joinToString()}宣告胜利，胜利者有${winner.joinToString()}")
            whoseTurn.game!!.players.send { unknownWaitingToc { } }
            GameExecutor.post(whoseTurn.game!!, {
                whoseTurn.game!!.allPlayerSetRoleFaceUp()
                whoseTurn.game!!.end(declaredWinner, winner)
            }, 1, TimeUnit.SECONDS)
            return ResolveResult(null, false)
        }
        if (players.all { !it.alive }) {
            logger.info("全部死亡，游戏结束")
            whoseTurn.game!!.players.send { unknownWaitingToc { } }
            GameExecutor.post(whoseTurn.game!!, {
                whoseTurn.game!!.allPlayerSetRoleFaceUp()
                whoseTurn.game!!.end(emptyList(), emptyList())
            }, 1, TimeUnit.SECONDS)
            return ResolveResult(null, false)
        }
        if (whoseTurn.game!!.checkOnlyOneAliveIdentityPlayers(whoseTurn))
            return ResolveResult(null, false)
        return ResolveResult(WaitForDieGiveCard(whoseTurn, diedQueue, afterDieResolve), true)
    }
}