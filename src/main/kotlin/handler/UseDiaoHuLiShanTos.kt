package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.card_type.Diao_Hu_Li_Shan
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.canUseCardTypes
import org.apache.logging.log4j.kotlin.logger

class UseDiaoHuLiShanTos : AbstractProtoHandler<Fengsheng.use_diao_hu_li_shan_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_diao_hu_li_shan_tos) {
        if (!r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        var card = r.findCard(pb.cardId)
        if (card == null) {
            logger.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        val (ok, convertCardSkill) = r.canUseCardTypes(Diao_Hu_Li_Shan, card)
        if (!ok) {
            logger.error("这张${card}不能当作调虎离山使用")
            r.sendErrorMessage("这张${card}不能当作调虎离山使用")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= r.game!!.players.size) {
            logger.error("目标错误: ${pb.targetPlayerId}")
            r.sendErrorMessage("目标错误: ${pb.targetPlayerId}")
            return
        }
        val target = r.game!!.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (card.type != Diao_Hu_Li_Shan) card = card.asCard(Diao_Hu_Li_Shan)
        if (card.canUse(r.game!!, r, target, pb.isSkill)) {
            r.incrSeq()
            convertCardSkill?.onConvert(r)
            card.execute(r.game!!, r, target, pb.isSkill)
        }
    }
}
