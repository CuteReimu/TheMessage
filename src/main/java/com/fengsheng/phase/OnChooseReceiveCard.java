package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.card.Card;
import org.apache.log4j.Logger;

/**
 * 选择接收情报时
 *
 * @param whoseTurn           谁的回合（也就是情报传出者）
 * @param messageCard         情报牌
 * @param inFrontOfWhom       情报在谁面前
 * @param isMessageCardFaceUp 情报是否面朝上
 */
public record OnChooseReceiveCard(Player whoseTurn, Card messageCard, Player inFrontOfWhom,
                                  boolean isMessageCardFaceUp) implements Fsm {
    private static final Logger log = Logger.getLogger(OnChooseReceiveCard.class);

    @Override
    public ResolveResult resolve() {
        log.info(inFrontOfWhom + "选择接收情报");
        for (Player p : whoseTurn.getGame().getPlayers())
            p.notifyChooseReceiveCard();
        return new ResolveResult(new FightPhaseIdle(whoseTurn, messageCard, inFrontOfWhom, inFrontOfWhom, isMessageCardFaceUp), true);
    }
}
