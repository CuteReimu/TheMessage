package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.card.Card;

/**
 * 接收情报时，传出者的技能
 */
public class ReceivePhaseSenderSkill implements Fsm {
    /**
     * 谁的回合
     */
    public Player whoseTurn;
    /**
     * 情报牌
     */
    public Card messageCard;
    /**
     * 接收第三张黑色情报牌的顺序（也就是后续结算死亡的顺序）
     */
    public final ReceiveOrder receiveOrder = new ReceiveOrder();
    /**
     * 情报在谁面前
     */
    public Player inFrontOfWhom;

    public ReceivePhaseSenderSkill(Player whoseTurn, Card messageCard, Player inFrontOfWhom) {
        this.whoseTurn = whoseTurn;
        this.messageCard = messageCard;
        this.inFrontOfWhom = inFrontOfWhom;
    }

    @Override
    public ResolveResult resolve() {
        ResolveResult result = whoseTurn.getGame().dealListeningSkill();
        return result != null ? result : new ResolveResult(new ReceivePhaseReceiverSkill(whoseTurn, messageCard, receiveOrder, inFrontOfWhom), true);
    }

    @Override
    public String toString() {
        return whoseTurn + "的回合，" + inFrontOfWhom + "成功接收情报";
    }
}
