package com.fengsheng.card;

import com.fengsheng.Game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
    private int nextId;
    private final Game game;
    private final List<Card> cards = new ArrayList<>();
    private final List<Card> discardPile = new ArrayList<>();

    public Deck(Game game) {
        this.game = game;
        cards.addAll(defaultDeck);
        this.nextId = cards.size() - 1;
        shuffle();
    }

    /**
     * 洗牌
     */
    public void shuffle() {
        cards.addAll(discardPile);
        discardPile.clear();
        Collections.shuffle(cards);
        notifyDeckCount(true);
    }

    /**
     * 摸牌
     */
    public Card[] draw(int n) {
        if (n > cards.size()) {
            shuffle();
        }
        if (n > cards.size()) {
            n = cards.size();
        }
        List<Card> subList = cards.subList(cards.size() - n, cards.size());
        Card[] result = subList.toArray(new Card[0]);
        subList.clear();
        if (cards.size() == 0) {
            shuffle();
        }
        return result;
    }

    /**
     * 弃牌
     */
    public void discard(Card... cards) {
        discardPile.addAll(List.of(cards));
    }

    /**
     * 获取牌堆剩余卡牌数量
     */
    public int getDeckCount() {
        return cards.size();
    }

    /**
     * 通知客户端牌堆剩余卡牌数量
     *
     * @param shuffled 是否洗牌
     */
    private void notifyDeckCount(boolean shuffled) {

    }

    /**
     * 如果需要新增一张卡牌，调用这个函数可以新卡牌的卡牌ID
     */
    public int getNextId() {
        return ++nextId;
    }

    private static final List<Card> defaultDeck = new ArrayList<>();
}
