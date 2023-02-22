package com.fengsheng.phaseimport

import com.fengsheng.*

com.fengsheng.phase.MainPhaseIdleimport com.fengsheng.phase.NextTurnimport com.fengsheng.protos.Common.cardimport org.apache.log4j.Logger com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Common.card
import com.fengsheng.phase.NextTurn

/**
 * 摸牌阶段
 */
class DrawPhase(player: Player) : Fsm {
    override fun resolve(): ResolveResult? {
        if (!player.isAlive) {
            return ResolveResult(NextTurn(player), true)
        }
        DrawPhase.Companion.log.info(player.toString() + "的回合开始了")
        for (p in player.game.players) {
            p.notifyDrawPhase()
        }
        player.draw(Config.HandCardCountEachTurn)
        return ResolveResult(MainPhaseIdle(player), true)
    }

    override fun toString(): String {
        return player.toString() + "的摸牌阶段"
    }

    val player: Player

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
    }

    companion object {
        private val log = Logger.getLogger(DrawPhase::class.java)
    }
}