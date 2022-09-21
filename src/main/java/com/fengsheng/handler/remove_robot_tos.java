package com.fengsheng.handler;

import com.fengsheng.Game;
import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.RobotPlayer;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class remove_robot_tos extends AbstractProtoHandler<Fengsheng.remove_robot_tos> {
    private static final Logger log = Logger.getLogger(remove_robot_tos.class);

    @Override
    protected void handle0(HumanPlayer player, Fengsheng.remove_robot_tos pb) {
        Player[] players = player.getGame().getPlayers();
        RobotPlayer robotPlayer = null;
        synchronized (Game.class) {
            for (Player p : player.getGame().getPlayers()) {
                if (p instanceof RobotPlayer) {
                    robotPlayer = (RobotPlayer) p;
                    players[p.location()] = null;
                    break;
                }
            }
        }
        if (robotPlayer != null) {
            log.info(robotPlayer.getPlayerName() + "离开了房间");
            var reply = Fengsheng.leave_room_toc.newBuilder().setPosition(robotPlayer.location()).build();
            for (Player p : players) {
                if (p instanceof HumanPlayer) {
                    ((HumanPlayer) p).send(reply);
                }
            }
        }
    }
}
