package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.cardimport

com.fengsheng.skill.JiangHuLingimport com.fengsheng.skill.JinBiimport com.fengsheng.skill.QiangLing
/**
 * 即将跳转到下一回合时
 *
 * @param player 当前回合的玩家（不是下回合的玩家）
 */
class NextTurn(player: Player) : Fsm {
    override fun resolve(): ResolveResult? {
        val game = player.game
        var whoseTurn = player.location()
        while (true) {
            whoseTurn = (whoseTurn + 1) % game.players.size
            val player = game.players[whoseTurn]
            if (player.isAlive) {
                for (p in game.players) {
                    p.resetSkillUseCount()
                }
                JinBi.Companion.resetJinBi(game)
                QiangLing.Companion.resetQiangLing(game)
                JiangHuLing.Companion.resetJiangHuLing(game)
                return ResolveResult(DrawPhase(player), true)
            }
        }
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
    }
}