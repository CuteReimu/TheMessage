package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.card.Card;

/**
 * 死亡给三张牌后，判断是否有下一个人需要结算死亡给三张牌
 */
public record AfterDieGiveCard(WaitForDieGiveCard dieGiveCard) implements Fsm {
    @Override
    public ResolveResult resolve() {
        Player player = dieGiveCard.diedQueue.get(dieGiveCard.diedIndex);
        Card[] cards = player.getCards().values().toArray(new Card[0]);
        player.getGame().playerDiscardCard(player, cards);
        for (Player p : player.getGame().getPlayers()) {
            p.notifyDie(player.location(), false);
        }
        dieGiveCard.diedIndex++;
        return new ResolveResult(dieGiveCard, true);
    }
}
