package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;

public class SendPhaseIdle implements Fsm {
    /**
     * 谁的回合
     */
    public Player whoseTurn;
    /**
     * 传递的情报牌
     */
    public Card messageCard;
    /**
     * 传递方向
     */
    public Common.direction dir;
    /**
     * 情报在谁面前
     */
    public Player inFrontOfWhom;
    /**
     * 被锁定的玩家
     */
    public Player[] lockedPlayers;
    /**
     * 情报是否面朝上
     */
    public boolean isMessageCardFaceUp;

    public SendPhaseIdle(Player whoseTurn, Card messageCard, Common.direction dir, Player inFrontOfWhom, Player[] lockedPlayers, boolean isMessageCardFaceUp) {
        this.whoseTurn = whoseTurn;
        this.messageCard = messageCard;
        this.dir = dir;
        this.inFrontOfWhom = inFrontOfWhom;
        this.lockedPlayers = lockedPlayers;
        this.isMessageCardFaceUp = isMessageCardFaceUp;
    }

    @Override
    public ResolveResult resolve() {
        for (Player p : whoseTurn.getGame().getPlayers()) {
            p.notifySendPhase(15);
        }
        return null;
    }

    @Override
    public String toString() {
        return whoseTurn + "的回合的情报传递阶段，情报在" + inFrontOfWhom + "面前";
    }
}
