package com.fengsheng.card;

import com.fengsheng.Game;
import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
    private int nextId;
    private final Game game;
    private final List<Card> cards = new ArrayList<>();
    private final List<Card> discardPile = new ArrayList<>();

    public Deck(Game game, boolean removeShiTan) {
        this.game = game;
        cards.addAll(DefaultDeck);
        if (removeShiTan)
            cards.subList(0, 18).clear();
        this.nextId = DefaultDeck.size();
        Collections.shuffle(cards);
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
        notifyDeckCount(false);
        if (cards.isEmpty()) {
            shuffle();
        }
        return result;
    }

    /**
     * 查看牌堆顶的n张牌
     *
     * @return 返回查看的牌，它是牌堆的一个 {@code sublist} ，其中 {@code cards.get(0)} 是观看的最后一张牌，
     * {@code cards.get(cards.length() - 1)} 是牌堆顶第一张牌。
     */
    public List<Card> peek(int n) {
        if (n > cards.size()) {
            shuffle();
        }
        if (n > cards.size()) {
            n = cards.size();
        }
        return cards.subList(cards.size() - n, cards.size());
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
        for (Player player : game.getPlayers()) {
            if (player instanceof HumanPlayer p) {
                p.send(Fengsheng.sync_deck_num_toc.newBuilder().setNum(cards.size()).setShuffled(shuffled).build());
            }
        }
    }

    /**
     * 如果需要新增一张卡牌，调用这个函数可以新卡牌的卡牌ID
     */
    public int getNextId() {
        return ++nextId;
    }

    public static final List<Card> DefaultDeck = List.of(
            new ShiTan(1, colors(Common.color.Black), Common.direction.Right, false, colors(Common.color.Black)),
            new ShiTan(2, colors(Common.color.Black), Common.direction.Right, false, colors(Common.color.Blue)),
            new ShiTan(3, colors(Common.color.Black), Common.direction.Right, false, colors(Common.color.Red, Common.color.Black)),
            new ShiTan(4, colors(Common.color.Black), Common.direction.Left, false, colors(Common.color.Red, Common.color.Blue)),
            new ShiTan(5, colors(Common.color.Black), Common.direction.Left, false, colors(Common.color.Blue, Common.color.Black)),
            new ShiTan(6, colors(Common.color.Black), Common.direction.Left, false, colors(Common.color.Red)),
            new ShiTan(7, colors(Common.color.Red), Common.direction.Right, false, colors(Common.color.Black)),
            new ShiTan(8, colors(Common.color.Red), Common.direction.Right, false, colors(Common.color.Blue)),
            new ShiTan(9, colors(Common.color.Red), Common.direction.Right, false, colors(Common.color.Red, Common.color.Black)),
            new ShiTan(10, colors(Common.color.Red), Common.direction.Left, false, colors(Common.color.Red, Common.color.Blue)),
            new ShiTan(11, colors(Common.color.Red), Common.direction.Left, false, colors(Common.color.Blue, Common.color.Black)),
            new ShiTan(12, colors(Common.color.Red), Common.direction.Left, false, colors(Common.color.Red)),
            new ShiTan(13, colors(Common.color.Blue), Common.direction.Right, false, colors(Common.color.Black)),
            new ShiTan(14, colors(Common.color.Blue), Common.direction.Right, false, colors(Common.color.Blue)),
            new ShiTan(15, colors(Common.color.Blue), Common.direction.Right, false, colors(Common.color.Red, Common.color.Black)),
            new ShiTan(16, colors(Common.color.Blue), Common.direction.Left, false, colors(Common.color.Red, Common.color.Blue)),
            new ShiTan(17, colors(Common.color.Blue), Common.direction.Left, false, colors(Common.color.Blue, Common.color.Black)),
            new ShiTan(18, colors(Common.color.Blue), Common.direction.Left, false, colors(Common.color.Red)),
            new PingHeng(19, colors(Common.color.Black), Common.direction.Left, true),
            new PingHeng(20, colors(Common.color.Black), Common.direction.Right, true),
            new PingHeng(21, colors(Common.color.Blue), Common.direction.Left, true),
            new PingHeng(22, colors(Common.color.Red), Common.direction.Right, true),
            new PingHeng(23, colors(Common.color.Red, Common.color.Black), Common.direction.Up, false),
            new PingHeng(24, colors(Common.color.Blue, Common.color.Black), Common.direction.Up, false),
            new PingHeng(25, colors(Common.color.Red, Common.color.Black), Common.direction.Left, false),
            new PingHeng(26, colors(Common.color.Blue, Common.color.Black), Common.direction.Right, false),
            new WeiBi(27, colors(Common.color.Red), Common.direction.Left, false),
            new WeiBi(28, colors(Common.color.Red), Common.direction.Left, false),
            new WeiBi(29, colors(Common.color.Red), Common.direction.Left, false),
            new WeiBi(30, colors(Common.color.Red), Common.direction.Right, false),
            new WeiBi(31, colors(Common.color.Blue), Common.direction.Left, false),
            new WeiBi(32, colors(Common.color.Blue), Common.direction.Right, false),
            new WeiBi(33, colors(Common.color.Blue), Common.direction.Right, false),
            new WeiBi(34, colors(Common.color.Blue), Common.direction.Right, false),
            new WeiBi(35, colors(Common.color.Black), Common.direction.Left, false),
            new WeiBi(36, colors(Common.color.Black), Common.direction.Left, false),
            new WeiBi(37, colors(Common.color.Black), Common.direction.Right, false),
            new WeiBi(38, colors(Common.color.Black), Common.direction.Right, false),
            new WeiBi(39, colors(Common.color.Blue, Common.color.Black), Common.direction.Left, false),
            new WeiBi(40, colors(Common.color.Red, Common.color.Black), Common.direction.Right, false),
            new LiYou(41, colors(Common.color.Black), Common.direction.Left, true),
            new LiYou(42, colors(Common.color.Black), Common.direction.Right, true),
            new LiYou(43, colors(Common.color.Black), Common.direction.Left, true),
            new LiYou(44, colors(Common.color.Black), Common.direction.Right, true),
            new LiYou(45, colors(Common.color.Black), Common.direction.Left, true),
            new LiYou(46, colors(Common.color.Black), Common.direction.Right, true),
            new LiYou(47, colors(Common.color.Blue), Common.direction.Left, true),
            new LiYou(48, colors(Common.color.Red), Common.direction.Right, true),
            new ChengQing(49, colors(Common.color.Red), Common.direction.Up, true),
            new ChengQing(50, colors(Common.color.Red), Common.direction.Up, true),
            new ChengQing(51, colors(Common.color.Black), Common.direction.Up, true),
            new ChengQing(52, colors(Common.color.Black), Common.direction.Up, true),
            new ChengQing(53, colors(Common.color.Blue), Common.direction.Up, true),
            new ChengQing(54, colors(Common.color.Blue), Common.direction.Up, true),
            new ChengQing(55, colors(Common.color.Black), Common.direction.Up, true),
            new ChengQing(56, colors(Common.color.Black), Common.direction.Up, true),
            new PoYi(57, colors(Common.color.Red, Common.color.Black), Common.direction.Left, true),
            new PoYi(58, colors(Common.color.Blue, Common.color.Black), Common.direction.Left, true),
            new PoYi(59, colors(Common.color.Red), Common.direction.Left, true),
            new PoYi(60, colors(Common.color.Blue), Common.direction.Left, true),
            new PoYi(61, colors(Common.color.Black), Common.direction.Left, true),
            new PoYi(62, colors(Common.color.Red, Common.color.Black), Common.direction.Right, true),
            new PoYi(63, colors(Common.color.Blue, Common.color.Black), Common.direction.Right, true),
            new PoYi(64, colors(Common.color.Red), Common.direction.Right, true),
            new PoYi(65, colors(Common.color.Blue), Common.direction.Right, true),
            new PoYi(66, colors(Common.color.Black), Common.direction.Right, true),
            new DiaoBao(67, colors(Common.color.Red), Common.direction.Up, false),
            new DiaoBao(68, colors(Common.color.Red), Common.direction.Left, false),
            new DiaoBao(69, colors(Common.color.Red), Common.direction.Right, false),
            new DiaoBao(70, colors(Common.color.Blue), Common.direction.Up, false),
            new DiaoBao(71, colors(Common.color.Blue), Common.direction.Left, false),
            new DiaoBao(72, colors(Common.color.Blue), Common.direction.Right, false),
            new DiaoBao(73, colors(Common.color.Black), Common.direction.Left, false),
            new DiaoBao(74, colors(Common.color.Black), Common.direction.Right, false),
            new DiaoBao(75, colors(Common.color.Red, Common.color.Black), Common.direction.Up, false),
            new DiaoBao(76, colors(Common.color.Red, Common.color.Black), Common.direction.Right, false),
            new DiaoBao(77, colors(Common.color.Blue, Common.color.Black), Common.direction.Up, false),
            new DiaoBao(78, colors(Common.color.Blue, Common.color.Black), Common.direction.Left, false),
            new JieHuo(79, colors(Common.color.Red), Common.direction.Up, false),
            new JieHuo(80, colors(Common.color.Red), Common.direction.Up, false),
            new JieHuo(81, colors(Common.color.Red), Common.direction.Up, true),
            new JieHuo(82, colors(Common.color.Blue), Common.direction.Up, false),
            new JieHuo(83, colors(Common.color.Blue), Common.direction.Up, false),
            new JieHuo(84, colors(Common.color.Blue), Common.direction.Up, true),
            new JieHuo(85, colors(Common.color.Black), Common.direction.Up, false),
            new JieHuo(86, colors(Common.color.Black), Common.direction.Up, false),
            new JieHuo(87, colors(Common.color.Black), Common.direction.Up, true),
            new JieHuo(88, colors(Common.color.Black), Common.direction.Up, true),
            new JieHuo(89, colors(Common.color.Blue, Common.color.Black), Common.direction.Up, false),
            new JieHuo(90, colors(Common.color.Red, Common.color.Black), Common.direction.Up, false),
            new WuDao(91, colors(Common.color.Red), Common.direction.Up, false),
            new WuDao(92, colors(Common.color.Red), Common.direction.Left, false),
            new WuDao(93, colors(Common.color.Red), Common.direction.Right, false),
            new WuDao(94, colors(Common.color.Blue), Common.direction.Up, false),
            new WuDao(95, colors(Common.color.Blue), Common.direction.Left, false),
            new WuDao(96, colors(Common.color.Blue), Common.direction.Right, false),
            new WuDao(97, colors(Common.color.Black), Common.direction.Left, false),
            new WuDao(98, colors(Common.color.Black), Common.direction.Right, false),
            new WuDao(99, colors(Common.color.Blue, Common.color.Black), Common.direction.Left, false),
            new WuDao(100, colors(Common.color.Red, Common.color.Black), Common.direction.Right, false)
    );

    private static Common.color[] colors(Common.color... colors) {
        return colors;
    }
}
