package com.fengsheng.phase

import com.fengsheng.*

/**
 * 死亡给三张牌后，判断是否有下一个人需要结算死亡给三张牌
 */
data class AfterDieGiveCard(val dieGiveCard: WaitForDieGiveCard) : Fsm {
    override fun resolve(): ResolveResult {
        val player = dieGiveCard.diedQueue[dieGiveCard.diedIndex]
        val cards = player!!.cards.values.toTypedArray()
        player.game.playerDiscardCard(player, *cards)
        player.game.deck.discard(*player.deleteAllMessageCards())
        for (p in player.game.players) {
            p.notifyDie(player.location())
        }
        dieGiveCard.diedIndex++
        return ResolveResult(dieGiveCard, true)
    }
}