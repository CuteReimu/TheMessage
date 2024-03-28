package com.fengsheng

import com.fengsheng.card.Card
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*

/**
 * 统计三种颜色以及真情报的数量
 *
 * @param black 黑色情报的数量
 * @param red 红色情报的数量
 * @param blue 蓝色情报的数量
 * @param trueCard 真情报的数量
 */
class CountColors(cards: Iterable<Card>) {
    var black = 0
    var red = 0
    var blue = 0
    var trueCard = 0

    init {
        plusAssign(cards)
    }

    operator fun plusAssign(cards: Iterable<Card>) = cards.forEach { plusAssign(it.colors) }
    operator fun plusAssign(card: Card) = plusAssign(card.colors)
    operator fun plusAssign(colors: List<color>) = colors.forEach { plusAssign(it) }

    operator fun plusAssign(color: color) {
        when (color) {
            Black -> black++
            Red -> red++
            Blue -> blue++
            else -> {}
        }
    }

    operator fun get(color: color) = when (color) {
        Black -> black
        Red -> red
        Blue -> blue
        else -> 0
    }
}
