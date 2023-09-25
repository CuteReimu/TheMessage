package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.direction
import org.apache.log4j.Logger

/**
 * 选择了要传递哪张情报时的角色技能
 *
 * @param whoseTurn     谁的回合
 * @param sender        情报传出者
 * @param messageCard   传递的情报牌
 * @param dir           传递方向
 * @param targetPlayer  传递的目标角色
 * @param lockedPlayers 被锁定的玩家
 * @param byYuQinGuZong 是否是因为欲擒故纵传递的
 */
data class OnSendCardSkill(
    val whoseTurn: Player,
    val sender: Player,
    val messageCard: Card,
    val dir: direction,
    val targetPlayer: Player,
    val lockedPlayers: Array<Player>,
    val byYuQinGuZong: Boolean = false,
) : Fsm {
    override fun resolve(): ResolveResult {
        val result = whoseTurn.game!!.dealListeningSkill(whoseTurn.location)
        if (result != null) return result
        log.info("情报到达${targetPlayer}面前")
        return ResolveResult(
            SendPhaseIdle(whoseTurn, messageCard, dir, targetPlayer, lockedPlayers, byYuQinGuZong, sender),
            true
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

    companion object {
        private val log = Logger.getLogger(OnSendCardSkill::class.java)
    }
}