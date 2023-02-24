package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.ResolveResult
import com.fengsheng.protos.Common

/**
 * 濒死求澄清时，使用了澄清
 */
data class UseChengQingOnDying(val waitForChengQing: WaitForChengQing) : Fsm {
    override fun resolve(): ResolveResult {
        var count = 0
        for (card in waitForChengQing.whoDie.messageCards) {
            for (color in card.colors) {
                if (color == Common.color.Black) count++
            }
        }
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