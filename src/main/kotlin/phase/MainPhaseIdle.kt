package com.fengsheng.phase

import com.fengsheng.Config
import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult

/**
 * 出牌阶段空闲时点
 */
data class MainPhaseIdle(val player: Player) : Fsm {
    override fun resolve(): ResolveResult? {
        if (!player.alive) {
            return ResolveResult(NextTurn(player), true)
        }
        for (p in player.game!!.players) {
            p!!.notifyMainPhase(Config.WaitSecond * 4 / 3)
        }
        return null
    }

    override fun toString(): String {
        return "${player}的出牌阶段"
    }
}