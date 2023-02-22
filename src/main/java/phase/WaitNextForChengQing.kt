package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.card

org.apache.log4j.Logger
/**
 * 濒死求澄清时，询问下一个人
 */
class WaitNextForChengQing(waitForChengQing: WaitForChengQing) : Fsm {
    override fun resolve(): ResolveResult? {
        val game = waitForChengQing.askWhom.game
        val players = game.players
        var askWhom = waitForChengQing.askWhom.location()
        while (true) {
            askWhom = (askWhom + 1) % players.size
            if (askWhom == waitForChengQing.whoseTurn.location()) {
                log.info("无人拯救，" + waitForChengQing.whoDie + "已死亡")
                waitForChengQing.whoDie.isAlive = false
                waitForChengQing.diedQueue.add(waitForChengQing.whoDie)
                for (p in players) p.notifyDying(waitForChengQing.whoDie.location(), false)
                return ResolveResult(
                    StartWaitForChengQing(
                        waitForChengQing.whoseTurn,
                        waitForChengQing.dyingQueue,
                        waitForChengQing.diedQueue,
                        waitForChengQing.afterDieResolve
                    ), true
                )
            }
            if (players[askWhom].isAlive && players[askWhom] !== game.jinBiPlayer) {
                waitForChengQing.askWhom = players[askWhom]
                return ResolveResult(waitForChengQing, true)
            }
        }
    }

    val waitForChengQing: WaitForChengQing

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
        this.whoseTurn = whoseTurn
        this.messageCard = messageCard
        this.inFrontOfWhom = inFrontOfWhom
        this.isMessageCardFaceUp = isMessageCardFaceUp
        this.waitForChengQing = waitForChengQing
        this.waitForChengQing = waitForChengQing
    }

    companion object {
        private val log = Logger.getLogger(WaitNextForChengQing::class.java)
    }
}