package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import org.apache.log4j.Logger;

/**
 * 情报传递阶段开始时，选择传递一张情报
 */
public record SendPhaseStart(Player player) implements Fsm {
    private static final Logger log = Logger.getLogger(SendPhaseStart.class);

    @Override
    public ResolveResult resolve() {
        Game game = player.getGame();
        if (player.isAlive()) {
            if (player.getCards().isEmpty()) {
                log.info(player + "没有情报可传，输掉了游戏");
                game.getDeck().discard(player.deleteAllMessageCards());
                player.setLose(true);
                player.setAlive(false);
                for (Player p : game.getPlayers()) {
                    p.notifyDying(player.location(), true);
                    p.notifyDie(player.location());
                }
            }
        }
        if (!player.isAlive()) {
            return new ResolveResult(new NextTurn(player), true);
        }
        for (Player p : game.getPlayers()) {
            p.notifySendPhaseStart(20);
        }
        return null;
    }

    @Override
    public String toString() {
        return player + "的情报传递阶段开始时";
    }
}
