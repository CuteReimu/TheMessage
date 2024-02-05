package com.fengsheng

import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.ScoreFactory.logger
import com.fengsheng.card.Card
import com.fengsheng.card.count
import com.fengsheng.card.countTrueCard
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.direction.*
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.skill.*
import com.fengsheng.skill.LengXueXunLian.MustLockOne

/**
 * 判断玩家是否会死
 */
fun Player.willDie(card: Card): Boolean = messageCards.count(Black) >= 2 && card.isBlack()

/**
 * 判断玩家是否能赢
 *
 * @param whoseTurn 当前回合玩家
 * @param inFrontOfWhom 情报在谁面前
 * @param card 情报牌
 */
fun Player.willWin(whoseTurn: Player, inFrontOfWhom: Player, card: Card): Boolean {
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
    if (game!!.players.any { isPartnerOrSelf(it!!) && it.willWin(whoseTurn, inFrontOfWhom, card) }) return 600
    if (game!!.players.any { isEnemy(it!!) && it.willWin(whoseTurn, inFrontOfWhom, card) }) return -600
    var value = 0
    if (identity == Black) {
        if (secretTask == Collector && this === inFrontOfWhom) {
            if (Red in card.colors) {
                value += when (messageCards.count(Red)) {
                    0 -> 10
                    1 -> 100
                    else -> 1000
                }
            }
            if (Blue in card.colors) {
                value += when (messageCards.count(Blue)) {
                    0 -> 10
                    1 -> 100
                    else -> 1000
                }
            }
        }
        if (secretTask !in listOf(Killer, Pioneer, Sweeper)) {
            if (this === inFrontOfWhom && Black in card.colors) {
                value -= when (inFrontOfWhom.messageCards.count(Black)) {
                    0 -> 1
                    1 -> 11
                    else -> 111
                }
            }
        } else {
            if (card.isBlack()) {
                value += when (inFrontOfWhom.messageCards.count(Black)) {
                    0 -> 1
                    1 -> 11
                    else -> -111
                }
                if (secretTask == Pioneer && this === inFrontOfWhom) {
                    value += when (inFrontOfWhom.messageCards.count(Black)) {
                        0 -> 1
                        1 -> 11
                        else -> -111
                    }
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

/**
 * 计算应该选择哪张情报传出的结果
 *
 * @param card 传出的牌
 * @param target 传出的目标
 * @param dir 传递方向
 * @param lockedPlayers 被锁定的玩家
 * @param value 价值
 */
class SendMessageCardResult(
    val card: Card,
    val target: Player,
    val dir: direction,
    var lockedPlayers: List<Player>,
    val value: Double
)

/**
 * 计算应该选择哪张情报传出
 *
 * @param availableCards 可以选择的牌，默认为r的所有手牌
 */
fun Player.calSendMessageCard(
    whoseTurn: Player = this,
    availableCards: List<Card> = cards,
): SendMessageCardResult {
    if (availableCards.isEmpty()) {
        logger.error("没有可用的情报牌，玩家手牌：${cards.joinToString()}")
        throw IllegalArgumentException("没有可用的情报牌")
    }
    var value = Double.NEGATIVE_INFINITY
    // 先随便填一个，反正后面要替换
    var result = SendMessageCardResult(availableCards[0], game!!.players[0]!!, Up, emptyList(), 0.0)

    fun calAveValue(
        card: Card,
        attenuation: Double,
        nextPlayerFunc: Player.() -> Player
    ): Double {
        var sum = 0.0
        var n = 0.0
        var currentPlayer = nextPlayerFunc()
        var currentPercent = 1.0
        val canLock = card.canLock() || skills.any { it is MustLockOne || it is QiangYingXiaLing }
        while (true) {
            var m = currentPercent
            if (canLock) m *= m
            else if (isPartnerOrSelf(currentPlayer)) m *= 1.2
            sum += calculateMessageCardValue(whoseTurn, currentPlayer, card) * m
            n += m
            if (currentPlayer === this) break
            currentPlayer = currentPlayer.nextPlayerFunc()
            currentPercent *= attenuation
        }
        return sum / n
    }

    for (card in availableCards.sortCards(identity, true)) {
        if (card.direction == Up || skills.any { it is LianLuo }) {
            for (target in game!!.sortedFrom(game!!.players, location)) {
                if (target === this || !target.alive) continue
                val tmpValue = calAveValue(card, 0.7) { if (this === target) this@calSendMessageCard else target }
                if (tmpValue > value) {
                    value = tmpValue
                    result = SendMessageCardResult(card, target, Up, emptyList(), value)
                }
            }
        } else if (card.direction == Left) {
            val tmpValue = calAveValue(card, 0.7, Player::getNextLeftAlivePlayer)
            if (tmpValue > value) {
                value = tmpValue
                result = SendMessageCardResult(card, getNextLeftAlivePlayer(), Left, emptyList(), value)
            }
        } else if (card.direction == Right) {
            val tmpValue = calAveValue(card, 0.7, Player::getNextRightAlivePlayer)
            if (tmpValue > value) {
                value = tmpValue
                result = SendMessageCardResult(card, getNextRightAlivePlayer(), Right, emptyList(), value)
            }
        }
    }
    if (result.card.canLock() || skills.any { it is MustLockOne || it is QiangYingXiaLing }) {
        var maxValue = Int.MIN_VALUE
        var lockTarget: Player? = null
        val targets =
            if (result.dir == Up) listOf(this, result.target)
            else game!!.players.filter { it!!.alive }
        for (player in game!!.sortedFrom(targets, location)) {
            val v = calculateMessageCardValue(whoseTurn, player, result.card)
            if (v > maxValue) {
                maxValue = v
                lockTarget = player
            }
        }
        lockTarget?.let { if (result.dir == Up && it.isPartner(this) || it !== this) result.lockedPlayers = listOf(it) }
    }
    logger.debug("计算结果：${result.card}(cardId:${result.card.id})传递给${result.target}，方向是${result.dir}，分数为${result.value}")
    return result
}

/**
 * 是否要救人
 */
fun Player.wantToSave(whoseTurn: Player, whoDie: Player): Boolean {
    var save = isPartnerOrSelf(whoDie)
    var notSave = false
    val killer = game!!.players.find { it!!.alive && it.identity == Black && it.secretTask == Killer }
    val pioneer = game!!.players.find { it!!.alive && it.identity == Black && it.secretTask == Pioneer }
    val sweeper = game!!.players.find { it!!.alive && it.identity == Black && it.secretTask == Sweeper }
    if (killer === whoseTurn && whoDie.messageCards.countTrueCard() >= 2) {
        if (killer === this) notSave = true
        save = save || killer !== this
    }
    if (pioneer === whoDie && whoDie.messageCards.countTrueCard() >= 1) {
        if (pioneer === this) notSave = true
        save = save || pioneer !== this
    }
    if (sweeper != null && whoDie.messageCards.run { count(Red) <= 1 && count(Blue) <= 1 }) {
        if (sweeper === this) notSave = true
        save = save || sweeper !== this
    }
    return !notSave && save
}

class FightPhaseResult(
    val cardType: card_type,
    val card: Card,
    val wuDaoTarget: Player?,
    val value: Int
)

fun Player.calFightPhase(
    e: FightPhaseIdle,
    whoUse: Player = this,
    availableCards: List<Card> = this.cards
): FightPhaseResult? {
    val order = mutableListOf(Jie_Huo, Wu_Dao, Diao_Bao)
    order.shuffle()
    if (skills.any { it is YouDao || it is JiangJiJiuJi }) {
        if (roleFaceUp) {
            order[order.indexOf(Wu_Dao)] = order[0]
            order[0] = Wu_Dao
        } else {
            order[order.indexOf(Wu_Dao)] = order[2]
            order[2] = Wu_Dao
        }
    } else if (skills.any { it is ShunShiErWei || it is ShenCang }) {
        if (roleFaceUp) {
            order[order.indexOf(Jie_Huo)] = order[0]
            order[0] = Jie_Huo
        } else {
            order[order.indexOf(Jie_Huo)] = order[2]
            order[2] = Jie_Huo
        }
    } else if (skills.any { it is HuanRi }) {
        if (roleFaceUp) {
            order[order.indexOf(Diao_Bao)] = order[0]
            order[0] = Diao_Bao
        } else {
            order[order.indexOf(Diao_Bao)] = order[2]
            order[2] = Diao_Bao
        }
    }
    var value = calculateMessageCardValue(e.whoseTurn, e.inFrontOfWhom, e.messageCard)
    var result: FightPhaseResult? = null
    val cards = availableCards.sortCards(identity)
    for (cardType in order) {
        !cannotPlayCard(cardType) || continue
        loop@ for (card in cards) {
            val (ok, _) = canUseCardTypes(cardType, card)
            ok || continue
            when (cardType) {
                Jie_Huo -> {
                    val newValue = calculateMessageCardValue(e.whoseTurn, whoUse, e.messageCard)
                    if (newValue > value) {
                        result = FightPhaseResult(cardType, card, null, newValue)
                        value = newValue
                    }
                    break@loop
                }

                Diao_Bao -> {
                    val newValue = calculateMessageCardValue(e.whoseTurn, e.inFrontOfWhom, card)
                    if (newValue > value) {
                        result = FightPhaseResult(cardType, card, null, newValue)
                        value = newValue
                    }
                }

                else -> { // Wu_Dao
                    val left = e.inFrontOfWhom.getNextLeftAlivePlayer()
                    val newValueLeft = calculateMessageCardValue(e.whoseTurn, left, e.messageCard)
                    if (newValueLeft > value) {
                        result = FightPhaseResult(cardType, card, left, newValueLeft)
                        value = newValueLeft
                    }
                    val right = e.inFrontOfWhom.getNextRightAlivePlayer()
                    val newValueRight = calculateMessageCardValue(e.whoseTurn, right, e.messageCard)
                    if (newValueRight > value) {
                        result = FightPhaseResult(cardType, card, right, newValueRight)
                        value = newValueRight
                    }
                    break@loop
                }
            }
        }
    }
    return result
}