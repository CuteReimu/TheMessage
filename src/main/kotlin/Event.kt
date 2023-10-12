package com.fengsheng

import com.fengsheng.card.Card
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.direction
import com.fengsheng.skill.Skill

/**
 * 游戏事件
 */
abstract class Event(val whoseTurn: Player) {
    private val alreadyResolvedSkills = ArrayList<Skill>()
    fun checkResolve(skill: Skill): Boolean {
        if (alreadyResolvedSkills.any { it === skill }) return false
        return alreadyResolvedSkills.add(skill)
    }
}

/**
 * 玩家死亡事件
 */
class PlayerDieEvent(whoseTurn: Player, val whoDie: Player) : Event(whoseTurn)

/**
 * 传递阶段，情报传递到谁面前事件
 */
class MessageMoveNextEvent(
    whoseTurn: Player,
    val messageCard: Card,
    val inFrontOfWhom: Player,
    val isMessageCardFaceUp: Boolean
) : Event(whoseTurn)

/**
 * 回合结束事件
 */
class TurnEndEvent(whoseTurn: Player) : Event(whoseTurn)

/**
 * 传递阶段开始事件
 */
class SendPhaseStartEvent(whoseTurn: Player) : Event(whoseTurn)

/**
 * 选择接收情报事件
 */
class ChooseReceiveCardEvent(whoseTurn: Player, val inFrontOfWhom: Player) : Event(whoseTurn)

/**
 * 弃牌事件
 */
class DiscardCardEvent(whoseTurn: Player, val player: Player) : Event(whoseTurn)

/**
 * 情报被置入谁的情报区
 */
class AddMessageCardEvent(whoseTurn: Player, bySkill: Boolean = true) : Event(whoseTurn)

/**
 * 使用卡牌时事件
 */
class UseCardEvent(val resolveCard: ResolveCard) : Event(resolveCard.whoseTurn)

/**
 * 卡牌结算后事件
 */
class FinishResolveCardEvent(val finishResolveCard: OnFinishResolveCard) : Event(finishResolveCard.whoseTurn)

/**
 * 一名玩家给另一名玩家一张牌事件
 */
class GiveCardEvent(whoseTurn: Player, val fromPlayer: Player, val toPlayer: Player) : Event(whoseTurn)

/**
 * 传出情报时
 */
class SendCardEvent(whoseTurn: Player, val sender: Player, val messageCard: Card, val dir: direction) : Event(whoseTurn)

/**
 * 接收情报时
 */
class ReceiveCardEvent(
    whoseTurn: Player,
    val sender: Player,
    val messageCard: Card,
    val inFrontOfWhom: Player
) : Event(whoseTurn)
