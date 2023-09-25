package com.fengsheng.phase

import com.fengsheng.Config
import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
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
    val whoseTurn: Player,
    val messageCard: Card,
    val dir: direction,
    val inFrontOfWhom: Player,
    val lockedPlayers: Array<Player>,
    val isMessageCardFaceUp: Boolean,
    val sender: Player,
) : Fsm {
    override fun resolve(): ResolveResult? {
        if (!inFrontOfWhom.alive) {
            return if (inFrontOfWhom === sender)
                ResolveResult(ReceivePhase(whoseTurn, sender, messageCard, inFrontOfWhom), true)
            else // 死人的锁不会生效
                ResolveResult(MessageMoveNext(this), true)
        }
        for (p in whoseTurn.game!!.players) {
            p!!.notifySendPhase(Config.WaitSecond)
        }
        val result = whoseTurn.game!!.dealListeningSkill(inFrontOfWhom.location)
        if (result == null) inFrontOfWhom.startSendPhaseTimer(Config.WaitSecond)
        return result
    }

    override fun toString(): String {
        return "${whoseTurn}的回合的情报传递阶段，传出者是${sender}，情报在${inFrontOfWhom}面前"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SendPhaseIdle

        if (whoseTurn != other.whoseTurn) return false
        if (messageCard != other.messageCard) return false
        if (dir != other.dir) return false
        if (inFrontOfWhom != other.inFrontOfWhom) return false
        if (!lockedPlayers.contentEquals(other.lockedPlayers)) return false
        if (isMessageCardFaceUp != other.isMessageCardFaceUp) return false
        if (sender != other.sender) return false

        return true
    }

    override fun hashCode(): Int {
        var result = whoseTurn.hashCode()
        result = 31 * result + messageCard.hashCode()
        result = 31 * result + dir.hashCode()
        result = 31 * result + inFrontOfWhom.hashCode()
        result = 31 * result + lockedPlayers.contentHashCode()
        result = 31 * result + isMessageCardFaceUp.hashCode()
        result = 31 * result + sender.hashCode()
        return result
    }
}