package com.fengsheng

import com.fengsheng.card.Card
import com.fengsheng.card.count
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task.*

/**
 * 判断玩家是否能赢
 *
 * @param whoseTurn 当前回合玩家
 * @param inFrontOfWhom 情报在谁面前
 * @param card 情报牌
 */
private fun Player.willWin(whoseTurn: Player, inFrontOfWhom: Player, card: Card): Boolean {
    if (!alive) return false
    if (identity != Black) {
        return isPartnerOrSelf(inFrontOfWhom) && identity in card.colors && inFrontOfWhom.messageCards.count(identity) >= 2
    } else {
        return when (secretTask) {
            Killer -> {
                if (this !== whoseTurn) return false
                if (game!!.players.any {
                        (it!!.identity != Black || it.secretTask in listOf(Collector, Mutator)) &&
                                it.willWin(whoseTurn, inFrontOfWhom, card)
                    }) {
                    return false
                }
                Black in card.colors && inFrontOfWhom.messageCards.count(Black) >= 2
            }

            Stealer ->
                this === whoseTurn && game!!.players.any { it !== this && it!!.willWin(whoseTurn, inFrontOfWhom, card) }

            Collector ->
                this === inFrontOfWhom &&
                        if (Red in card.colors) messageCards.count(Red) >= 2
                        else if (Blue in card.colors) messageCards.count(Blue) >= 2
                        else false

            Mutator ->
                (inFrontOfWhom === this || !inFrontOfWhom.willWin(whoseTurn, inFrontOfWhom, card)) &&
                        if (Red in card.colors) messageCards.count(Red) >= 2
                        else if (Blue in card.colors) messageCards.count(Blue) >= 2
                        else false

            Pioneer ->
                this === inFrontOfWhom && Black in card.colors && messageCards.count(Black) >= 2

            Sweeper ->
                Black in card.colors && inFrontOfWhom.messageCards.count(Black) >= 2 &&
                        if (Red in card.colors) inFrontOfWhom.messageCards.all { Red !in it.colors }
                        else if (Blue in card.colors) inFrontOfWhom.messageCards.all { Blue !in it.colors }
                        else true

            else -> false
        }
    }
}

/**
 * 计算情报牌的价值
 *
 * @param whoseTurn 当前回合玩家
 * @param inFrontOfWhom 情报在谁面前
 * @param card 情报牌
 */
fun Player.calculateMessageCardValue(whoseTurn: Player, inFrontOfWhom: Player, card: Card): Int {
    if (game!!.players.any { isPartnerOrSelf(it!!) && willWin(whoseTurn, inFrontOfWhom, card) }) return 600
    if (game!!.players.any { isEnemy(it!!) && willWin(whoseTurn, inFrontOfWhom, card) }) return -600
    var value = 0
    if (identity == Black) {
        if (secretTask == Collector) {
            if (Red in card.colors) {
                value += when (inFrontOfWhom.messageCards.count(Red)) {
                    0 -> 10
                    1 -> 100
                    else -> 1000
                }
            }
            if (Blue in card.colors) {
                value += when (inFrontOfWhom.messageCards.count(Blue)) {
                    0 -> 10
                    1 -> 100
                    else -> 1000
                }
            }
        }
        if (secretTask !in listOf(Killer, Pioneer, Sweeper)) {
            if (Black in card.colors) {
                value -= when (inFrontOfWhom.messageCards.count(Black)) {
                    0 -> 1
                    1 -> 11
                    else -> 111
                }
            }
        }
    } else {
        val myColor = identity
        val enemyColor = (listOf(Red, Blue) - myColor).first()
        if (inFrontOfWhom.identity == myColor) { // 队友
            if (myColor in card.colors) {
                value += when (inFrontOfWhom.messageCards.count(myColor)) {
                    0 -> 10
                    1 -> 100
                    else -> 1000
                }
            }
            if (Black in card.colors) {
                value -= when (inFrontOfWhom.messageCards.count(Black)) {
                    0 -> 1
                    1 -> 11
                    else -> 111
                }
            }
        } else if (inFrontOfWhom.identity == enemyColor) { // 敌人
            if (enemyColor in card.colors) {
                value -= when (inFrontOfWhom.messageCards.count(enemyColor)) {
                    0 -> 10
                    1 -> 100
                    else -> 1000
                }
            }
            if (Black in card.colors) {
                value += when (inFrontOfWhom.messageCards.count(Black)) {
                    0 -> 1
                    1 -> 11
                    else -> 111
                }
            }
        }
    }
    return value.coerceIn(-600..600)
}