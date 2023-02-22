package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.card

/**
 * 出牌阶段空闲时点
 */
data class MainPhaseIdle(val player: Player) : Fsm {
    override fun resolve(): ResolveResult? {
        if (!player.isAlive) {
            return ResolveResult(NextTurn(player), true)
        }
        for (p in player.game.players) {
            p.notifyMainPhase(20)
        }
        return null
    }

    override fun toString(): String {
        return player.toString() + "的出牌阶段"
    }

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
    }
}