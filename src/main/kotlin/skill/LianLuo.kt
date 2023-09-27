package com.fengsheng.skill

import com.fengsheng.card.Card
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.direction.*

/**
 * 老鳖技能【联络】：你传递情报时，可以将箭头视为任意方向。
 */
class LianLuo : InitialSkill, SendMessageDirectionSkill {
    override val skillId = SkillId.LIAN_LUO

    override fun checkDir(card: Card, dir: direction): Boolean {
        return dir == Up || dir == Left || dir == Right
    }
}