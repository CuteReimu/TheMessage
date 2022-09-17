package com.fengsheng.handler;

import com.fengsheng.Game;
import com.fengsheng.GameExecutor;
import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class remove_one_position_tos extends AbstractProtoHandler<Fengsheng.remove_one_position_tos> {
    private static final Logger log = Logger.getLogger(remove_one_position_tos.class);

    @Override
    protected void handle0(HumanPlayer player, Fengsheng.remove_one_position_tos pb) {
        synchronized (Game.class) {
            if (player.getGame().isStarted()) {
                log.error("game already started");
                return;
            }
            Player[] oldPlayers = player.getGame().getPlayers();
            if (oldPlayers.length <= 3) return;
            int i = oldPlayers.length - 1;
            for (; i >= 0; i--)
                if (oldPlayers[i] == null) break;
            Player[] players = new Player[oldPlayers.length - 1];
            System.arraycopy(oldPlayers, 0, players, 0, i);
            System.arraycopy(oldPlayers, i + 1, players, i, oldPlayers.length - i - 1);
            player.getGame().setPlayers(players);
            for (Player p : players) {
                if (p instanceof HumanPlayer)
                    ((HumanPlayer) p).send(Fengsheng.remove_one_position_toc.newBuilder().setPosition(i).build());
            }
            for (Player p : players)
                if (p == null) return;
            log.info("已满" + players.length + "个人，游戏开始。。。");
            player.getGame().setStarted(true);
            GameExecutor.post(player.getGame(), player.getGame()::start);
            Game.newInstance();
        }
    }
}
