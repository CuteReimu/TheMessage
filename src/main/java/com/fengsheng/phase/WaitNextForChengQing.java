package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import org.apache.log4j.Logger;

/**
 * 濒死求澄清时，询问下一个人
 */
public record WaitNextForChengQing(WaitForChengQing waitForChengQing) implements Fsm {
    private static final Logger log = Logger.getLogger(WaitNextForChengQing.class);

    @Override
    public ResolveResult resolve() {
        Player[] players = waitForChengQing.askWhom.getGame().getPlayers();
        int askWhom = waitForChengQing.askWhom.location();
        while (true) {
            askWhom = (askWhom + 1) % players.length;
            if (askWhom == waitForChengQing.whoseTurn.location()) {
                log.info("无人拯救，" + waitForChengQing.whoDie + "已死亡");
                waitForChengQing.whoDie.setAlive(false);
                waitForChengQing.diedQueue.add(waitForChengQing.whoDie);
                for (Player p : players)
                    p.notifyDying(waitForChengQing.whoDie.location(), false);
                return new ResolveResult(new StartWaitForChengQing(waitForChengQing.whoseTurn, waitForChengQing.dyingQueue, waitForChengQing.diedQueue, waitForChengQing.afterDieResolve), true);
            }
            if (players[askWhom].isAlive()) {
                waitForChengQing.askWhom = players[askWhom];
                return new ResolveResult(waitForChengQing, true);
            }
        }
    }
}
