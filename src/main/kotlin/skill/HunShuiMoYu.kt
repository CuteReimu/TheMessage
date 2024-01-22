package com.fengsheng.skill

import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.protos.Common.card_type.Diao_Hu_Li_Shan
import com.fengsheng.protos.Common.card_type.Yu_Qin_Gu_Zong
import com.fengsheng.protos.Role.skill_hun_shui_mo_yu_toc

/**
 * SP阿芙罗拉技能【浑水摸鱼】：整局限一次，你可以将任意手牌作为【欲擒故纵】或者【调虎离山】打出。
 */
class HunShuiMoYu : ConvertCardSkill(null, listOf(Yu_Qin_Gu_Zong, Diao_Hu_Li_Shan), false) {
    override val skillId = SkillId.HUN_SHUI_MO_YU

    override val isInitialSkill = true

    override fun onConvert(r: Player) {
        for (p in r.game!!.players) {
            if (p is HumanPlayer) {
                val builder = skill_hun_shui_mo_yu_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                p.send(builder.build())
            }
        }
        r.skills = r.skills.filterNot { it === this }
    }
}