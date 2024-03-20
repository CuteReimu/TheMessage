package com.fengsheng.phase

import com.fengsheng.Config
import com.fengsheng.Player
import com.fengsheng.ProcessFsm
import com.fengsheng.ResolveResult

/**
 * 出牌阶段空闲时点
 */
data class MainPhaseIdle(
    /**
     * 表示当前回合的玩家。 **/
    override val whoseTurn: Player
) : ProcessFsm() {
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