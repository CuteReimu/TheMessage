package com.fengsheng.skill

import com.fengsheng.Player
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.card_type

/**
 * 本回合不能出牌
 *
 * @param cardType 被禁用的卡牌列表
 * @param forbidAllCard 是否禁用所有牌
 */
class CannotPlayCard(
    private val cardType: List<card_type> = emptyList(),
    val forbidAllCard: Boolean = false
) : OneTurnSkill {
    override val skillId = SkillId.UNKNOWN

    fun cannotPlayCard(cardType: card_type) = forbidAllCard || cardType in this.cardType
}

fun Player.cannotPlayCard(cardType: card_type) =
    skills.any { it is CannotPlayCard && it.cannotPlayCard(cardType) }

fun Player.cannotPlayCardAndSkillForFightPhase(fightPhase: FightPhaseIdle) =
    (skills.any { it is CannotPlayCard && it.forbidAllCard } || cards.isEmpty()) // 不能出牌或者没有手牌
            && (!skills.any { it is InitialSkill || it is ActiveSkill } // 没有初始技能，说明被禁技能了（考虑到可能新获得主动技能，需要排除一下主动技能）
            || hasEverFaceUp && !skills.any {
        it is ActiveSkill && it.canUse(fightPhase, this)
    }) // 曾经面朝上过并且没有争夺阶段可以用的主动技能

fun Player.cannotPlayCardAndSkillForChengQing() =
    (skills.any { it is CannotPlayCard && it.forbidAllCard } || cards.isEmpty()) // 不能出牌或者没有手牌
            && (!skills.any { it is InitialSkill || it is ActiveSkill } // 没有初始技能，说明被禁技能了（考虑到可能新获得主动技能，需要排除一下主动技能）
            || hasEverFaceUp && !skills.any { it is ActiveSkill && it !is MainPhaseSkill }) // 曾经面朝上过并且没有主动技能
