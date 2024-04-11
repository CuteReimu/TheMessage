package com.fengsheng

import com.fengsheng.card.Card
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ReceivePhaseIdle
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Common.direction
import com.fengsheng.skill.Skill

/**
 * 游戏事件
 */
abstract class Event {
    abstract val whoseTurn: Player

    private val alreadyResolvedSkills = ArrayList<Skill>()
    fun checkResolve(skill: Skill): Boolean {
        if (alreadyResolvedSkills.any { it === skill }) return false
        return alreadyResolvedSkills.add(skill)
    }
}

/**
 * 玩家死亡事件
 */
class PlayerDieEvent(override val whoseTurn: Player, val whoDie: Player) : Event()

/**
 * 传递阶段，情报传递到谁面前事件
 */
class MessageMoveNextEvent(
    override val whoseTurn: Player,
    val messageCard: Card,
    val inFrontOfWhom: Player,
    val isMessageCardFaceUp: Boolean
) : Event()

/**
 * 回合结束事件
 */
class TurnEndEvent(override val whoseTurn: Player) : Event()

/**
 * 传递阶段开始事件
 */
class SendPhaseStartEvent(override val whoseTurn: Player) : Event()

/**
 * 选择接收情报事件
 */
class ChooseReceiveCardEvent(
    override val whoseTurn: Player,
    val inFrontOfWhom: Player,
    val messageCard: Card,
    val sender: Player
) : Event()

/**
 * 弃牌事件
 */
class DiscardCardEvent(override val whoseTurn: Player, val player: Player) : Event()

/**
 * 情报被置入谁的情报区
 */
class AddMessageCardEvent(override val whoseTurn: Player, val bySkill: Boolean = true) : Event()

/**
 * 使用卡牌时事件
 */
class UseCardEvent(private val resolveCard: ResolveCard) : Event() {
    override val whoseTurn: Player
        get() = resolveCard.whoseTurn

    val player: Player
        get() = resolveCard.player

    val targetPlayer: Player?
        get() = resolveCard.targetPlayer

    val card: Card?
        get() = resolveCard.card

    val cardType: card_type
        get() = resolveCard.cardType

    val currentFsm: ProcessFsm
        get() = resolveCard.currentFsm

    var valid: Boolean
        get() = resolveCard.valid
        set(value) {
            resolveCard.valid = value
        }
}

/**
 * 卡牌结算后事件
 */
class FinishResolveCardEvent(private val finishResolveCard: OnFinishResolveCard) : Event() {
    override val whoseTurn: Player
        get() = finishResolveCard.whoseTurn

    val player: Player
        get() = finishResolveCard.player

    val targetPlayer: Player?
        get() = finishResolveCard.targetPlayer

    val card: Card?
        get() = finishResolveCard.card

    val cardType: card_type
        get() = finishResolveCard.cardType

    val nextFsm: Fsm
        get() = finishResolveCard.nextFsm

    var discardAfterResolve: Boolean
        get() = finishResolveCard.discardAfterResolve
        set(value) {
            finishResolveCard.discardAfterResolve = value
        }
}

/**
 * 一名玩家给另一名玩家一张牌事件（可以自己给自己牌）
 */
class GiveCardEvent(override val whoseTurn: Player, val fromPlayer: Player, val toPlayer: Player) : Event()

/**
 * 传出情报时
 */
class SendCardEvent(
    override val whoseTurn: Player,
    val sender: Player,
    val messageCard: Card,
    val targetPlayer: Player,
    val dir: direction
) : Event()

/**
 * 接收情报时
 */
class ReceiveCardEvent(private val receivePhase: ReceivePhaseIdle) : Event() {
    override val whoseTurn: Player
        get() = receivePhase.whoseTurn

    val sender: Player
        get() = receivePhase.sender

    val inFrontOfWhom: Player
        get() = receivePhase.inFrontOfWhom

    var messageCard: Card
        get() = receivePhase.messageCard
        set(value) {
            receivePhase.messageCard = value
        }
}
