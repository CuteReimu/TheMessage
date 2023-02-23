package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.direction

data class SendPhaseIdle(
    /**
     * 谁的回合
     */
    val whoseTurn: Player,
    /**
     * 传递的情报牌
     */
    val messageCard: Card,
    /**
     * 传递方向
     */
    val dir: direction,
    /**
     * 情报在谁面前
     */
    val inFrontOfWhom: Player,
    /**
     * 被锁定的玩家
     */
    val lockedPlayers: Array<Player>,
    /**
     * 情报是否面朝上
     */
    val isMessageCardFaceUp: Boolean
) : Fsm {
    override fun resolve(): ResolveResult? {
        for (p in whoseTurn.game!!.players) {
            p!!.notifySendPhase(15)
        }
        return null
    }

    override fun toString(): String {
        return whoseTurn.toString() + "的回合的情报传递阶段，情报在" + inFrontOfWhom + "面前"
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

        return true
    }

    override fun hashCode(): Int {
        var result = whoseTurn.hashCode()
        result = 31 * result + messageCard.hashCode()
        result = 31 * result + dir.hashCode()
        result = 31 * result + inFrontOfWhom.hashCode()
        result = 31 * result + lockedPlayers.contentHashCode()
        result = 31 * result + isMessageCardFaceUp.hashCode()
        return result
    }
}