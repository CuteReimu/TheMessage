package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.ResolveResult;
import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;

/**
 * 濒死求澄清时，使用了澄清
 */
public record UseChengQingOnDying(WaitForChengQing waitForChengQing) implements Fsm {
    @Override
    public ResolveResult resolve() {
        int count = 0;
        for (Card card : waitForChengQing.whoDie.getMessageCards().values()) {
            for (Common.color color : card.getColors()) {
                if (color == Common.color.Black) count++;
            }
        }
        if (count >= 3) return new ResolveResult(waitForChengQing, true);
        return new ResolveResult(new StartWaitForChengQing(waitForChengQing.whoseTurn, waitForChengQing.dyingQueue, waitForChengQing.diedQueue, waitForChengQing.afterDieResolve), true);
    }
}
