package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Fengsheng

class Deck(private val game: Game) {
    private var nextId = 0
    private var cards = ArrayList<Card>()
    private var discardPile = ArrayList<Card>()

    /**
     * 洗牌
     */
    fun shuffle() {
        discardPile.shuffle()
        discardPile.addAll(cards)
        cards = discardPile
        discardPile = ArrayList()
        notifyDeckCount(true)
    }

    /**
     * 摸牌
     */
    fun draw(n: Int): Array<Card> {
        if (n > cards.size) shuffle()
        val from = if (n > cards.size) 0 else cards.size - n
        val subList = cards.subList(from, cards.size)
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
    fun peek(n: Int): MutableList<Card> {
        if (n > cards.size) shuffle()
        val from = if (n > cards.size) 0 else cards.size - n
        return cards.subList(from, cards.size)
    }

    /**
     * 弃牌
     */
    fun discard(vararg cards: Card) {
        discardPile.addAll(cards)
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

    fun init(totalPlayerCount: Int) {
        cards.clear()
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
        cards.shuffle()
    }

    companion object {
        val DefaultDeck = listOf(
            ShiTan(1, listOf(color.Black), direction.Right, false, listOf(color.Black)),
            ShiTan(2, listOf(color.Black), direction.Right, false, listOf(color.Blue)),
            ShiTan(3, listOf(color.Black), direction.Right, false, listOf(color.Red, color.Black)),
            ShiTan(4, listOf(color.Black), direction.Left, false, listOf(color.Red, color.Blue)),
            ShiTan(5, listOf(color.Black), direction.Left, false, listOf(color.Blue, color.Black)),
            ShiTan(6, listOf(color.Black), direction.Left, false, listOf(color.Red)),
            ShiTan(7, listOf(color.Red), direction.Right, false, listOf(color.Black)),
            ShiTan(8, listOf(color.Red), direction.Right, false, listOf(color.Blue)),
            ShiTan(9, listOf(color.Red), direction.Right, false, listOf(color.Red, color.Black)),
            ShiTan(10, listOf(color.Red), direction.Left, false, listOf(color.Red, color.Blue)),
            ShiTan(11, listOf(color.Red), direction.Left, false, listOf(color.Blue, color.Black)),
            ShiTan(12, listOf(color.Red), direction.Left, false, listOf(color.Red)),
            ShiTan(13, listOf(color.Blue), direction.Right, false, listOf(color.Black)),
            ShiTan(14, listOf(color.Blue), direction.Right, false, listOf(color.Blue)),
            ShiTan(15, listOf(color.Blue), direction.Right, false, listOf(color.Red, color.Black)),
            ShiTan(16, listOf(color.Blue), direction.Left, false, listOf(color.Red, color.Blue)),
            ShiTan(17, listOf(color.Blue), direction.Left, false, listOf(color.Blue, color.Black)),
            ShiTan(18, listOf(color.Blue), direction.Left, false, listOf(color.Red)),
            PingHeng(19, listOf(color.Black), direction.Left, true),
            PingHeng(20, listOf(color.Black), direction.Right, true),
            PingHeng(21, listOf(color.Blue), direction.Left, true),
            PingHeng(22, listOf(color.Red), direction.Right, true),
            PingHeng(23, listOf(color.Red, color.Black), direction.Up, false),
            PingHeng(24, listOf(color.Blue, color.Black), direction.Up, false),
            PingHeng(25, listOf(color.Red, color.Black), direction.Left, false),
            PingHeng(26, listOf(color.Blue, color.Black), direction.Right, false),
            WeiBi(27, listOf(color.Red), direction.Left, false),
            WeiBi(28, listOf(color.Red), direction.Left, false),
            WeiBi(29, listOf(color.Red), direction.Left, false),
            WeiBi(30, listOf(color.Red), direction.Right, false),
            WeiBi(31, listOf(color.Blue), direction.Left, false),
            WeiBi(32, listOf(color.Blue), direction.Right, false),
            WeiBi(33, listOf(color.Blue), direction.Right, false),
            WeiBi(34, listOf(color.Blue), direction.Right, false),
            WeiBi(35, listOf(color.Black), direction.Left, false),
            WeiBi(36, listOf(color.Black), direction.Left, false),
            WeiBi(37, listOf(color.Black), direction.Right, false),
            WeiBi(38, listOf(color.Black), direction.Right, false),
            WeiBi(39, listOf(color.Blue, color.Black), direction.Left, false),
            WeiBi(40, listOf(color.Red, color.Black), direction.Right, false),
            LiYou(41, listOf(color.Black), direction.Left, true),
            LiYou(42, listOf(color.Black), direction.Right, true),
            LiYou(43, listOf(color.Black), direction.Left, true),
            LiYou(44, listOf(color.Black), direction.Right, true),
            LiYou(45, listOf(color.Black), direction.Left, true),
            LiYou(46, listOf(color.Black), direction.Right, true),
            LiYou(47, listOf(color.Blue), direction.Left, true),
            LiYou(48, listOf(color.Red), direction.Right, true),
            ChengQing(49, listOf(color.Red), direction.Up, true),
            ChengQing(50, listOf(color.Red), direction.Up, true),
            ChengQing(51, listOf(color.Black), direction.Up, true),
            ChengQing(52, listOf(color.Black), direction.Up, true),
            ChengQing(53, listOf(color.Blue), direction.Up, true),
            ChengQing(54, listOf(color.Blue), direction.Up, true),
            ChengQing(55, listOf(color.Black), direction.Up, true),
            ChengQing(56, listOf(color.Black), direction.Up, true),
            PoYi(57, listOf(color.Red, color.Black), direction.Left, true),
            PoYi(58, listOf(color.Blue, color.Black), direction.Left, true),
            PoYi(59, listOf(color.Red), direction.Left, true),
            PoYi(60, listOf(color.Blue), direction.Left, true),
            PoYi(61, listOf(color.Black), direction.Left, true),
            PoYi(62, listOf(color.Red, color.Black), direction.Right, true),
            PoYi(63, listOf(color.Blue, color.Black), direction.Right, true),
            PoYi(64, listOf(color.Red), direction.Right, true),
            PoYi(65, listOf(color.Blue), direction.Right, true),
            PoYi(66, listOf(color.Black), direction.Right, true),
            DiaoBao(67, listOf(color.Red), direction.Up, false),
            DiaoBao(68, listOf(color.Red), direction.Left, false),
            DiaoBao(69, listOf(color.Red), direction.Right, false),
            DiaoBao(70, listOf(color.Blue), direction.Up, false),
            DiaoBao(71, listOf(color.Blue), direction.Left, false),
            DiaoBao(72, listOf(color.Blue), direction.Right, false),
            DiaoBao(73, listOf(color.Black), direction.Left, false),
            DiaoBao(74, listOf(color.Black), direction.Right, false),
            DiaoBao(75, listOf(color.Red, color.Black), direction.Up, false),
            DiaoBao(76, listOf(color.Red, color.Black), direction.Right, false),
            DiaoBao(77, listOf(color.Blue, color.Black), direction.Up, false),
            DiaoBao(78, listOf(color.Blue, color.Black), direction.Left, false),
            JieHuo(79, listOf(color.Red), direction.Up, false),
            JieHuo(80, listOf(color.Red), direction.Up, false),
            JieHuo(81, listOf(color.Red), direction.Up, true),
            JieHuo(82, listOf(color.Blue), direction.Up, false),
            JieHuo(83, listOf(color.Blue), direction.Up, false),
            JieHuo(84, listOf(color.Blue), direction.Up, true),
            JieHuo(85, listOf(color.Black), direction.Up, false),
            JieHuo(86, listOf(color.Black), direction.Up, false),
            JieHuo(87, listOf(color.Black), direction.Up, true),
            JieHuo(88, listOf(color.Black), direction.Up, true),
            JieHuo(89, listOf(color.Blue, color.Black), direction.Up, false),
            JieHuo(90, listOf(color.Red, color.Black), direction.Up, false),
            WuDao(91, listOf(color.Red), direction.Up, false),
            WuDao(92, listOf(color.Red), direction.Left, false),
            WuDao(93, listOf(color.Red), direction.Right, false),
            WuDao(94, listOf(color.Blue), direction.Up, false),
            WuDao(95, listOf(color.Blue), direction.Left, false),
            WuDao(96, listOf(color.Blue), direction.Right, false),
            WuDao(97, listOf(color.Black), direction.Left, false),
            WuDao(98, listOf(color.Black), direction.Right, false),
            WuDao(99, listOf(color.Blue, color.Black), direction.Left, false),
            WuDao(100, listOf(color.Red, color.Black), direction.Right, false),
            FengYunBianHuan(101, listOf(color.Black), direction.Up, false),
            FengYunBianHuan(102, listOf(color.Black), direction.Up, false)
        )
    }
}