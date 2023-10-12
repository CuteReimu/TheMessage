package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.skill.SkillId.LENG_XUE_XUN_LIAN
import org.apache.log4j.Logger

/**
 * 情报传递阶段开始时，选择传递一张情报
 */
data class SendPhaseStart(override val whoseTurn: Player) : ProcessFsm() {
    /** 刚刚切到这个状态，需要先给客户端发一遍notify消息 */
    private var justSwitch = true

    override fun resolve0(): ResolveResult? {
        val game = whoseTurn.game!!
        if (justSwitch) {
            justSwitch = false
            game.addEvent(SendPhaseStartEvent(whoseTurn))
            for (p in game.players)
                p!!.notifySendPhaseStart()
            return ResolveResult(this, true)
        }
        if (whoseTurn.alive && whoseTurn.cards.isEmpty() && whoseTurn.findSkill(LENG_XUE_XUN_LIAN) == null) {
            log.info("${whoseTurn}没有情报可传，输掉了游戏")
            val messageCards = whoseTurn.messageCards.toTypedArray()
            whoseTurn.messageCards.clear()
            game.deck.discard(*messageCards)
            whoseTurn.lose = true
            whoseTurn.alive = false
            for (p in game.players) p!!.notifyDying(whoseTurn.location, true)
            for (p in game.players) p!!.notifyDie(whoseTurn.location)
        }
        if (!whoseTurn.alive)
            return ResolveResult(NextTurn(whoseTurn), true)
        if (game.checkOnlyOneAliveIdentityPlayers(whoseTurn))
            return ResolveResult(null, false)
        for (p in game.players)
            p!!.notifySendPhaseStart(Config.WaitSecond)
        return null
    }

    override fun toString(): String {
        return "${whoseTurn}的情报传递阶段开始时"
    }

    companion object {
        private val log = Logger.getLogger(SendPhaseStart::class.java)
    }
}