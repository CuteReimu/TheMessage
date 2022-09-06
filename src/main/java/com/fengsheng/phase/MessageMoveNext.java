package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.protos.Common;
import org.apache.log4j.Logger;

/**
 * 情报传递阶段，情报移到下一个人
 *
 * @param sendPhase 原先那个人的 {@link SendPhaseIdle} （不是下一个人的）
 */
public record MessageMoveNext(SendPhaseIdle sendPhase) implements Fsm {
    private static final Logger log = Logger.getLogger(MessageMoveNext.class);

    @Override
    public ResolveResult resolve() {
        if (sendPhase.dir == Common.direction.Up) {
            if (sendPhase.whoseTurn.isAlive()) {
                sendPhase.inFrontOfWhom = sendPhase.whoseTurn;
                log.info("情报到达" + sendPhase.inFrontOfWhom + "面前");
                return new ResolveResult(sendPhase, true);
            } else {
                return new ResolveResult(new NextTurn(sendPhase.whoseTurn), true);
            }
        } else {
            Player[] players = sendPhase.whoseTurn.getGame().getPlayers();
            int inFrontOfWhom = sendPhase.inFrontOfWhom.location();
            while (true) {
                if (sendPhase.dir == Common.direction.Left)
                    inFrontOfWhom = (inFrontOfWhom + players.length - 1) % players.length;
                else
                    inFrontOfWhom = (inFrontOfWhom + 1) % players.length;
                sendPhase.inFrontOfWhom = players[inFrontOfWhom];
                if (sendPhase.inFrontOfWhom.isAlive()) {
                    log.info("情报到达" + sendPhase.inFrontOfWhom + "面前");
                    return new ResolveResult(sendPhase, true);
                } else if (sendPhase.whoseTurn == sendPhase.inFrontOfWhom) {
                    return new ResolveResult(new NextTurn(sendPhase.whoseTurn), true);
                }
            }
        }
    }
}
