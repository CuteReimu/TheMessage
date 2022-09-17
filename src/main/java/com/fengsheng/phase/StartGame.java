package com.fengsheng.phase;

import com.fengsheng.*;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 游戏马上开始
 */
public record StartGame(Game game) implements Fsm {
    @Override
    public ResolveResult resolve() {
        Game.GameCache.put(game.getId(), game);
        Player[] players = game.getPlayers();
        final int whoseTurn = ThreadLocalRandom.current().nextInt(players.length);
        for (int i = 0; i < players.length; i++)
            players[(whoseTurn + i) % players.length].init();
        for (int i = 0; i < players.length; i++)
            players[(whoseTurn + i) % players.length].draw(Config.HandCardCountBegin);
        GameExecutor.post(game, () -> game.resolve(new DrawPhase(players[whoseTurn])), 1, TimeUnit.SECONDS);
        return null;
    }
}
