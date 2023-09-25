package com.fengsheng.skill

import com.fengsheng.Player
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
    override val skillId = SkillId.CANNOT_PLAY_CARD

    fun cannotPlayCard(cardType: card_type) = forbidAllCard || cardType in this.cardType
}

fun Player.cannotPlayCard(cardType: card_type) =
    skills.any { it is CannotPlayCard && it.cannotPlayCard(cardType) }

fun Player.cannotPlayCardAndSkill() =
    (skills.any { it is CannotPlayCard && it.forbidAllCard } || cards.isEmpty()) // 不能出牌或者没有手牌
            && hasEverFaceUp && !skills.any { it is ActiveSkill && it !is MainPhaseSkill }  // 曾经面朝上过并且没有主动技能
