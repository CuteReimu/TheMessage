package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.card.Card;

import java.util.List;

/**
 * 接收情报时，传出者的技能
 *
 * @param whoseTurn     谁的回合
 * @param messageCard   情报牌
 * @param receiveOrder  接收情报牌的顺序（也就是后续结算死亡的顺序）
 * @param inFrontOfWhom 情报在谁面前
 */
public record ReceivePhaseSenderSkill(Player whoseTurn, Card messageCard, List<Player> receiveOrder,
                                      Player inFrontOfWhom) implements Fsm {
    @Override
    public ResolveResult resolve() {
        // TODO need complete this function
        return null;
    }
}
