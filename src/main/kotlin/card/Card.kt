package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.Player
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Common.color.*

/**
 * 注意：一张卡牌一定是不能修改的
 */
abstract class Card {
    /**
     * 获取卡牌ID。卡牌ID一定大于0
     */
    val id: Int

    /**
     * 获取卡牌颜色
     */
    val colors: List<color>

    /**
     * 获取卡牌方向
     */
    val direction: direction
    protected val lockable: Boolean
    private val originCard: Card?

    protected constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) {
        this.id = id
        this.colors = colors
        this.direction = direction
        this.lockable = lockable
        originCard = null
    }

    protected constructor(id: Int, card: Card) {
        this.id = id
        colors = card.colors
        direction = card.direction
        lockable = card.lockable
        originCard = null
    }

    /**
     * 仅用于“作为……使用”
     */
    internal constructor(card: Card) {
        id = card.id
        colors = card.colors
        direction = card.direction
        lockable = card.lockable
        originCard = card
    }

    /**
     * 获取卡牌类型
     */
    abstract val type: card_type

    /**
     * 获取卡牌作为情报传递时是否可以锁定
     */
    fun canLock(): Boolean {
        return lockable
    }

    /**
     * 获取原本的卡牌
     *
     * @return 如果原本卡牌就是自己，则返回自己。如果是“作为……使用”，则返回原卡牌。
     */
    fun getOriginCard(): Card {
        return originCard ?: this
    }

    /**
     * 判断卡牌是否能够使用。参数和[.execute]的参数相同
     *
     * @param r    使用者
     * @param args 根据不同的卡牌，传入的其他不同参数
     */
    abstract fun canUse(g: Game, r: Player, vararg args: Any): Boolean

    /**
     * 执行卡牌的逻辑。参数和[.canUse]的参数相同
     *
     * @param r    使用者
     * @param args 根据不同的卡牌，传入的其他不同参数
     */
    abstract fun execute(g: Game, r: Player, vararg args: Any)

    fun hasSameColor(card2: Card) = colors.any { it in card2.colors }

    override fun hashCode(): Int {
        return id
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Card) return false
        return other.id == id
    }

    open fun toPbCard(): card {
        val c = getOriginCard()
        val builder = card.newBuilder()
        builder.cardId = c.id
        builder.cardDir = c.direction
        builder.canLock = c.lockable
        builder.cardType = c.type
        builder.addAllCardColor(c.colors)
        return builder.build()
    }

    fun isBlack(): Boolean = Black in colors
    fun isPureBlack(): Boolean = colors.size == 1 && colors.first() == Black

    companion object {
        /**
         * （日志用）将颜色转为卡牌的字符串
         */
        fun cardColorToString(colors: List<color?>): String {
            val sb = StringBuilder()
            for (c in colors) {
                when (c) {
                    Red -> sb.append("红")
                    Blue -> sb.append("蓝")
                    Black -> sb.append("黑")
                    else -> throw RuntimeException("unknown color: " + colors.toTypedArray().contentToString())
                }
            }
            when (colors.size) {
                1 -> sb.append("色")
                2 -> sb.append("双色")
                else -> throw RuntimeException("unknown color: " + colors.toTypedArray().contentToString())
            }
            return sb.toString()
        }

        fun falseCard(falseType: card_type, originCard: Card): Card {
            return when (falseType) {
                Cheng_Qing -> ChengQing(originCard)
                Wei_Bi -> WeiBi(originCard)
                Li_You -> LiYou(originCard)
                Ping_Heng -> PingHeng(originCard)
                Po_Yi -> PoYi(originCard)
                Jie_Huo -> JieHuo(originCard)
                Diao_Bao -> DiaoBao(originCard)
                Wu_Dao -> WuDao(originCard)
                Feng_Yun_Bian_Huan -> FengYunBianHuan(originCard)
                Diao_Hu_Li_Shan -> DiaoHuLiShan(originCard)
                Yu_Qin_Gu_Zong -> YuQinGuZong(originCard)
                else -> throw IllegalStateException("Unexpected value: $falseType")
            }
        }
    }
}

fun Iterable<Card>.count(c: color) = count { c in it.colors }

fun Iterable<Card>.filter(c: color) = filter { c in it.colors }

fun Iterable<Card>.countTrueCard() = count { card -> card.colors.any { c -> c != Black } }
