package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.phase.StartWaitForChengQingimport

com.fengsheng.protos.Common.card org.apache.log4j.Loggerimport java.util.*
/**
 * 判断是否需要濒死求澄清
 *
 * @param whoseTurn       谁的回合
 * @param diedQueue       接收第三张黑色情报的顺序，也就是后续结算濒死的顺序
 * @param afterDieResolve 濒死结算后的下一个动作
 */
internal class StartWaitForChengQing(
    whoseTurn: Player,
    dyingQueue: Queue<Player>,
    diedQueue: List<Player?>,
    afterDieResolve: Fsm?
) : Fsm {
    constructor(whoseTurn: Player, dyingQueue: LinkedList<Player>, afterDieResolve: Fsm?) : this(
        whoseTurn,
        dyingQueue,
        ArrayList<Player?>(),
        afterDieResolve
    )

    override fun resolve(): ResolveResult? {
        if (dyingQueue.isEmpty()) return ResolveResult(CheckKillerWin(whoseTurn, diedQueue, afterDieResolve), true)
        val whoDie = dyingQueue.poll()
        log.info(whoDie.toString() + "濒死")
        val next = WaitForChengQing(whoseTurn, whoDie, whoseTurn, dyingQueue, diedQueue, afterDieResolve)
        return ResolveResult(if (whoDie.isAlive) next else WaitNextForChengQing(next), true)
    }

    val whoseTurn: Player
    val dyingQueue: Queue<Player>
    val diedQueue: List<Player?>
    val afterDieResolve: Fsm?

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
        this.whoseTurn = whoseTurn
        this.dyingQueue = dyingQueue
        this.diedQueue = diedQueue
        this.afterDieResolve = afterDieResolve
    }

    companion object {
        private val log = Logger.getLogger(StartWaitForChengQing::class.java)
    }
}