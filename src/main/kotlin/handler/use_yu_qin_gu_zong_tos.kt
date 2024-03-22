package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.card_type.Yu_Qin_Gu_Zong
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.canSendCard
import com.fengsheng.skill.canUseCardTypes
import org.apache.logging.log4j.kotlin.logger

class use_yu_qin_gu_zong_tos : AbstractProtoHandler<Fengsheng.use_yu_qin_gu_zong_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_yu_qin_gu_zong_tos) {
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
        val (ok, convertCardSkill) = r.canUseCardTypes(Yu_Qin_Gu_Zong, card)
        if (!ok) {
            logger.error("这张${card}不能当作欲擒故纵使用")
            r.sendErrorMessage("这张${card}不能当作欲擒故纵使用")
            return
        }
        val messageCard = r.findMessageCard(pb.messageCardId)
        if (messageCard == null) {
            logger.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        if (Red !in messageCard.colors && Blue !in messageCard.colors) {
            logger.error("选择的不是真情报")
            r.sendErrorMessage("选择的不是真情报")
            return
        }
        if (pb.targetPlayerId <= 0 || pb.targetPlayerId >= r.game!!.players.size) {
            logger.error("目标错误: ${pb.targetPlayerId}")
            r.sendErrorMessage("遇到了bug，试试把牌取消选择重新选一下")
            return
        }
        val target = r.game!!.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        val lockPlayers = pb.lockPlayerIdList.map {
            if (it < 0 || it >= r.game!!.players.size) {
                logger.error("锁定目标错误: $it")
                r.sendErrorMessage("锁定目标错误: $it")
                return
            }
            r.game!!.players[r.getAbstractLocation(it)]!!
        }
        val sendCardError = r.canSendCard(r, messageCard, null, pb.cardDir, target, lockPlayers)
        if (sendCardError != null) {
            logger.error(sendCardError)
            r.sendErrorMessage(sendCardError)
            return
        }
        if (card.type != Yu_Qin_Gu_Zong) card = card.asCard(Yu_Qin_Gu_Zong)
        if (card.canUse(r.game!!, r, messageCard, pb.cardDir, target, lockPlayers)) {
            r.incrSeq()
            convertCardSkill?.onConvert(r)
            card.execute(r.game!!, r, messageCard, pb.cardDir, target, lockPlayers)
        }
    }
}
