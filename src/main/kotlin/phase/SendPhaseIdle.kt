package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.direction

/**
 * 传递阶段情报传到某人面前的空闲时点
 *
 * @param whoseTurn 谁的回合
 * @param messageCard 传递的情报牌
 * @param dir 传递方向
 * @param inFrontOfWhom 情报在谁面前
 * @param lockedPlayers 被锁定的玩家
 * @param isMessageCardFaceUp 情报是否面朝上
 * @param sender 情报传出者
 */
data class SendPhaseIdle(
    override val whoseTurn: Player,
    val messageCard: Card,
    val dir: direction,
    val inFrontOfWhom: Player,
    val lockedPlayers: List<Player>,
    val isMessageCardFaceUp: Boolean,
    val sender: Player,
) : ProcessFsm() {
    override fun onSwitch() {
        for (p in whoseTurn.game!!.players) {
            p!!.notifySendPhase()
        }
        if (inFrontOfWhom.alive)
            whoseTurn.game!!.addEvent(MessageMoveNextEvent(whoseTurn, messageCard, inFrontOfWhom, isMessageCardFaceUp))
    }

    override fun resolve0(): ResolveResult? {
        if (!inFrontOfWhom.alive) {
            return if (inFrontOfWhom === sender)
                ResolveResult(OnReceiveCard(whoseTurn, sender, messageCard, inFrontOfWhom), true)
            else // 死人的锁不会生效
                ResolveResult(MessageMoveNext(this), true)
        }
        for (p in whoseTurn.game!!.players) {
            p!!.notifySendPhase(Config.WaitSecond)
        }
        inFrontOfWhom.startSendPhaseTimer(Config.WaitSecond)
        return null
    }

    override fun toString(): String {
        return "${whoseTurn}的回合的情报传递阶段，传出者是${sender}，情报在${inFrontOfWhom}面前"
    }
}