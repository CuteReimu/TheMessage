package com.fengsheng.phase

import com.fengsheng.DiscardCardEvent
import com.fengsheng.Fsm
import com.fengsheng.ResolveResult

/**
 * 死亡给三张牌后，判断是否有下一个人需要结算死亡给三张牌
 */
data class AfterDieGiveCard(val dieGiveCard: WaitForDieGiveCard) : Fsm {
    override val whoseTurn
        get() = dieGiveCard.whoseTurn

    override fun resolve(): ResolveResult {
        val player = dieGiveCard.diedQueue[dieGiveCard.diedIndex]
        val cards = player.cards.toList()
        player.game!!.playerDiscardCard(player, cards)
        val messageCards = player.messageCards.toList()
        player.messageCards.clear()
        player.game!!.deck.discard(messageCards)
        for (p in player.game!!.players) {
            p!!.notifyDie(player.location)
        }
        dieGiveCard.diedIndex++
        if (player.cards.isNotEmpty())
            player.game!!.addEvent(DiscardCardEvent(dieGiveCard.whoseTurn, player))
        return ResolveResult(dieGiveCard, true)
    }
}
