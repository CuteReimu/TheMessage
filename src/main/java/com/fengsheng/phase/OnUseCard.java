package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;

/**
 * 使用卡牌时，成为卡牌目标时
 */
public class OnUseCard implements Fsm {
    /**
     * 谁的回合
     */
    public Player whoseTurn;
    /**
     * 目标角色
     */
    public Player targetPlayer;
    /**
     * 出牌的人
     */
    public Player player;
    /**
     * 出的牌，有可能没出牌
     */
    public Card card;
    /**
     * 出的牌的类型
     */
    public Common.card_type cardType;
    /**
     * 遍历到了谁的技能
     */
    public Player askWhom;
    /**
     * 卡牌效果的结算函数
     */
    public Fsm resolveFunc;

    public OnUseCard(Player whoseTurn, Player player, Player targetPlayer, Card card, Common.card_type cardType, Player askWhom, Fsm resolveFunc) {
        this.whoseTurn = whoseTurn;
        this.player = player;
        this.targetPlayer = targetPlayer;
        this.card = card;
        this.cardType = cardType;
        this.askWhom = askWhom;
        this.resolveFunc = resolveFunc;
    }

    @Override
    public ResolveResult resolve() {
        ResolveResult result = whoseTurn.getGame().dealListeningSkill();
        return result != null ? result : new ResolveResult(new OnUseCardNext(this), true);
    }

    @Override
    public String toString() {
        return player + "使用" + card + "时";
    }

    private record OnUseCardNext(OnUseCard onUseCard) implements Fsm {
        @Override
        public ResolveResult resolve() {
            int askWhom = onUseCard.askWhom.location();
            Player[] players = onUseCard.askWhom.getGame().getPlayers();
            while (true) {
                askWhom = (askWhom + 1) % players.length;
                if (askWhom == onUseCard.player.location()) {
                    return onUseCard.resolveFunc.resolve();
                }
                if (players[askWhom].isAlive()) {
                    onUseCard.askWhom = players[askWhom];
                    return new ResolveResult(onUseCard, true);
                }
            }
        }
    }
}
