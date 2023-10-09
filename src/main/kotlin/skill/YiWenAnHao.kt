package com.fengsheng.skill

import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.protos.Common.card_type.Diao_Bao
import com.fengsheng.protos.Common.card_type.Po_Yi
import com.fengsheng.protos.Role.skill_yi_wen_an_hao_toc

/**
 * 陈安娜技能【译文暗号】：你可以将【破译】作为【调包】面朝下打出。
 */
class YiWenAnHao : InitialSkill, ConvertCardSkill(Po_Yi, Diao_Bao, false) {
    override val skillId = SkillId.YI_WEN_AN_HAO

    override fun onConvert(r: Player) {
        for (p in r.game!!.players) {
            if (p is HumanPlayer) {
                val builder = skill_yi_wen_an_hao_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                p.send(builder.build())
            }
        }
    }
}