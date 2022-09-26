package com.fengsheng.handler;

import com.fengsheng.Game;
import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.RobotPlayer;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class add_one_position_tos extends AbstractProtoHandler<Fengsheng.add_one_position_tos> {
    private static final Logger log = Logger.getLogger(add_one_position_tos.class);

    @Override
    protected void handle0(HumanPlayer player, Fengsheng.add_one_position_tos pb) {
        synchronized (Game.class) {
            if (player.getGame().isStarted()) {
                log.error("game already started");
                return;
            }
            Player[] players = player.getGame().getPlayers();
            if (players.length >= 9) return;
            Player[] newPlayers = new Player[players.length + 1];
            System.arraycopy(players, 0, newPlayers, 0, players.length);
            player.getGame().setPlayers(newPlayers);
            for (Player p : players) {
                if (p instanceof HumanPlayer)
                    ((HumanPlayer) p).send(Fengsheng.add_one_position_toc.getDefaultInstance());
            }
            if (newPlayers.length > 5) {
                for (int i = 0; i < newPlayers.length; i++) {
                    if (newPlayers[i] instanceof RobotPlayer robotPlayer) {
                        log.info(robotPlayer.getPlayerName() + "离开了房间");
                        var reply = Fengsheng.leave_room_toc.newBuilder().setPosition(robotPlayer.location()).build();
                        newPlayers[i] = null;
                        for (Player p : players) {
                            if (p instanceof HumanPlayer) {
                                ((HumanPlayer) p).send(reply);
                            }
                        }
                    }
                }
            }
        }
    }
}
