package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.phase.Main_Phase
import com.fengsheng.protos.Game.notify_phase_toc

/**
 * 出牌阶段空闲时点
 */
data class MainPhaseIdle(override val whoseTurn: Player) : ProcessFsm() {
    override fun onSwitch() {
        for (p in whoseTurn.game!!.players) {
            if (p is HumanPlayer) {
                val builder = notify_phase_toc.newBuilder()
                builder.currentPlayerId = p.getAlternativeLocation(whoseTurn.location)
                builder.currentPhase = Main_Phase
                p.send(builder.build())
            }
        }
    }

    override fun resolve0(): ResolveResult? {
        if (!whoseTurn.alive) {
            return ResolveResult(NextTurn(whoseTurn), true)
        }
        for (p in whoseTurn.game!!.players) {
            p!!.notifyMainPhase(Config.WaitSecond * 4 / 3)
        }
        return null
    }

    override fun toString(): String {
        return "${whoseTurn}的出牌阶段"
    }
}