package com.fengsheng.phase

import com.fengsheng.Config
import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.skill.SkillId.LENG_XUE_XUN_LIAN
import org.apache.log4j.Logger

/**
 * 情报传递阶段开始时，选择传递一张情报
 */
data class SendPhaseStart(val player: Player) : Fsm {
    override fun resolve(): ResolveResult? {
        val game = player.game!!
        val result = game.dealListeningSkill(player.location)
        if (result != null) return result
        if (player.alive && player.cards.isEmpty() && player.findSkill(LENG_XUE_XUN_LIAN) == null) {
            log.info("${player}没有情报可传，输掉了游戏")
            val messageCards = player.messageCards.toTypedArray()
            player.messageCards.clear()
            game.deck.discard(*messageCards)
            player.lose = true
            player.alive = false
            for (p in game.players) p!!.notifyDying(player.location, true)
            for (p in game.players) p!!.notifyDie(player.location)
        }
        if (!player.alive) {
            return ResolveResult(NextTurn(player), true)
        }
        if (game.checkOnlyOneAliveIdentityPlayers()) {
            return ResolveResult(null, false)
        }
        for (p in game.players) {
            p!!.notifySendPhaseStart(Config.WaitSecond)
        }
        return null
    }

    override fun toString(): String {
        return "${player}的情报传递阶段开始时"
    }

    companion object {
        private val log = Logger.getLogger(SendPhaseStart::class.java)
    }
}