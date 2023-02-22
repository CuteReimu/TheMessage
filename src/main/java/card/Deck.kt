package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Fengsheng
import java.util.*

class Deck(private val game: Game, totalPlayerCount: Int) {
    private var nextId: Int
    private var cards: MutableList<Card?> = ArrayList()
    private var discardPile: MutableList<Card?> = ArrayList()

    /**
     * 洗牌
     */
    fun shuffle() {
        Collections.shuffle(discardPile)
        discardPile.addAll(cards)
        cards = discardPile
        discardPile = ArrayList()
        notifyDeckCount(true)
    }

    /**
     * 摸牌
     */
    fun draw(n: Int): Array<Card?> {
        var n = n
        if (n > cards.size) {
            shuffle()
        }
        if (n > cards.size) {
            n = cards.size
        }
        val subList = cards.subList(cards.size - n, cards.size)
        val result = subList.toTypedArray()
        subList.clear()
        notifyDeckCount(false)
        if (cards.isEmpty()) {
            shuffle()
        }
        return result
    }

    /**
     * 查看牌堆顶的n张牌
     *
     * @return 返回查看的牌，它是牌堆的一个 `sublist` ，其中 `cards.get(0)` 是观看的最后一张牌，
     * `cards.get(cards.length() - 1)` 是牌堆顶第一张牌。
     */
    fun peek(n: Int): List<Card?> {
        var n = n
        if (n > cards.size) {
            shuffle()
        }
        if (n > cards.size) {
            n = cards.size
        }
        return cards.subList(cards.size - n, cards.size)
    }

    /**
     * 弃牌
     */
    fun discard(vararg cards: Card?) {
        discardPile.addAll(java.util.List.of(*cards))
    }

    val deckCount: Int
        /**
         * 获取牌堆剩余卡牌数量
         */
        get() = cards.size

    /**
     * 通知客户端牌堆剩余卡牌数量
     *
     * @param shuffled 是否洗牌
     */
    private fun notifyDeckCount(shuffled: Boolean) {
        for (player in game.players) {
            (player as? HumanPlayer)?.send(
                Fengsheng.sync_deck_num_toc.newBuilder().setNum(cards.size).setShuffled(shuffled).build()
            )
        }
    }

    /**
     * 如果需要新增一张卡牌，调用这个函数可以新卡牌的卡牌ID
     */
    fun getNextId(): Int {
        return ++nextId
    }

    init {
        cards.addAll(DefaultDeck)
        if (totalPlayerCount < 4) {
            cards.subList(0, 18).clear()
        } else if (totalPlayerCount == 4 || totalPlayerCount == 5) {
            cards.removeAt(16)
            cards.removeAt(13)
            cards.removeAt(11)
            cards.removeAt(8)
            cards.removeAt(3)
            cards.removeAt(0)
        }
        nextId = DefaultDeck.size
        Collections.shuffle(cards)
    }

    companion object {
        val DefaultDeck = java.util.List.of(
            ShiTan(1, colors(color.Black), direction.Right, false, colors(color.Black)),
            ShiTan(2, colors(color.Black), direction.Right, false, colors(color.Blue)),
            ShiTan(3, colors(color.Black), direction.Right, false, colors(color.Red, color.Black)),
            ShiTan(4, colors(color.Black), direction.Left, false, colors(color.Red, color.Blue)),
            ShiTan(5, colors(color.Black), direction.Left, false, colors(color.Blue, color.Black)),
            ShiTan(6, colors(color.Black), direction.Left, false, colors(color.Red)),
            ShiTan(7, colors(color.Red), direction.Right, false, colors(color.Black)),
            ShiTan(8, colors(color.Red), direction.Right, false, colors(color.Blue)),
            ShiTan(9, colors(color.Red), direction.Right, false, colors(color.Red, color.Black)),
            ShiTan(10, colors(color.Red), direction.Left, false, colors(color.Red, color.Blue)),
            ShiTan(11, colors(color.Red), direction.Left, false, colors(color.Blue, color.Black)),
            ShiTan(12, colors(color.Red), direction.Left, false, colors(color.Red)),
            ShiTan(13, colors(color.Blue), direction.Right, false, colors(color.Black)),
            ShiTan(14, colors(color.Blue), direction.Right, false, colors(color.Blue)),
            ShiTan(15, colors(color.Blue), direction.Right, false, colors(color.Red, color.Black)),
            ShiTan(16, colors(color.Blue), direction.Left, false, colors(color.Red, color.Blue)),
            ShiTan(17, colors(color.Blue), direction.Left, false, colors(color.Blue, color.Black)),
            ShiTan(18, colors(color.Blue), direction.Left, false, colors(color.Red)),
            PingHeng(19, colors(color.Black), direction.Left, true),
            PingHeng(20, colors(color.Black), direction.Right, true),
            PingHeng(21, colors(color.Blue), direction.Left, true),
            PingHeng(22, colors(color.Red), direction.Right, true),
            PingHeng(23, colors(color.Red, color.Black), direction.Up, false),
            PingHeng(24, colors(color.Blue, color.Black), direction.Up, false),
            PingHeng(25, colors(color.Red, color.Black), direction.Left, false),
            PingHeng(26, colors(color.Blue, color.Black), direction.Right, false),
            WeiBi(27, colors(color.Red), direction.Left, false),
            WeiBi(28, colors(color.Red), direction.Left, false),
            WeiBi(29, colors(color.Red), direction.Left, false),
            WeiBi(30, colors(color.Red), direction.Right, false),
            WeiBi(31, colors(color.Blue), direction.Left, false),
            WeiBi(32, colors(color.Blue), direction.Right, false),
            WeiBi(33, colors(color.Blue), direction.Right, false),
            WeiBi(34, colors(color.Blue), direction.Right, false),
            WeiBi(35, colors(color.Black), direction.Left, false),
            WeiBi(36, colors(color.Black), direction.Left, false),
            WeiBi(37, colors(color.Black), direction.Right, false),
            WeiBi(38, colors(color.Black), direction.Right, false),
            WeiBi(39, colors(color.Blue, color.Black), direction.Left, false),
            WeiBi(40, colors(color.Red, color.Black), direction.Right, false),
            LiYou(41, colors(color.Black), direction.Left, true),
            LiYou(42, colors(color.Black), direction.Right, true),
            LiYou(43, colors(color.Black), direction.Left, true),
            LiYou(44, colors(color.Black), direction.Right, true),
            LiYou(45, colors(color.Black), direction.Left, true),
            LiYou(46, colors(color.Black), direction.Right, true),
            LiYou(47, colors(color.Blue), direction.Left, true),
            LiYou(48, colors(color.Red), direction.Right, true),
            ChengQing(49, colors(color.Red), direction.Up, true),
            ChengQing(50, colors(color.Red), direction.Up, true),
            ChengQing(51, colors(color.Black), direction.Up, true),
            ChengQing(52, colors(color.Black), direction.Up, true),
            ChengQing(53, colors(color.Blue), direction.Up, true),
            ChengQing(54, colors(color.Blue), direction.Up, true),
            ChengQing(55, colors(color.Black), direction.Up, true),
            ChengQing(56, colors(color.Black), direction.Up, true),
            PoYi(57, colors(color.Red, color.Black), direction.Left, true),
            PoYi(58, colors(color.Blue, color.Black), direction.Left, true),
            PoYi(59, colors(color.Red), direction.Left, true),
            PoYi(60, colors(color.Blue), direction.Left, true),
            PoYi(61, colors(color.Black), direction.Left, true),
            PoYi(62, colors(color.Red, color.Black), direction.Right, true),
            PoYi(63, colors(color.Blue, color.Black), direction.Right, true),
            PoYi(64, colors(color.Red), direction.Right, true),
            PoYi(65, colors(color.Blue), direction.Right, true),
            PoYi(66, colors(color.Black), direction.Right, true),
            DiaoBao(67, colors(color.Red), direction.Up, false),
            DiaoBao(68, colors(color.Red), direction.Left, false),
            DiaoBao(69, colors(color.Red), direction.Right, false),
            DiaoBao(70, colors(color.Blue), direction.Up, false),
            DiaoBao(71, colors(color.Blue), direction.Left, false),
            DiaoBao(72, colors(color.Blue), direction.Right, false),
            DiaoBao(73, colors(color.Black), direction.Left, false),
            DiaoBao(74, colors(color.Black), direction.Right, false),
            DiaoBao(75, colors(color.Red, color.Black), direction.Up, false),
            DiaoBao(76, colors(color.Red, color.Black), direction.Right, false),
            DiaoBao(77, colors(color.Blue, color.Black), direction.Up, false),
            DiaoBao(78, colors(color.Blue, color.Black), direction.Left, false),
            JieHuo(79, colors(color.Red), direction.Up, false),
            JieHuo(80, colors(color.Red), direction.Up, false),
            JieHuo(81, colors(color.Red), direction.Up, true),
            JieHuo(82, colors(color.Blue), direction.Up, false),
            JieHuo(83, colors(color.Blue), direction.Up, false),
            JieHuo(84, colors(color.Blue), direction.Up, true),
            JieHuo(85, colors(color.Black), direction.Up, false),
            JieHuo(86, colors(color.Black), direction.Up, false),
            JieHuo(87, colors(color.Black), direction.Up, true),
            JieHuo(88, colors(color.Black), direction.Up, true),
            JieHuo(89, colors(color.Blue, color.Black), direction.Up, false),
            JieHuo(90, colors(color.Red, color.Black), direction.Up, false),
            WuDao(91, colors(color.Red), direction.Up, false),
            WuDao(92, colors(color.Red), direction.Left, false),
            WuDao(93, colors(color.Red), direction.Right, false),
            WuDao(94, colors(color.Blue), direction.Up, false),
            WuDao(95, colors(color.Blue), direction.Left, false),
            WuDao(96, colors(color.Blue), direction.Right, false),
            WuDao(97, colors(color.Black), direction.Left, false),
            WuDao(98, colors(color.Black), direction.Right, false),
            WuDao(99, colors(color.Blue, color.Black), direction.Left, false),
            WuDao(100, colors(color.Red, color.Black), direction.Right, false),
            FengYunBianHuan(101, colors(color.Black), direction.Up, false),
            FengYunBianHuan(102, colors(color.Black), direction.Up, false)
        )

        private fun colors(vararg colors: color): Array<color> {
            return colors
        }
    }
}