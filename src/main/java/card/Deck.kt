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
            ShiTan(1, arrayOf(color.Black), direction.Right, false, arrayOf(color.Black)),
            ShiTan(2, arrayOf(color.Black), direction.Right, false, arrayOf(color.Blue)),
            ShiTan(3, arrayOf(color.Black), direction.Right, false, arrayOf(color.Red, color.Black)),
            ShiTan(4, arrayOf(color.Black), direction.Left, false, arrayOf(color.Red, color.Blue)),
            ShiTan(5, arrayOf(color.Black), direction.Left, false, arrayOf(color.Blue, color.Black)),
            ShiTan(6, arrayOf(color.Black), direction.Left, false, arrayOf(color.Red)),
            ShiTan(7, arrayOf(color.Red), direction.Right, false, arrayOf(color.Black)),
            ShiTan(8, arrayOf(color.Red), direction.Right, false, arrayOf(color.Blue)),
            ShiTan(9, arrayOf(color.Red), direction.Right, false, arrayOf(color.Red, color.Black)),
            ShiTan(10, arrayOf(color.Red), direction.Left, false, arrayOf(color.Red, color.Blue)),
            ShiTan(11, arrayOf(color.Red), direction.Left, false, arrayOf(color.Blue, color.Black)),
            ShiTan(12, arrayOf(color.Red), direction.Left, false, arrayOf(color.Red)),
            ShiTan(13, arrayOf(color.Blue), direction.Right, false, arrayOf(color.Black)),
            ShiTan(14, arrayOf(color.Blue), direction.Right, false, arrayOf(color.Blue)),
            ShiTan(15, arrayOf(color.Blue), direction.Right, false, arrayOf(color.Red, color.Black)),
            ShiTan(16, arrayOf(color.Blue), direction.Left, false, arrayOf(color.Red, color.Blue)),
            ShiTan(17, arrayOf(color.Blue), direction.Left, false, arrayOf(color.Blue, color.Black)),
            ShiTan(18, arrayOf(color.Blue), direction.Left, false, arrayOf(color.Red)),
            PingHeng(19, arrayOf(color.Black), direction.Left, true),
            PingHeng(20, arrayOf(color.Black), direction.Right, true),
            PingHeng(21, arrayOf(color.Blue), direction.Left, true),
            PingHeng(22, arrayOf(color.Red), direction.Right, true),
            PingHeng(23, arrayOf(color.Red, color.Black), direction.Up, false),
            PingHeng(24, arrayOf(color.Blue, color.Black), direction.Up, false),
            PingHeng(25, arrayOf(color.Red, color.Black), direction.Left, false),
            PingHeng(26, arrayOf(color.Blue, color.Black), direction.Right, false),
            WeiBi(27, arrayOf(color.Red), direction.Left, false),
            WeiBi(28, arrayOf(color.Red), direction.Left, false),
            WeiBi(29, arrayOf(color.Red), direction.Left, false),
            WeiBi(30, arrayOf(color.Red), direction.Right, false),
            WeiBi(31, arrayOf(color.Blue), direction.Left, false),
            WeiBi(32, arrayOf(color.Blue), direction.Right, false),
            WeiBi(33, arrayOf(color.Blue), direction.Right, false),
            WeiBi(34, arrayOf(color.Blue), direction.Right, false),
            WeiBi(35, arrayOf(color.Black), direction.Left, false),
            WeiBi(36, arrayOf(color.Black), direction.Left, false),
            WeiBi(37, arrayOf(color.Black), direction.Right, false),
            WeiBi(38, arrayOf(color.Black), direction.Right, false),
            WeiBi(39, arrayOf(color.Blue, color.Black), direction.Left, false),
            WeiBi(40, arrayOf(color.Red, color.Black), direction.Right, false),
            LiYou(41, arrayOf(color.Black), direction.Left, true),
            LiYou(42, arrayOf(color.Black), direction.Right, true),
            LiYou(43, arrayOf(color.Black), direction.Left, true),
            LiYou(44, arrayOf(color.Black), direction.Right, true),
            LiYou(45, arrayOf(color.Black), direction.Left, true),
            LiYou(46, arrayOf(color.Black), direction.Right, true),
            LiYou(47, arrayOf(color.Blue), direction.Left, true),
            LiYou(48, arrayOf(color.Red), direction.Right, true),
            ChengQing(49, arrayOf(color.Red), direction.Up, true),
            ChengQing(50, arrayOf(color.Red), direction.Up, true),
            ChengQing(51, arrayOf(color.Black), direction.Up, true),
            ChengQing(52, arrayOf(color.Black), direction.Up, true),
            ChengQing(53, arrayOf(color.Blue), direction.Up, true),
            ChengQing(54, arrayOf(color.Blue), direction.Up, true),
            ChengQing(55, arrayOf(color.Black), direction.Up, true),
            ChengQing(56, arrayOf(color.Black), direction.Up, true),
            PoYi(57, arrayOf(color.Red, color.Black), direction.Left, true),
            PoYi(58, arrayOf(color.Blue, color.Black), direction.Left, true),
            PoYi(59, arrayOf(color.Red), direction.Left, true),
            PoYi(60, arrayOf(color.Blue), direction.Left, true),
            PoYi(61, arrayOf(color.Black), direction.Left, true),
            PoYi(62, arrayOf(color.Red, color.Black), direction.Right, true),
            PoYi(63, arrayOf(color.Blue, color.Black), direction.Right, true),
            PoYi(64, arrayOf(color.Red), direction.Right, true),
            PoYi(65, arrayOf(color.Blue), direction.Right, true),
            PoYi(66, arrayOf(color.Black), direction.Right, true),
            DiaoBao(67, arrayOf(color.Red), direction.Up, false),
            DiaoBao(68, arrayOf(color.Red), direction.Left, false),
            DiaoBao(69, arrayOf(color.Red), direction.Right, false),
            DiaoBao(70, arrayOf(color.Blue), direction.Up, false),
            DiaoBao(71, arrayOf(color.Blue), direction.Left, false),
            DiaoBao(72, arrayOf(color.Blue), direction.Right, false),
            DiaoBao(73, arrayOf(color.Black), direction.Left, false),
            DiaoBao(74, arrayOf(color.Black), direction.Right, false),
            DiaoBao(75, arrayOf(color.Red, color.Black), direction.Up, false),
            DiaoBao(76, arrayOf(color.Red, color.Black), direction.Right, false),
            DiaoBao(77, arrayOf(color.Blue, color.Black), direction.Up, false),
            DiaoBao(78, arrayOf(color.Blue, color.Black), direction.Left, false),
            JieHuo(79, arrayOf(color.Red), direction.Up, false),
            JieHuo(80, arrayOf(color.Red), direction.Up, false),
            JieHuo(81, arrayOf(color.Red), direction.Up, true),
            JieHuo(82, arrayOf(color.Blue), direction.Up, false),
            JieHuo(83, arrayOf(color.Blue), direction.Up, false),
            JieHuo(84, arrayOf(color.Blue), direction.Up, true),
            JieHuo(85, arrayOf(color.Black), direction.Up, false),
            JieHuo(86, arrayOf(color.Black), direction.Up, false),
            JieHuo(87, arrayOf(color.Black), direction.Up, true),
            JieHuo(88, arrayOf(color.Black), direction.Up, true),
            JieHuo(89, arrayOf(color.Blue, color.Black), direction.Up, false),
            JieHuo(90, arrayOf(color.Red, color.Black), direction.Up, false),
            WuDao(91, arrayOf(color.Red), direction.Up, false),
            WuDao(92, arrayOf(color.Red), direction.Left, false),
            WuDao(93, arrayOf(color.Red), direction.Right, false),
            WuDao(94, arrayOf(color.Blue), direction.Up, false),
            WuDao(95, arrayOf(color.Blue), direction.Left, false),
            WuDao(96, arrayOf(color.Blue), direction.Right, false),
            WuDao(97, arrayOf(color.Black), direction.Left, false),
            WuDao(98, arrayOf(color.Black), direction.Right, false),
            WuDao(99, arrayOf(color.Blue, color.Black), direction.Left, false),
            WuDao(100, arrayOf(color.Red, color.Black), direction.Right, false),
            FengYunBianHuan(101, arrayOf(color.Black), direction.Up, false),
            FengYunBianHuan(102, arrayOf(color.Black), direction.Up, false)
        )
    }
}