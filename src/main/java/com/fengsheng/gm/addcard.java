package com.fengsheng.gm;

import com.fengsheng.Game;
import com.fengsheng.GameExecutor;
import com.fengsheng.Player;
import com.fengsheng.card.*;
import com.fengsheng.protos.Common;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class addcard implements Function<Map<String, String>, String> {
    private static final Logger log = Logger.getLogger(addcard.class);

    @Override
    public String apply(Map<String, String> form) {
        try {
            final int playerId = form.containsKey("player") ? Integer.parseInt(form.get("player")) : 0;
            int cardTypeNum = Integer.parseInt(form.get("card"));
            final Common.card_type cardType = Common.card_type.forNumber(cardTypeNum);
            if (cardType == null || cardType == Common.card_type.UNRECOGNIZED)
                return "{\"error\": \"invalid arguments\"}";
            String count = form.get("count");
            final int finalCount = count != null ? Math.max(1, Math.min(Integer.parseInt(count), 99)) : 1;
            List<Card> availableCards = new ArrayList<>();
            for (Card c : Deck.DefaultDeck)
                if (c.getType() == cardType) availableCards.add(c);
            for (Game g : Game.GameCache.values()) {
                GameExecutor.post(g, () -> {
                    if (playerId < g.getPlayers().length && playerId >= 0 && g.getPlayers()[playerId].isAlive()) {
                        List<Card> cardList = new ArrayList<>();
                        for (int i = 0; i < finalCount; i++) {
                            AbstractCard c = (AbstractCard) availableCards.get(ThreadLocalRandom.current().nextInt(availableCards.size()));
                            cardList.add(switch (cardType) {
                                case Cheng_Qing -> new ChengQing(g.getDeck().getNextId(), c);
                                case Shi_Tan -> new ShiTan(g.getDeck().getNextId(), (ShiTan) c);
                                case Wei_Bi -> new WeiBi(g.getDeck().getNextId(), c);
                                case Li_You -> new LiYou(g.getDeck().getNextId(), c);
                                case Ping_Heng -> new PingHeng(g.getDeck().getNextId(), c);
                                case Po_Yi -> new PoYi(g.getDeck().getNextId(), c);
                                case Jie_Huo -> new JieHuo(g.getDeck().getNextId(), c);
                                case Diao_Bao -> new DiaoBao(g.getDeck().getNextId(), c);
                                case Wu_Dao -> new WuDao(g.getDeck().getNextId(), c);
                                default -> throw new IllegalStateException("Unexpected value: " + cardType);
                            });
                        }
                        Player p = g.getPlayers()[playerId];
                        Card[] cards = cardList.toArray(new Card[0]);
                        p.addCard(cards);
                        log.info("由于GM命令，" + p + "摸了" + Arrays.toString(cards) + "，现在有" + p.getCards().size() + "张手牌");
                        for (Player player : g.getPlayers()) {
                            if (player.location() == playerId)
                                player.notifyAddHandCard(playerId, 0, cards);
                            else
                                player.notifyAddHandCard(playerId, cards.length);
                        }
                    }
                });
            }
            return "{\"msg\": \"success\"}";
        } catch (NumberFormatException | NullPointerException e) {
            return "{\"error\": \"invalid arguments\"}";
        }
    }
}
