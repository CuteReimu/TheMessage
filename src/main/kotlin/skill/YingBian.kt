package com.fengsheng.skill

import com.fengsheng.Player
import com.fengsheng.protos.Common.card_type.Jie_Huo
import com.fengsheng.protos.Common.card_type.Wu_Dao
import com.fengsheng.protos.skillYingBianToc
import com.fengsheng.send

/**
 * SP李宁玉技能【应变】：你的【截获】可以当做【误导】使用。
 */
class YingBian : ConvertCardSkill(Jie_Huo, listOf(Wu_Dao), false) {
    override val skillId = SkillId.YING_BIAN

    override val isInitialSkill = true

    override fun onConvert(r: Player) {
        r.game!!.players.send { skillYingBianToc { playerId = it.getAlternativeLocation(r.location) } }
    }
}