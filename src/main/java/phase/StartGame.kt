package com.fengsheng.phase

import com.fengsheng.*

com.fengsheng.protos.Common.cardimport java.util.concurrent.* org.apache.log4j.Loggerimport java.util.*import java.util.concurrent.*

/**
 * 游戏马上开始
 */
class StartGame(game: Game) : Fsm {
    override fun resolve(): ResolveResult? {
        Game.Companion.GameCache.put(game.id, game)
        val players = game.players
        log.info("游戏开始了，场上的角色依次是：" + Arrays.toString(players))
        game.deck = Deck(game, players.size)
        val whoseTurn = ThreadLocalRandom.current().nextInt(players.size)
        for (i in players.indices) players[(whoseTurn + i) % players.size].init()
        for (i in players.indices) players[(whoseTurn + i) % players.size].draw(Config.HandCardCountBegin)
        GameExecutor.Companion.post(game, Runnable { game.resolve(DrawPhase(players[whoseTurn])) }, 1, TimeUnit.SECONDS)
        return null
    }

    val game: Game

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
    }

    companion object {
        private val log = Logger.getLogger(StartGame::class.java)
    }
}