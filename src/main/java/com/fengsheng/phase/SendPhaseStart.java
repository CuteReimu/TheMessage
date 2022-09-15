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
                for (Player p : game.getPlayers())
                    p.notifyDying(player.location(), true);
                Player alivePlayer = getOnlyOneAlivePlayer(game.getPlayers());
                if (alivePlayer != null) {
                    CheckKillerWin.onlyOneAliveWinner(game, alivePlayer);
                    return new ResolveResult(null, false);
                }
                for (Player p : game.getPlayers())
                    p.notifyDie(player.location());
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

    private static Player getOnlyOneAlivePlayer(Player[] players) {
        Player alivePlayer = null;
        for (Player p : players) {
            if (p.isAlive()) {
                if (alivePlayer == null)
                    alivePlayer = p;
                else
                    return null;
            }
        }
        return alivePlayer;
    }

    @Override
    public String toString() {
        return player + "的情报传递阶段开始时";
    }
}
