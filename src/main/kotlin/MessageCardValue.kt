package com.fengsheng

import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.ScoreFactory.logger
import com.fengsheng.card.Card
import com.fengsheng.card.count
import com.fengsheng.card.countTrueCard
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.direction.*
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.skill.*
import com.fengsheng.skill.LengXueXunLian.MustLockOne
import kotlin.random.Random

/**
 * 判断是否有玩家会死或者赢
 */
fun Iterable<Player?>.anyoneWillWinOrDie(e: FightPhaseIdle) = any {
    it!!.willWin(e.whoseTurn, e.inFrontOfWhom, e.messageCard)
} || e.inFrontOfWhom.willDie(e.messageCard)

/**
 * 判断玩家是否会死
 */
fun Player.willDie(card: Card) = messageCards.count(Black) >= 2 && card.isBlack()

/**
 * 判断玩家是否会死
 */
fun Player.willDie(colors: List<color>) = messageCards.count(Black) >= 2 && Black in colors

/**
 * 判断玩家是否能赢
 *
 * @param whoseTurn 当前回合玩家
 * @param inFrontOfWhom 情报在谁面前
 * @param card 情报牌
 */
fun Player.willWin(whoseTurn: Player, inFrontOfWhom: Player, card: Card) =
    calculateMessageCardValue(whoseTurn, inFrontOfWhom, card) >= 600

/**
 * 判断玩家是否能赢
 *
 * @param whoseTurn 当前回合玩家
 * @param inFrontOfWhom 情报在谁面前
 * @param colors 情报牌的颜色
 */
fun Player.willWin(whoseTurn: Player, inFrontOfWhom: Player, colors: List<color>) =
    calculateMessageCardValue(whoseTurn, inFrontOfWhom, colors) >= 600

/**
 * 判断玩家是否赢了，考虑CP小九和CP韩梅的技能，不考虑簒夺者
 */
private fun Player.willWinInternalCp(
    whoseTurn: Player,
    inFrontOfWhom: Player,
    colors: List<color>,
    partnerTogether: Boolean = true
): Boolean {
    if (!alive) return false
    if (willWinInternal(whoseTurn, inFrontOfWhom, colors, partnerTogether)) return true
    val g = whoseTurn.game!!
    fun isCpXiaoJiu(p: Player?) = p!!.alive && p.skills.any { it is YiZhongRen }
    fun isCpHanMei(p: Player?) = p!!.alive && p.skills.any { it is BaiYueGuang }
    fun isHanMei(p: Player) = p.alive && p.roleName.endsWith("韩梅")
    fun isXiaoJiu(p: Player) = p.alive && p.roleName.endsWith("小九")
    val counts = inFrontOfWhom.countMessageCard(colors)
    if (isCpXiaoJiu(this) && isHanMei(inFrontOfWhom) && counts[Red.number] >= 3 &&
        !inFrontOfWhom.willWinInternal(whoseTurn, inFrontOfWhom, colors, false)
    ) return true
    if (isCpHanMei(this) && isXiaoJiu(inFrontOfWhom) && counts[Blue.number] >= 3 &&
        !inFrontOfWhom.willWinInternal(whoseTurn, inFrontOfWhom, colors, false)
    ) return true
    if (this === inFrontOfWhom) {
        if (isHanMei(this) && counts[Red.number] >= 3 && g.players.any(::isCpXiaoJiu)) return true
        if (isXiaoJiu(this) && counts[Blue.number] >= 3 && g.players.any(::isCpHanMei)) return true
    }
    return false
}

/**
 * 判断玩家是否赢了，不考虑簒夺者
 */
private fun Player.willWinInternal(
    whoseTurn: Player,
    inFrontOfWhom: Player,
    colors: List<color>,
    partnerTogether: Boolean = true
): Boolean {
    if (!alive) return false
    if (identity != Black) {
        return (if (partnerTogether) isPartnerOrSelf(inFrontOfWhom) else this === inFrontOfWhom)
                && identity in colors && inFrontOfWhom.messageCards.count(identity) >= 2
    } else {
        return when (secretTask) {
            Killer, Pioneer, Sweeper -> {
                if (game!!.players.any {
                        (it!!.identity != Black || it.secretTask in listOf(Collector, Mutator)) &&
                                it.willWinInternal(whoseTurn, inFrontOfWhom, colors)
                    }) {
                    return false
                }
                val counts = inFrontOfWhom.countMessageCard(colors)
                counts[Black.number] >= 3 || return false
                when (secretTask) {
                    Killer -> this === whoseTurn && counts[3] >= 2
                    Pioneer -> this === inFrontOfWhom && counts[3] >= 1
                    Sweeper -> counts[Red.number] <= 1 && counts[Blue.number] <= 1
                    else -> false
                }
            }

            Collector ->
                this === inFrontOfWhom &&
                        if (Red in colors) messageCards.count(Red) >= 2
                        else if (Blue in colors) messageCards.count(Blue) >= 2
                        else false

            Mutator -> {
                if (inFrontOfWhom.let {
                        (it.identity != Black || it.secretTask == Collector) &&
                                it.willWinInternal(whoseTurn, it, colors)
                    }) {
                    return false
                }
                if (Red in colors && inFrontOfWhom.messageCards.count(Red) >= 2) return true
                if (Blue in colors && inFrontOfWhom.messageCards.count(Blue) >= 2) return true
                false
            }

            Disturber ->
                if (Red in colors || Blue in colors)
                    game!!.players.all { it === this || it === inFrontOfWhom || !it!!.alive || it.messageCards.countTrueCard() >= 2 }
                            && inFrontOfWhom.messageCards.countTrueCard() >= 1
                else
                    game!!.players.all { it === this || !it!!.alive || it.messageCards.countTrueCard() >= 2 }

            else -> false
        }
    }
}

/**
 * 计算随机颜色情报牌的平均价值
 *
 * @param whoseTurn 当前回合玩家
 * @param inFrontOfWhom 情报在谁面前
 */
fun Player.calculateMessageCardValue(whoseTurn: Player, inFrontOfWhom: Player, checkThreeSame: Boolean = false) =
    game!!.deck.colorRates.withIndex().sumOf { (i, rate) ->
        val colors =
            if (i % 3 == i / 3) listOf(color.forNumber(i / 3))
            else listOf(color.forNumber(i / 3), color.forNumber(i % 3))
        calculateMessageCardValue(whoseTurn, inFrontOfWhom, colors, checkThreeSame) * rate
    }

/**
 * 计算情报牌的价值
 *
 * @param whoseTurn 当前回合玩家
 * @param inFrontOfWhom 情报在谁面前
 * @param card 情报牌
 */
fun Player.calculateMessageCardValue(
    whoseTurn: Player,
    inFrontOfWhom: Player,
    card: Card,
    checkThreeSame: Boolean = false
) = calculateMessageCardValue(whoseTurn, inFrontOfWhom, card.colors, checkThreeSame)

/**
 * 计算移除一张情报牌的价值
 */
fun Player.calculateRemoveCardValue(whoseTurn: Player, from: Player, card: Card): Int {
    val index = from.messageCards.indexOfFirst { it.id == card.id }
    from.messageCards.removeAt(index)
    return -calculateMessageCardValue(whoseTurn, from, card).apply {
        from.messageCards.add(index, card)
    }
}

/**
 * 计算情报牌的价值
 *
 * @param whoseTurn 当前回合玩家
 * @param inFrontOfWhom 情报在谁面前
 * @param colors 情报牌的颜色
 */
fun Player.calculateMessageCardValue(
    whoseTurn: Player,
    inFrontOfWhom: Player,
    colors: List<color>,
    checkThreeSame: Boolean = false
): Int {
    val disturber = game!!.players.find { it!!.alive && it.identity == Black && it.secretTask == Disturber }
    if (!checkThreeSame) {
        if (whoseTurn.identity == Black && whoseTurn.secretTask == Stealer) {
            if (this === whoseTurn) { // 簒夺者的回合，任何人赢了，簒夺者都会赢
                if (game!!.players.any { it !== disturber && it!!.willWinInternalCp(whoseTurn, inFrontOfWhom, colors) })
                    return 600
            } else { // 簒夺者的回合，任何人赢了，都算作输
                if (game!!.players.any { it !== disturber && it!!.willWinInternalCp(whoseTurn, inFrontOfWhom, colors) })
                    return -600
            }
        } else if (whoseTurn.roleFaceUp && whoseTurn.skills.any { it is BiYiShuangFei }) {
            if (this === whoseTurn) { // 秦圆圆的回合，任何男性角色赢了，秦圆圆都会赢
                if (game!!.players.any {
                        it !== disturber && (isPartnerOrSelf(it!!) || it.isMale)
                                && it.willWinInternalCp(whoseTurn, inFrontOfWhom, colors, false)
                    }) return 600
                if (game!!.players.any {
                        it !== disturber && !(isPartnerOrSelf(it!!) || it.isMale)
                                && it.willWinInternalCp(whoseTurn, inFrontOfWhom, colors, false)
                    }) return -600
            } else if (identity == Black) { // 秦圆圆的回合，神秘人没关系，反正没有队友
                if (game!!.players.any {
                        it !== disturber && !isEnemy(it!!) && it.willWinInternalCp(whoseTurn, inFrontOfWhom, colors)
                    }) return 600
                if (game!!.players.any {
                        it !== disturber && isEnemy(it!!) && it.willWinInternalCp(whoseTurn, inFrontOfWhom, colors)
                    }) return -600
            } else if (inFrontOfWhom.identity in colors && inFrontOfWhom.messageCards.count(inFrontOfWhom.identity) >= 2) {
                return if (inFrontOfWhom === this || isPartner(inFrontOfWhom) &&
                    (!inFrontOfWhom.isMale || isPartnerOrSelf(whoseTurn))
                ) 600 else -600
            }
        } else {
            if (game!!.players.any {
                    it !== disturber && !isEnemy(it!!) && it.willWinInternalCp(whoseTurn, inFrontOfWhom, colors)
                }) return 600
            if (game!!.players.any {
                    it !== disturber && isEnemy(it!!) && it.willWinInternalCp(whoseTurn, inFrontOfWhom, colors)
                }) return -600
        }
    }
    if (disturber != null && disturber.willWinInternal(whoseTurn, inFrontOfWhom, colors))
        return if (disturber === this) 300 else -300
    var value = 0
    if (identity == Black) {
        if (secretTask == Collector && this === inFrontOfWhom) {
            if (Red in colors) {
                value += when (messageCards.count(Red)) {
                    0 -> 10
                    1 -> 100
                    else -> if (checkThreeSame) return 10 else 1000
                }
            }
            if (Blue in colors) {
                value += when (messageCards.count(Blue)) {
                    0 -> 10
                    1 -> 100
                    else -> if (checkThreeSame) return 10 else 1000
                }
            }
        }
        if (secretTask == Mutator && this === inFrontOfWhom) {
            if (Red in colors) {
                value += when (messageCards.count(Red)) {
                    0, 1 -> 6
                    else -> if (checkThreeSame) return 10 else 1000
                }
            }
            if (Blue in colors) {
                value += when (messageCards.count(Blue)) {
                    0, 1 -> 6
                    else -> if (checkThreeSame) return 10 else 1000
                }
            }
        }
        if (secretTask == Disturber && this != inFrontOfWhom) {
            val count = inFrontOfWhom.messageCards.countTrueCard()
            if (inFrontOfWhom.willDie(colors))
                value += (2 - count) * 5
            else if (count < 2 && (Red in colors || Blue in colors))
                value += 5
        }
        if (secretTask !in listOf(Killer, Pioneer, Sweeper)) {
            if (this === inFrontOfWhom && Black in colors) {
                value -= when (inFrontOfWhom.messageCards.count(Black)) {
                    0 -> 1
                    1 -> 11
                    else -> if (checkThreeSame) return 10 else 111
                }
            }
        } else {
            if (Black in colors) {
                val useless = secretTask == Sweeper && inFrontOfWhom.messageCards.countTrueCard() > 1
                value += when (inFrontOfWhom.messageCards.count(Black)) {
                    0 -> if (useless) 0 else 1
                    1 -> if (useless) 0 else 6
                    else -> if (checkThreeSame) return 10 else if (this === inFrontOfWhom) -112 else 0
                }
                if (secretTask == Pioneer && this === inFrontOfWhom)
                    value += 11
            }
        }
    } else {
        val myColor = identity
        val enemyColor = (listOf(Red, Blue) - myColor).first()
        if (inFrontOfWhom.identity == myColor) { // 队友
            if (myColor in colors) {
                value += when (inFrontOfWhom.messageCards.count(myColor)) {
                    0 -> 10
                    1 -> 100
                    else -> if (checkThreeSame) return 10 else 1000
                }
            }
            if (Black in colors) {
                value -= when (inFrontOfWhom.messageCards.count(Black)) {
                    0 -> 1
                    1 -> 11
                    else -> if (checkThreeSame) return 10 else 111
                }
            }
        } else if (inFrontOfWhom.identity == enemyColor) { // 敌人
            if (enemyColor in colors) {
                value -= when (inFrontOfWhom.messageCards.count(enemyColor)) {
                    0 -> 10
                    1 -> 100
                    else -> if (checkThreeSame) return 10 else 1000
                }
            }
            if (Black in colors) {
                value += when (inFrontOfWhom.messageCards.count(Black)) {
                    0 -> 1
                    1 -> 11
                    else -> if (checkThreeSame) return 10 else 111
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
        if (attenuation == 0.0) { // 对于向上的情报，单独处理
            val nextPlayer = currentPlayer
            val nextValue = calculateMessageCardValue(whoseTurn, nextPlayer, card)
            val me = currentPlayer.nextPlayerFunc()
            val myValue = calculateMessageCardValue(whoseTurn, me, card)
            return when {
                canLock && nextValue >= myValue -> nextValue
                nextPlayer.isEnemy(me) -> minOf(nextValue, myValue)
                else -> maxOf(nextValue, myValue)
            }.toDouble()
        }
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
            val (partner, enemy) = game!!.players.filter { it !== this && it!!.alive }.partition { isPartner(it!!) }
            for (target in partner.shuffled() + enemy.shuffled()) {
                val tmpValue = calAveValue(card, 0.0) { if (this === target) this@calSendMessageCard else target!! }
                if (tmpValue > value) {
                    value = tmpValue
                    result = SendMessageCardResult(card, target!!, Up, emptyList(), value)
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
        when (result.dir) {
            Left -> {
                val targets = game!!.sortedFrom(game!!.players.filter { it!!.alive }, location)
                for (i in listOf(0) + ((targets.size - 1) downTo 1)) {
                    val target = targets[i]
                    val v = calculateMessageCardValue(whoseTurn, target, result.card)
                    if (v > maxValue) {
                        maxValue = v
                        lockTarget = target
                    }
                }
            }

            Right -> {
                val targets = game!!.sortedFrom(game!!.players.filter { it!!.alive }, location)
                for (target in targets) {
                    val v = calculateMessageCardValue(whoseTurn, target, result.card)
                    if (v > maxValue) {
                        maxValue = v
                        lockTarget = target
                    }
                }
            }

            else -> {
                for (player in listOf(this, result.target)) {
                    val v = calculateMessageCardValue(whoseTurn, player, result.card)
                    if (v > maxValue) {
                        maxValue = v
                        lockTarget = player
                    }
                }
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
    val value: Int,
    val deltaValue: Int,
    val convertCardSkill: ConvertCardSkill?
)

fun Player.calFightPhase(
    e: FightPhaseIdle,
    whoUse: Player = this,
    availableCards: List<Card> = this.cards
): FightPhaseResult? {
    val order = mutableListOf(Wu_Dao, Jie_Huo, Diao_Bao)
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
    val oldValue = calculateMessageCardValue(e.whoseTurn, e.inFrontOfWhom, e.messageCard)
    var value = oldValue
    var result: FightPhaseResult? = null
    val cards = availableCards.sortCards(identity)
    for (cardType in order) {
        !whoUse.cannotPlayCard(cardType) || continue
        loop@ for (card in cards) {
            val (ok, convertCardSkill) = whoUse.canUseCardTypes(cardType, card, whoUse !== this)
            ok || continue
            when (cardType) {
                Jie_Huo -> {
                    val newValue = calculateMessageCardValue(e.whoseTurn, whoUse, e.messageCard)
                    if (newValue > value) {
                        result = FightPhaseResult(
                            cardType,
                            card,
                            null,
                            newValue,
                            newValue - oldValue,
                            convertCardSkill
                        )
                        value = newValue
                    }
                    break@loop
                }

                Diao_Bao -> {
                    val newValue = calculateMessageCardValue(e.whoseTurn, e.inFrontOfWhom, card)
                    if (newValue > value) {
                        result = FightPhaseResult(
                            cardType,
                            card,
                            null,
                            newValue,
                            newValue - oldValue,
                            convertCardSkill
                        )
                        value = newValue
                    }
                }

                else -> { // Wu_Dao
                    val calLeft = {
                        val left = e.inFrontOfWhom.getNextLeftAlivePlayer()
                        val newValueLeft = calculateMessageCardValue(e.whoseTurn, left, e.messageCard)
                        if (newValueLeft > value) {
                            result = FightPhaseResult(
                                cardType,
                                card,
                                left,
                                newValueLeft,
                                newValueLeft - oldValue,
                                convertCardSkill
                            )
                            value = newValueLeft
                        }
                    }
                    val calRight = {
                        val right = e.inFrontOfWhom.getNextRightAlivePlayer()
                        val newValueRight = calculateMessageCardValue(e.whoseTurn, right, e.messageCard)
                        if (newValueRight > value) {
                            result = FightPhaseResult(
                                cardType,
                                card,
                                right,
                                newValueRight,
                                newValueRight - oldValue,
                                convertCardSkill
                            )
                            value = newValueRight
                        }
                    }
                    if (Random.nextBoolean()) {
                        calLeft()
                        calRight()
                    } else {
                        calRight()
                        calLeft()
                    }
                    break@loop
                }
            }
        }
    }
    return result
}