package com.fengsheng.phase

import com.fengsheng.Player
import com.fengsheng.ProcessFsm
import com.fengsheng.ResolveResult
import com.fengsheng.SendCardEvent
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.direction

/**
 * 选择了要传递哪张情报时的角色技能
 *
 * @param whoseTurn     谁的回合
 * @param sender        情报传出者
 * @param messageCard   传递的情报牌
 * @param dir           传递方向
 * @param targetPlayer  传递的目标角色
 * @param lockedPlayers 被锁定的玩家
 * @param isMessageCardFaceUp 情报是否面朝上
 */
data class OnSendCardSkill(
    override val whoseTurn: Player,
    val sender: Player,
    val messageCard: Card,
    val dir: direction,
    val targetPlayer: Player,
    val lockedPlayers: Array<Player>,
    val isMessageCardFaceUp: Boolean,
) : ProcessFsm() {
    override fun onSwitch() {
        sender.game!!.addEvent(SendCardEvent(whoseTurn, sender, messageCard, targetPlayer, dir))
    }

    override fun resolve0(): ResolveResult {
        return ResolveResult(
            SendPhaseIdle(whoseTurn, messageCard, dir, targetPlayer, lockedPlayers, isMessageCardFaceUp, sender), true
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OnSendCardSkill

        if (whoseTurn != other.whoseTurn) return false
        if (messageCard != other.messageCard) return false
        if (dir != other.dir) return false
        if (targetPlayer != other.targetPlayer) return false
        if (!lockedPlayers.contentEquals(other.lockedPlayers)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = whoseTurn.hashCode()
        result = 31 * result + messageCard.hashCode()
        result = 31 * result + dir.hashCode()
        result = 31 * result + targetPlayer.hashCode()
        result = 31 * result + lockedPlayers.contentHashCode()
        return result
    }
}