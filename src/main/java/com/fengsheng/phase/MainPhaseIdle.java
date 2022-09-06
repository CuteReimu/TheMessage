package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;

/**
 * 出牌阶段空闲时点
 */
public record MainPhaseIdle(Player player) implements Fsm {
    @Override
    public ResolveResult resolve() {
        if (!player.isAlive()) {
            return new ResolveResult(new NextTurn(player), true);
        }
        for (Player p : player.getGame().getPlayers()) {
            p.notifyMainPhase(30);
        }
        return new ResolveResult(this, false);
    }

    @Override
    public String toString() {
        return player + "的出牌阶段";
    }
}
