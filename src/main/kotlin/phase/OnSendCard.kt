package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Fengsheng.send_message_card_toc
import org.apache.logging.log4j.kotlin.logger

/**
 * 选择了要传递哪张情报时
 *
 * @param whoseTurn     谁的回合
 * @param sender        情报传出者
 * @param messageCard   传递的情报牌
 * @param dir           传递方向
 * @param targetPlayer  传递的目标角色
 * @param lockedPlayers 被锁定的玩家
 * @param isMessageCardFaceUp 情报是否面朝上
 * @param needRemoveCardAndNotify 是否需要移除手牌并且广播[send_message_card_toc]
 */
data class OnSendCard(
    val whoseTurn: Player,
    val sender: Player,
    val messageCard: Card,
    val dir: direction,
    val targetPlayer: Player,
    val lockedPlayers: List<Player>,
    val isMessageCardFaceUp: Boolean = false,
    val needRemoveCard: Boolean = true,
    val needNotify: Boolean = true
) : Fsm {
    override fun resolve(): ResolveResult {
        var s = "${sender}传出了${messageCard}，方向是${dir}，传给了${targetPlayer}"
        if (lockedPlayers.isNotEmpty()) s += "，并锁定了${lockedPlayers.joinToString()}"
        logger.info(s)
        if (needRemoveCard)
            sender.deleteCard(messageCard.id)
        if (needNotify) {
            for (p in whoseTurn.game!!.players)
                p!!.notifySendMessageCard(whoseTurn, sender, targetPlayer, lockedPlayers, messageCard, dir)
        }
        return ResolveResult(
            OnSendCardSkill(whoseTurn, sender, messageCard, dir, targetPlayer, lockedPlayers, isMessageCardFaceUp), true
        )
    }
}