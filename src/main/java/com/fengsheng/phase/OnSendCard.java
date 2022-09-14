package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;
import org.apache.log4j.Logger;

import java.util.Arrays;

/**
 * 选择了要传递哪张情报时
 *
 * @param whoseTurn     谁的回合
 * @param messageCard   传递的情报牌
 * @param dir           传递方向
 * @param targetPlayer  传递的目标角色
 * @param lockedPlayers 被锁定的玩家
 */
public record OnSendCard(Player whoseTurn, Card messageCard, Common.direction dir, Player targetPlayer,
                         Player[] lockedPlayers) implements Fsm {
    private static final Logger log = Logger.getLogger(OnSendCard.class);

    @Override
    public ResolveResult resolve() {
        String s = whoseTurn + "传出了" + messageCard + "，方向是" + dir + "，传给了" + targetPlayer;
        if (lockedPlayers.length > 0) s += "，并锁定了" + Arrays.toString(lockedPlayers);
        log.info(s);
        whoseTurn.deleteCard(messageCard.getId());
        for (Player p : whoseTurn.getGame().getPlayers())
            p.notifySendMessageCard(whoseTurn, targetPlayer, lockedPlayers, messageCard, dir);
        log.info("情报到达" + targetPlayer + "面前");
        return new ResolveResult(new SendPhaseIdle(whoseTurn, messageCard, dir, targetPlayer, lockedPlayers, false), true);
    }
}
