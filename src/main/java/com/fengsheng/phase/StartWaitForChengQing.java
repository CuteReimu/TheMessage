package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 判断是否需要濒死求澄清
 *
 * @param whoseTurn       谁的回合
 * @param diedQueue       接收第三张黑色情报的顺序，也就是后续结算濒死的顺序
 * @param afterDieResolve 濒死结算后的下一个动作
 */
record StartWaitForChengQing(Player whoseTurn, LinkedList<Player> dyingQueue, List<Player> diedQueue,
                             Fsm afterDieResolve) implements Fsm {
    private static final Logger log = Logger.getLogger(StartWaitForChengQing.class);

    StartWaitForChengQing(Player whoseTurn, LinkedList<Player> dyingQueue, Fsm afterDieResolve) {
        this(whoseTurn, dyingQueue, new ArrayList<>(), afterDieResolve);
    }

    @Override
    public ResolveResult resolve() {
        if (diedQueue.isEmpty())
            return new ResolveResult(new CheckKillerWin(whoseTurn, diedQueue, afterDieResolve), true);
        Player whoDie = dyingQueue.removeFirst();
        log.info(whoDie + "濒死");
        var next = new WaitForChengQing(whoseTurn, whoDie, whoseTurn, dyingQueue, diedQueue, afterDieResolve);
        return new ResolveResult(whoDie.isAlive() ? next : new WaitNextForChengQing(next), true);
    }
}
