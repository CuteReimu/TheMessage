package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card

/**
 * 接收情报时的技能
 *
 * @param whoseTurn 谁的回合
 * @param messageCard 情报牌
 * @param receiveOrder 接收情报牌的顺序（也就是后续结算死亡的顺序）
 * @param inFrontOfWhom 情报在谁面前
 * @param receiveOrder 接收第三张黑色情报牌的顺序（也就是后续结算死亡的顺序）
 */
data class ReceivePhaseSkill(
    val whoseTurn: Player,
    val sender: Player,
    val messageCard: Card,
    val inFrontOfWhom: Player,
    val askWhom: Player = sender,
    override val receiveOrder: ReceiveOrder = ReceiveOrder()
) : Fsm, HasReceiveOrder {
    override fun resolve(): ResolveResult {
        val result = whoseTurn.game!!.dealListeningSkill()
        return result ?: ResolveResult(ReceivePhaseSkillNext(this), true)
    }

    override fun toString(): String {
        return "${whoseTurn}的回合，${inFrontOfWhom}成功接收情报"
    }

    private data class ReceivePhaseSkillNext(val fsm: ReceivePhaseSkill) : Fsm {
        override fun resolve(): ResolveResult {
            var askWhom = fsm.askWhom.location
            val players = fsm.askWhom.game!!.players
            while (true) {
                askWhom = (askWhom + 1) % players.size
                if (askWhom == fsm.sender.location) {
                    return ResolveResult(CheckWin(fsm.whoseTurn, fsm.receiveOrder, NextTurn(fsm.whoseTurn)), true)
                }
                if (players[askWhom]!!.alive) {
                    return ResolveResult(fsm.copy(askWhom = players[askWhom]!!), true)
                }
            }
        }
    }
}