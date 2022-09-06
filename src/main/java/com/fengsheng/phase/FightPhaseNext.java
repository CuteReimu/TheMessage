package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;

/**
 * 争夺阶段即将询问下一个人时
 *
 * @param fightPhase 原先那个人的 {@link FightPhaseIdle} （不是下一个人的）
 */
public record FightPhaseNext(FightPhaseIdle fightPhase) implements Fsm {
    @Override
    public ResolveResult resolve() {
        Player[] players = fightPhase.whoseFightTurn.getGame().getPlayers();
        int whoseFightTurn = fightPhase.whoseFightTurn.location();
        while (true) {
            whoseFightTurn = (whoseFightTurn + 1) % players.length;
            if (whoseFightTurn == fightPhase.inFrontOfWhom.location())
                return new ResolveResult(new ReceivePhase(fightPhase.whoseTurn, fightPhase.messageCard, fightPhase.inFrontOfWhom), true);
            else if (players[whoseFightTurn].isAlive())
                break;
        }
        fightPhase.whoseFightTurn = players[whoseFightTurn];
        return new ResolveResult(fightPhase, true);
    }
}
