package com.fengsheng.phase;

import com.fengsheng.Config;
import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import org.apache.log4j.Logger;

/**
 * 摸牌阶段
 */
public record DrawPhase(Player player) implements Fsm {
    private static final Logger log = Logger.getLogger(DrawPhase.class);

    @Override
    public ResolveResult resolve() {
        if (!player.isAlive()) {
            return new ResolveResult(new NextTurn(player), true);
        }
        log.info(player + "的回合开始了");
        for (Player p : player.getGame().getPlayers()) {
            p.notifyDrawPhase();
        }
        player.draw(Config.HandCardCountEachTurn);
        return new ResolveResult(new MainPhaseIdle(player), true);
    }

    @Override
    public String toString() {
        return player + "的摸牌阶段";
    }
}
