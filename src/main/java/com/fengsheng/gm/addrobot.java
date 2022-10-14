package com.fengsheng.gm;

import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.RobotPlayer;
import com.fengsheng.Statistics;

import java.util.Map;
import java.util.function.Function;

public class addrobot implements Function<Map<String, String>, String> {
    @Override
    public String apply(Map<String, String> form) {
        try {
            int count = form.containsKey("count") ? Integer.parseInt(form.get("count")) : 0;
            count = count > 0 ? count : 99;
            synchronized (Game.class) {
                Game g = Game.getInstance();
                for (int i = 0; i < count; i++) {
                    if (g.isStarted())
                        break;
                    Player robotPlayer = new RobotPlayer();
                    robotPlayer.setPlayerName(Player.randPlayerName());
                    robotPlayer.setGame(g);
                    robotPlayer.getGame().onPlayerJoinRoom(robotPlayer, Statistics.getInstance().getTotalPlayerGameCount());
                }
            }
            return "{\"msg\": \"success\"}";
        } catch (NumberFormatException | NullPointerException e) {
            return "{\"error\": \"invalid arguments\"}";
        }
    }
}
