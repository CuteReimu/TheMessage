package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.card.Card;

/**
 * 接收情报时，接收者的技能
 *
 * @param whoseTurn     谁的回合
 * @param messageCard   情报牌
 * @param receiveOrder  接收情报牌的顺序（也就是后续结算死亡的顺序）
 * @param inFrontOfWhom 情报在谁面前
 */
public record ReceivePhaseReceiverSkill(Player whoseTurn, Card messageCard, ReceiveOrder receiveOrder,
                                        Player inFrontOfWhom) implements Fsm {
    @Override
    public ResolveResult resolve() {
        ResolveResult result = inFrontOfWhom.getGame().dealListeningSkill();
        return result != null ? result : new ResolveResult(new CheckWin(whoseTurn, receiveOrder, new NextTurn(whoseTurn)), true);
    }

    @Override
    public String toString() {
        return whoseTurn + "的回合，" + inFrontOfWhom + "成功接收情报";
    }
}
