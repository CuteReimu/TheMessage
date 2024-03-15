package com.fengsheng.skill

import com.fengsheng.Player
import com.fengsheng.protos.Common.card_type.Diao_Hu_Li_Shan
import com.fengsheng.protos.Common.card_type.Yu_Qin_Gu_Zong
import com.fengsheng.protos.skillHunShuiMoYuToc
import com.fengsheng.send

/**
 * SP阿芙罗拉技能【浑水摸鱼】：整局限一次，你可以将任意手牌作为【欲擒故纵】或者【调虎离山】打出。
 */
class HunShuiMoYu : ConvertCardSkill(null, listOf(Yu_Qin_Gu_Zong, Diao_Hu_Li_Shan), false) {
    override val skillId = SkillId.HUN_SHUI_MO_YU

    override val isInitialSkill = true

    override fun onConvert(r: Player) {
        r.game!!.players.send { skillHunShuiMoYuToc { playerId = it.getAlternativeLocation(r.location) } }
        r.skills = r.skills.filterNot { it === this }
    }
}