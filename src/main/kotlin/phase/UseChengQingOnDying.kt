package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.count
import com.fengsheng.protos.Common.color.Black

/**
 * 濒死求澄清时，使用了澄清
 */
data class UseChengQingOnDying(val waitForChengQing: WaitForChengQing) : Fsm {
    override val whoseTurn: Player
        get() = waitForChengQing.whoseTurn

    override fun resolve(): ResolveResult {
        val count = waitForChengQing.whoDie.messageCards.count(Black)
        return if (count >= 3) ResolveResult(waitForChengQing, true) else ResolveResult(
            StartWaitForChengQing(
                waitForChengQing.whoseTurn,
                waitForChengQing.dyingQueue,
                waitForChengQing.diedQueue,
                waitForChengQing.afterDieResolve
            ), true
        )
    }
}
