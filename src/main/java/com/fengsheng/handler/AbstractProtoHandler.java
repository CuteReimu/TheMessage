package com.fengsheng.handler;

import com.fengsheng.Game;
import com.fengsheng.GameExecutor;
import com.fengsheng.HumanPlayer;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

public abstract class AbstractProtoHandler<T extends GeneratedMessageV3> implements ProtoHandler {
    private static final Logger log = Logger.getLogger(AbstractProtoHandler.class);

    protected abstract void handle0(HumanPlayer r, T pb);

    @SuppressWarnings("unchecked")
    @Override
    public void handle(final HumanPlayer player, final GeneratedMessageV3 message) {
        // 因为player.setGame()只会join_room_tos调用，所以一定和这里的player.getGame()在同一线程，所以无需加锁
        Game game = player.getGame();
        if (game == null) {
            log.error("player didn't not join room, current msg: " + message.getDescriptorForType().getName());
        } else {
            GameExecutor.post(game, () -> {
                player.clearTimeoutCount();
                handle0(player, (T) message);
            });
        }
    }
}
