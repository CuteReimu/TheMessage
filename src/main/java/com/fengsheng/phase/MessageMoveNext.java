package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
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
                return nextTurn();
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
                    return nextTurn();
                }
            }
        }
    }

    private ResolveResult nextTurn() {
        sendPhase.inFrontOfWhom.getGame().getDeck().discard(sendPhase.messageCard);
        if (!sendPhase.isMessageCardFaceUp) {
            Player[] players = sendPhase.whoseTurn.getGame().getPlayers();
            for (Player player : players) {
                if (player instanceof HumanPlayer p) {
                    var builder = Fengsheng.notify_phase_toc.newBuilder();
                    builder.setCurrentPlayerId(p.getAlternativeLocation(sendPhase.whoseTurn.location()));
                    builder.setCurrentPhase(Common.phase.Send_Phase);
                    builder.setMessagePlayerId(p.getAlternativeLocation(sendPhase.inFrontOfWhom.location()));
                    builder.setMessageCardDir(sendPhase.dir);
                    builder.setMessageCard(sendPhase.messageCard.toPbCard());
                    builder.setWaitingPlayerId(p.getAlternativeLocation(sendPhase.inFrontOfWhom.location()));
                    p.send(builder.build());
                }
            }
        }
        return new ResolveResult(new NextTurn(sendPhase.whoseTurn), true);
    }
}
