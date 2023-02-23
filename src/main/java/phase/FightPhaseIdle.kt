package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card

/**
 * 争夺阶段空闲时点
 */
data class FightPhaseIdle(
    /**
     * 谁的回合
     */
    val whoseTurn: Player,
    /**
     * 情报牌
     */
    val messageCard: Card,
    /**
     * 情报在谁面前
     */
    val inFrontOfWhom: Player,
    /**
     * 正在询问谁
     */
    val whoseFightTurn: Player,
    /**
     * 情报是否面朝上
     */
    val isMessageCardFaceUp: Boolean
) : Fsm {
    override fun resolve(): ResolveResult? {
        if (!whoseFightTurn.alive || whoseFightTurn.game!!.jinBiPlayer === whoseFightTurn) return ResolveResult(
            FightPhaseNext(this),
            true
        )
        for (p in whoseTurn.game!!.players) p!!.notifyFightPhase(15)
        return null
    }

    override fun toString(): String {
        return "${whoseTurn}的回合的争夺阶段，情报在${inFrontOfWhom}面前，正在询问${whoseFightTurn}"
    }
}