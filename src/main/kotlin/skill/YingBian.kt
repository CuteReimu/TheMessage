package com.fengsheng.skill

import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.protos.Common.card_type.Jie_Huo
import com.fengsheng.protos.Common.card_type.Wu_Dao
import com.fengsheng.protos.Role.skill_ying_bian_toc

/**
 * SP李宁玉技能【应变】：你的【截获】可以当做【误导】使用。
 */
class YingBian : ConvertCardSkill(Jie_Huo, listOf(Wu_Dao), false) {
    override val skillId = SkillId.YING_BIAN

    override val isInitialSkill = true

    override fun onConvert(r: Player) {
        for (p in r.game!!.players) {
            if (p is HumanPlayer) {
                val builder = skill_ying_bian_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                p.send(builder.build())
            }
        }
    }
}