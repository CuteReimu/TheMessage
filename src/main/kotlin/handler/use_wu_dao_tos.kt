package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.card.Card
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Role
import com.fengsheng.skill.RuBiZhiShi.excuteRuBiZhiShi
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class use_wu_dao_tos : AbstractProtoHandler<Fengsheng.use_wu_dao_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_wu_dao_tos) {
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
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= r.game!!.players.size) {
            log.error("目标错误")
            r.sendErrorMessage("目标错误")
            return
        }
        if (card.type != card_type.Wu_Dao) {
            if (r.findSkill(SkillId.YING_BIAN) != null) {
                if (card.type != card_type.Jie_Huo) {
                    log.error("这张牌不是截获，而是$card")
                    r.sendErrorMessage("这张牌不是截获，而是$card")
                    return
                }
                for (p in r.game!!.players) {
                    if (p is HumanPlayer) {
                        val builder = Role.skill_ying_bian_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(r.location)
                        p.send(builder.build())
                    }
                }
            } else if (r.findSkill(SkillId.ZHENG_DUO) != null) {
                val fsm = r.game!!.fsm as? FightPhaseIdle
                if (fsm == null || fsm.whoseTurn === r) {
                    log.error("[争夺]只能在其他玩家的争夺阶段使用")
                    r.sendErrorMessage("[争夺]只能在其他玩家的争夺阶段使用")
                    return
                }
                r.skills = r.skills.filterNot { it.skillId == SkillId.ZHENG_DUO }.toTypedArray()
                for (p in r.game!!.players) {
                    if (p is HumanPlayer) {
                        val builder = Role.skill_zheng_duo_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(r.location)
                        p.send(builder.build())
                    }
                }
            } else {
                log.error("这张牌不是误导，而是$card")
                r.sendErrorMessage("这张牌不是误导，而是$card")
                return
            }
        }
        val target = r.game!!.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (card.type != card_type.Wu_Dao) card = Card.falseCard(card_type.Wu_Dao, card)
        if (card.canUse(r.game!!, r, target)) {
            r.incrSeq()
            card.execute(r.game!!, r, target)
        }
    }

    companion object {
        private val log = Logger.getLogger(use_wu_dao_tos::class.java)
    }
}