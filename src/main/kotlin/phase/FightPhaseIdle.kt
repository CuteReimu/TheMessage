package com.fengsheng.phase

import com.fengsheng.Config
import com.fengsheng.Player
import com.fengsheng.ProcessFsm
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card
import com.fengsheng.skill.cannotPlayCardAndSkill

/**
 * 争夺阶段空闲时点
 *
 * @param whoseTurn 谁的回合
 * @param sender 情报传出者
 * @param messageCard 情报牌
 * @param inFrontOfWhom 情报在谁面前
 * @param whoseFightTurn 正在询问谁
 * @param isMessageCardFaceUp 情报是否面朝上
 */
data class FightPhaseIdle(
    override val whoseTurn: Player,
    val sender: Player,
    val messageCard: Card,
    val inFrontOfWhom: Player,
    val whoseFightTurn: Player,
    val isMessageCardFaceUp: Boolean
) : ProcessFsm() {
    override fun resolve0(): ResolveResult? {
        if (!whoseFightTurn.alive || whoseFightTurn.cannotPlayCardAndSkill())
            return ResolveResult(FightPhaseNext(this), true)
        for (p in whoseTurn.game!!.players)
            p!!.notifyFightPhase(Config.WaitSecond)
        return null
    }

    override fun toString(): String {
        return "${whoseTurn}的回合的争夺阶段，情报在${inFrontOfWhom}面前，正在询问${whoseFightTurn}"
    }
}