package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.direction.*
import com.fengsheng.protos.syncDeckNumToc
import com.fengsheng.send

class Deck(private val game: Game) {
    private var nextId = 0
    private var cards = ArrayList<Card>()
    private var discardPile = ArrayList<Card>()

    /**
     * 洗牌
     */
    private fun shuffle() {
        discardPile.shuffle()
        discardPile.addAll(cards)
        cards = discardPile
        discardPile = ArrayList()
        notifyDeckCount(true)
    }

    /**
     * 摸牌
     */
    fun draw(n: Int): List<Card> {
        if (n > cards.size) shuffle()
        val from = if (n > cards.size) 0 else cards.size - n
        val subList = cards.subList(from, cards.size)
        val result = subList.toList()
        subList.clear()
        notifyDeckCount(false)
        return result
    }

    /**
     * 查看牌堆顶的n张牌（不足n张则全返回）
     *
     * @return 返回查看的牌，它是牌堆的一个 [subList][List.subList] ，其中 [List.first] 是观看的最后一张牌，
     * [List.last] 是牌堆顶第一张牌。它的 [size][List.size] 可能小于n。
     */
    fun peek(n: Int): List<Card> {
        if (n > cards.size) shuffle()
        val from = if (n > cards.size) 0 else cards.size - n
        return cards.subList(from, cards.size)
    }

    /**
     * 往牌堆顶放牌
     *
     * @param cards 排在数组后面的牌将会放在上面
     */
    fun addFirst(cards: Collection<Card>) {
        this.cards.addAll(cards)
        notifyDeckCount(false)
    }

    /**
     * 往牌堆顶放牌
     */
    fun addFirst(card: Card) {
        cards.add(card)
        notifyDeckCount(false)
    }

    /**
     * 弃牌
     */
    fun discard(cards: List<Card>) {
        discardPile.addAll(cards)
    }

    /**
     * 弃牌
     */
    fun discard(card: Card) {
        discardPile.add(card)
    }

    /**
     * 获取并移除弃牌堆最上面的一张牌，若弃牌堆为空则返回`null`
     */
    fun popDiscardPile() = discardPile.removeLastOrNull()

    /**
     * 通知客户端牌堆剩余卡牌数量
     *
     * @param shuffled 是否洗牌
     */
    private fun notifyDeckCount(shuffled: Boolean) {
        game.players.send {
            syncDeckNumToc {
                num = cards.size
                this.shuffled = shuffled
            }
        }
    }

    /**
     * 如果需要新增一张卡牌，调用这个函数可以新卡牌的卡牌ID
     */
    fun getNextId(): Int {
        return ++nextId
    }

    private val shiTanIndex = listOf(16, 13, 11, 8, 3, 0)

    /**
     * （针对新手）往牌堆顶放入试探
     *
     * @param count 放入试探的数量，不要超过6张
     */
    fun pushShiTan(count: Int) {
        if (count == 0) return
        if (count == 1)
            addFirst(ShiTan(getNextId(), DefaultDeck[shiTanIndex.random()] as ShiTan))
        else
            addFirst(shiTanIndex.shuffled().subList(0, count).map {
                ShiTan(getNextId(), DefaultDeck[it] as ShiTan)
            })
    }

    fun init(totalPlayerCount: Int) {
        cards.clear()
        if (totalPlayerCount <= 4) {
            cards.addAll(DefaultDeck.subList(0, 108))
            cards.removeAt(55)
            cards.removeAt(54)
            cards.subList(0, 18).clear()
        } else {
            cards.addAll(DefaultDeck)
            if (totalPlayerCount <= 8) {
                cards.removeAt(55)
                cards.removeAt(54)
                shiTanIndex.forEach { cards.removeAt(it) }
            }
        }
        for (card in cards) {
            val index =
                if (card.colors.size == 1) card.colors.first().number * 4
                else card.colors[0].number * 3 + card.colors[1].number
            colorRates[index] += 1.0
        }
        for (i in colorRates.indices) colorRates[i] /= cards.size.toDouble()
        nextId = DefaultDeck.last().id
        cards.shuffle()
    }

    val colorRates = DoubleArray(9)

    companion object {
        val DefaultDeck = listOf(
            ShiTan(1, listOf(Black), Right, false, listOf(Black)),
            ShiTan(2, listOf(Black), Right, false, listOf(Blue)),
            ShiTan(3, listOf(Black), Right, false, listOf(Red, Black)),
            ShiTan(4, listOf(Black), Left, false, listOf(Red, Blue)),
            ShiTan(5, listOf(Black), Left, false, listOf(Blue, Black)),
            ShiTan(6, listOf(Black), Left, false, listOf(Red)),
            ShiTan(7, listOf(Red), Right, false, listOf(Black)),
            ShiTan(8, listOf(Red), Right, false, listOf(Blue)),
            ShiTan(9, listOf(Red), Right, false, listOf(Red, Black)),
            ShiTan(10, listOf(Red), Left, false, listOf(Red, Blue)),
            ShiTan(11, listOf(Red), Left, false, listOf(Blue, Black)),
            ShiTan(12, listOf(Red), Left, false, listOf(Red)),
            ShiTan(13, listOf(Blue), Right, false, listOf(Black)),
            ShiTan(14, listOf(Blue), Right, false, listOf(Blue)),
            ShiTan(15, listOf(Blue), Right, false, listOf(Red, Black)),
            ShiTan(16, listOf(Blue), Left, false, listOf(Red, Blue)),
            ShiTan(17, listOf(Blue), Left, false, listOf(Blue, Black)),
            ShiTan(18, listOf(Blue), Left, false, listOf(Red)),
            PingHeng(19, listOf(Black), Left, true),
            PingHeng(20, listOf(Black), Right, true),
            PingHeng(21, listOf(Blue), Left, true),
            PingHeng(22, listOf(Red), Right, true),
            PingHeng(23, listOf(Red, Black), Up, false),
            PingHeng(24, listOf(Blue, Black), Up, false),
            PingHeng(25, listOf(Red, Black), Left, false),
            PingHeng(26, listOf(Blue, Black), Right, false),
            WeiBi(27, listOf(Red), Left, false),
            WeiBi(28, listOf(Red), Left, false),
            WeiBi(29, listOf(Red), Left, false),
            WeiBi(30, listOf(Red), Right, false),
            WeiBi(31, listOf(Blue), Left, false),
            WeiBi(32, listOf(Blue), Right, false),
            WeiBi(33, listOf(Blue), Right, false),
            WeiBi(34, listOf(Blue), Right, false),
            WeiBi(35, listOf(Black), Left, false),
            WeiBi(36, listOf(Black), Left, false),
            WeiBi(37, listOf(Black), Right, false),
            WeiBi(38, listOf(Black), Right, false),
            WeiBi(39, listOf(Blue, Black), Left, false),
            WeiBi(40, listOf(Red, Black), Right, false),
            LiYou(41, listOf(Black), Left, true),
            LiYou(42, listOf(Black), Right, true),
            LiYou(43, listOf(Black), Left, true),
            LiYou(44, listOf(Black), Right, true),
            LiYou(45, listOf(Black), Left, true),
            LiYou(46, listOf(Black), Right, true),
            LiYou(47, listOf(Blue), Left, true),
            LiYou(48, listOf(Red), Right, true),
            ChengQing(49, listOf(Red), Up, true),
            ChengQing(50, listOf(Red), Up, true),
            ChengQing(51, listOf(Black), Up, true),
            ChengQing(52, listOf(Black), Up, true),
            ChengQing(53, listOf(Blue), Up, true),
            ChengQing(54, listOf(Blue), Up, true),
            ChengQing(55, listOf(Black), Up, true),
            ChengQing(56, listOf(Black), Up, true),
            PoYi(57, listOf(Red, Black), Left, true),
            PoYi(58, listOf(Blue, Black), Left, true),
            PoYi(59, listOf(Red), Left, true),
            PoYi(60, listOf(Blue), Left, true),
            PoYi(61, listOf(Black), Left, true),
            PoYi(62, listOf(Red, Black), Right, true),
            PoYi(63, listOf(Blue, Black), Right, true),
            PoYi(64, listOf(Red), Right, true),
            PoYi(65, listOf(Blue), Right, true),
            PoYi(66, listOf(Black), Right, true),
            DiaoBao(67, listOf(Red), Up, false),
            DiaoBao(68, listOf(Red), Left, false),
            DiaoBao(69, listOf(Red), Right, false),
            DiaoBao(70, listOf(Blue), Up, false),
            DiaoBao(71, listOf(Blue), Left, false),
            DiaoBao(72, listOf(Blue), Right, false),
            DiaoBao(73, listOf(Black), Left, false),
            DiaoBao(74, listOf(Black), Right, false),
            DiaoBao(75, listOf(Red, Black), Up, false),
            DiaoBao(76, listOf(Red, Black), Right, false),
            DiaoBao(77, listOf(Blue, Black), Up, false),
            DiaoBao(78, listOf(Blue, Black), Left, false),
            JieHuo(79, listOf(Red), Up, false),
            JieHuo(80, listOf(Red), Up, false),
            JieHuo(81, listOf(Red), Up, true),
            JieHuo(82, listOf(Blue), Up, false),
            JieHuo(83, listOf(Blue), Up, false),
            JieHuo(84, listOf(Blue), Up, true),
            JieHuo(85, listOf(Black), Up, false),
            JieHuo(86, listOf(Black), Up, false),
            JieHuo(87, listOf(Black), Up, true),
            JieHuo(88, listOf(Black), Up, true),
            JieHuo(89, listOf(Blue, Black), Up, false),
            JieHuo(90, listOf(Red, Black), Up, false),
            WuDao(91, listOf(Red), Up, false),
            WuDao(92, listOf(Red), Left, false),
            WuDao(93, listOf(Red), Right, false),
            WuDao(94, listOf(Blue), Up, false),
            WuDao(95, listOf(Blue), Left, false),
            WuDao(96, listOf(Blue), Right, false),
            WuDao(97, listOf(Black), Left, false),
            WuDao(98, listOf(Black), Right, false),
            WuDao(99, listOf(Blue, Black), Left, false),
            WuDao(100, listOf(Red, Black), Right, false),
            FengYunBianHuan(101, listOf(Black), Up, true),
            FengYunBianHuan(102, listOf(Black), Up, true),
            MiLing(103, listOf(Blue, Black), Up, true, listOf(Blue, Black, Red)),
            MiLing(104, listOf(Blue), Right, false, listOf(Black, Blue, Red)),
            MiLing(105, listOf(Blue), Left, false, listOf(Blue, Red, Black)),
            MiLing(106, listOf(Red, Black), Up, true, listOf(Red, Black, Blue)),
            // 这张牌的实际卡牌是蓝红黑，和105重复了，这里优化一下，改成黑红蓝
            MiLing(107, listOf(Red), Left, false, listOf(Black, Red, Blue)),
            MiLing(108, listOf(Red), Right, false, listOf(Red, Blue, Black)),
            DiaoHuLiShan(109, listOf(Black), Up, false),
            // DiaoHuLiShan(110, listOf(Black), Left, false),
            // DiaoHuLiShan(111, listOf(Black), Right, false),
            DiaoHuLiShan(112, listOf(Black), Up, true),
            DiaoHuLiShan(113, listOf(Red, Black), Left, true),
            DiaoHuLiShan(114, listOf(Blue, Black), Right, true),
            YuQinGuZong(115, listOf(Red, Black), Up, false),
            YuQinGuZong(116, listOf(Red, Blue), Left, false),
            YuQinGuZong(117, listOf(Red, Blue), Right, false),
            YuQinGuZong(118, listOf(Blue, Black), Up, false),
            YuQinGuZong(119, listOf(Red, Blue), Left, false),
            YuQinGuZong(120, listOf(Red, Blue), Right, false),
            // WuDao(121, listOf(Black), Up, false),
        )
    }
}
