package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.card_type.Diao_Bao
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.RuBiZhiShi.excuteRuBiZhiShi
import com.fengsheng.skill.canUseCardTypes
import org.apache.logging.log4j.kotlin.logger

class use_diao_bao_tos : AbstractProtoHandler<Fengsheng.use_diao_bao_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_diao_bao_tos) {
        if (!r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (r.game!!.fsm is excuteRuBiZhiShi) {
            r.game!!.tryContinueResolveProtocol(r, pb)
            return
        }
        var card = r.findCard(pb.cardId)
        if (card == null) {
            logger.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        val (ok, convertCardSkill) = r.canUseCardTypes(Diao_Bao, card)
        if (!ok) {
            logger.error("这张${card}不能当作调包使用")
            r.sendErrorMessage("这张${card}不能当作调包使用")
            return
        }
        if (card.type != Diao_Bao) card = card.asCard(Diao_Bao)
        if (card.canUse(r.game!!, r)) {
            r.incrSeq()
            convertCardSkill?.onConvert(r)
            card.execute(r.game!!, r)
        }
    }
}