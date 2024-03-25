package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.card_type.Jie_Huo
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.RuBiZhiShi.ExecuteRuBiZhiShi
import com.fengsheng.skill.canUseCardTypes
import org.apache.logging.log4j.kotlin.logger

class UseJieHuoTos : AbstractProtoHandler<Fengsheng.use_jie_huo_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_jie_huo_tos) {
        if (!r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (r.game!!.fsm is ExecuteRuBiZhiShi) {
            r.game!!.tryContinueResolveProtocol(r, pb)
            return
        }
        var card = r.findCard(pb.cardId)
        if (card == null) {
            logger.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        val (ok, convertCardSkill) = r.canUseCardTypes(Jie_Huo, card)
        if (!ok) {
            logger.error("这张${card}不能当作截获使用")
            r.sendErrorMessage("这张${card}不能当作截获使用")
            return
        }
        if (card.type != Jie_Huo) card = card.asCard(Jie_Huo)
        if (card.canUse(r.game!!, r)) {
            r.incrSeq()
            convertCardSkill?.onConvert(r)
            card.execute(r.game!!, r)
        }
    }
}
