package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.card

/**
 * 死亡给三张牌后，判断是否有下一个人需要结算死亡给三张牌
 */
class AfterDieGiveCard(dieGiveCard: WaitForDieGiveCard) : Fsm {
    override fun resolve(): ResolveResult? {
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

    val dieGiveCard: WaitForDieGiveCard

    init {
        this.card = card
        this.sendPhase = sendPhase
        this.r = r
        this.target = target
        this.card = card
        this.wantType = wantType
        this.r = r
        this.target = target
        this.card = card
        this.player = player
        this.card = card
        this.card = card
        this.drawCards = drawCards
        this.players = players
        this.mainPhaseIdle = mainPhaseIdle
        this.dieSkill = dieSkill
        this.player = player
        this.player = player
        this.onUseCard = onUseCard
        this.game = game
        this.whoseTurn = whoseTurn
        this.messageCard = messageCard
        this.dir = dir
        this.targetPlayer = targetPlayer
        this.lockedPlayers = lockedPlayers
        this.whoseTurn = whoseTurn
        this.messageCard = messageCard
        this.inFrontOfWhom = inFrontOfWhom
        this.player = player
        this.whoseTurn = whoseTurn
        this.diedQueue = diedQueue
        this.afterDieResolve = afterDieResolve
        this.fightPhase = fightPhase
        this.player = player
        this.sendPhase = sendPhase
        this.dieGiveCard = dieGiveCard
    }
}