package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.card

org.apache.log4j.Logger
/**
 * 情报传递阶段开始时，选择传递一张情报
 */
class SendPhaseStart(player: Player) : Fsm {
    override fun resolve(): ResolveResult? {
        val game = player.game
        if (player.isAlive && player.cards.isEmpty()) {
            log.info(player.toString() + "没有情报可传，输掉了游戏")
            game.deck.discard(*player.deleteAllMessageCards())
            player.isLose = true
            player.isAlive = false
            for (p in game.players) p.notifyDying(player.location(), true)
            val alivePlayer = getOnlyOneAlivePlayer(game.players)
            if (alivePlayer != null) {
                CheckKillerWin.Companion.onlyOneAliveWinner(game, alivePlayer)
                return ResolveResult(null, false)
            }
            for (p in game.players) p.notifyDie(player.location())
        }
        if (!player.isAlive) {
            return ResolveResult(NextTurn(player), true)
        }
        for (p in game.players) {
            p.notifySendPhaseStart(15)
        }
        return null
    }

    override fun toString(): String {
        return player.toString() + "的情报传递阶段开始时"
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
    }

    companion object {
        private val log = Logger.getLogger(SendPhaseStart::class.java)
        private fun getOnlyOneAlivePlayer(players: Array<Player>): Player? {
            var alivePlayer: Player? = null
            for (p in players) {
                if (p.isAlive) {
                    alivePlayer = if (alivePlayer == null) p else return null
                }
            }
            return alivePlayer
        }
    }
}