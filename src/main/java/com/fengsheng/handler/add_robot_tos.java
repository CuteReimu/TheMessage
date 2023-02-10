package com.fengsheng.handler;

import com.fengsheng.*;
import com.fengsheng.protos.Errcode;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class add_robot_tos extends AbstractProtoHandler<Fengsheng.add_robot_tos> {
    private static final Logger log = Logger.getLogger(add_robot_tos.class);

    @Override
    protected void handle0(HumanPlayer player, Fengsheng.add_robot_tos pb) {
        synchronized (Game.class) {
            if (player.getGame().isStarted()) {
                log.error("room is already full");
                return;
            }
            if (player.getGame().getPlayers().length > 5) {
                player.send(Errcode.error_code_toc.newBuilder().setCode(Errcode.error_code.robot_not_allowed).build());
                return;
            }
            Statistics.PlayerGameCount count = Statistics.getInstance().getPlayerGameCount(player.getPlayerName());
            if (count == null || count.winCount() <= 0) {
                long now = System.currentTimeMillis();
                long startTrialTime = Statistics.getInstance().getTrialStartTime(player.getDevice());
                if (startTrialTime != 0 && now - 5 * 24 * 3600 * 1000 >= startTrialTime) {
                    player.send(Errcode.error_code_toc.newBuilder().setCode(Errcode.error_code.robot_not_allowed).build());
                    return;
                }
                Statistics.getInstance().setTrialStartTime(player.getDevice(), now);
            }
            Player robotPlayer = new RobotPlayer();
            robotPlayer.setPlayerName(Player.randPlayerName());
            robotPlayer.setGame(player.getGame());
            robotPlayer.getGame().onPlayerJoinRoom(robotPlayer, Statistics.getInstance().getTotalPlayerGameCount());
        }
    }
}
