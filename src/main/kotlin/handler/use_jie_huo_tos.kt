package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.card.Card
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Role.skill_jie_qu_toc
import com.fengsheng.skill.RuBiZhiShi.excuteRuBiZhiShi
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class use_jie_huo_tos : AbstractProtoHandler<Fengsheng.use_jie_huo_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_jie_huo_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (r.game!!.fsm is excuteRuBiZhiShi) {
            r.game!!.tryContinueResolveProtocol(r, pb)
            return
        }
        var card = r.findCard(pb.cardId)
        if (card == null) {
            log.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        var useJieQu = false
        if (card.type != card_type.Jie_Huo) {
            if (r.findSkill(SkillId.JIE_QU) != null) {
                val fsm = r.game!!.fsm as? FightPhaseIdle
                if (fsm == null || fsm.whoseTurn === r) {
                    log.error("[截取]只能在其他玩家的争夺阶段使用")
                    r.sendErrorMessage("[截取]只能在其他玩家的争夺阶段使用")
                    return
                }
                useJieQu = true
            } else {
                log.error("这张牌不是截获，而是$card")
                r.sendErrorMessage("这张牌不是截获，而是$card")
                return
            }
        }
        if (card.type != card_type.Jie_Huo) card = Card.falseCard(card_type.Jie_Huo, card)
        if (card.canUse(r.game!!, r)) {
            r.incrSeq()
            if (useJieQu) {
                r.skills = r.skills.filterNot { it.skillId == SkillId.JIE_QU }.toTypedArray()
                for (p in r.game!!.players) {
                    if (p is HumanPlayer) {
                        val builder = skill_jie_qu_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(r.location)
                        p.send(builder.build())
                    }
                }
            }
            card.execute(r.game!!, r)
        }
    }

    companion object {
        private val log = Logger.getLogger(use_jie_huo_tos::class.java)
    }
}