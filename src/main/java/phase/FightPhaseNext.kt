package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.card

/**
 * 争夺阶段即将询问下一个人时
 *
 * @param fightPhase 原先那个人的 [FightPhaseIdle] （不是下一个人的）
 */
class FightPhaseNext(fightPhase: FightPhaseIdle) : Fsm {
    override fun resolve(): ResolveResult? {
        val game = fightPhase.whoseFightTurn.game
        val players = game.players
        var whoseFightTurn = fightPhase.whoseFightTurn.location()
        while (true) {
            whoseFightTurn = (whoseFightTurn + 1) % players.size
            if (whoseFightTurn == fightPhase.inFrontOfWhom.location()) return ResolveResult(
                ReceivePhase(
                    fightPhase.whoseTurn,
                    fightPhase.messageCard,
                    fightPhase.inFrontOfWhom
                ), true
            ) else if (players[whoseFightTurn].isAlive && players[whoseFightTurn] !== game.jinBiPlayer) break
        }
        fightPhase.whoseFightTurn = players[whoseFightTurn]
        return ResolveResult(fightPhase, true)
    }

    val fightPhase: FightPhaseIdle

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
    }
}