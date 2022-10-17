package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.card.Card;

/**
 * 争夺阶段空闲时点
 */
public class FightPhaseIdle implements Fsm {
    /**
     * 谁的回合
     */
    public Player whoseTurn;
    /**
     * 情报牌
     */
    public Card messageCard;
    /**
     * 情报在谁面前
     */
    public Player inFrontOfWhom;
    /**
     * 正在询问谁
     */
    public Player whoseFightTurn;
    /**
     * 情报是否面朝上
     */
    public boolean isMessageCardFaceUp;

    public FightPhaseIdle(Player whoseTurn, Card messageCard, Player inFrontOfWhom, Player whoseFightTurn, boolean isMessageCardFaceUp) {
        this.whoseTurn = whoseTurn;
        this.messageCard = messageCard;
        this.inFrontOfWhom = inFrontOfWhom;
        this.whoseFightTurn = whoseFightTurn;
        this.isMessageCardFaceUp = isMessageCardFaceUp;
    }

    @Override
    public ResolveResult resolve() {
        if (!whoseFightTurn.isAlive() || whoseFightTurn.getGame().getJinBiPlayer() == whoseFightTurn)
            return new ResolveResult(new FightPhaseNext(this), true);
        for (Player p : whoseTurn.getGame().getPlayers())
            p.notifyFightPhase(15);
        return null;
    }

    @Override
    public String toString() {
        return whoseTurn + "的回合的争夺阶段，情报在" + inFrontOfWhom + "面前，正在询问" + whoseFightTurn;
    }
}
