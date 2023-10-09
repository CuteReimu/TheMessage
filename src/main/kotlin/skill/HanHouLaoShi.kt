package com.fengsheng.skill

import com.fengsheng.Player
import com.fengsheng.card.Card

/**
 * 哑炮技能【憨厚老实】：你的回合，你无法主动传出纯黑色情报（除非你只能传出纯黑色情报）。
 */
class HanHouLaoShi : InitialSkill, SendMessageCardSkill {
    override val skillId = SkillId.HAN_HOU_LAO_SHI

    override fun checkSendCard(player: Player, whoseTurn: Player, availableCards: List<Card>, card: Card) =
        player !== whoseTurn || !card.isPureBlack() || availableCards.all { it.isPureBlack() }
}