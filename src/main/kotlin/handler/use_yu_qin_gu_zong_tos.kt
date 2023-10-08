package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.canSendCard
import org.apache.log4j.Logger

class use_yu_qin_gu_zong_tos : AbstractProtoHandler<Fengsheng.use_yu_qin_gu_zong_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_yu_qin_gu_zong_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            log.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        if (card.type != card_type.Yu_Qin_Gu_Zong) {
            log.error("这张牌不是欲擒故纵，而是$card")
            r.sendErrorMessage("这张牌不是欲擒故纵，而是$card")
            return
        }
        val messageCard = r.findMessageCard(pb.messageCardId)
        if (messageCard == null) {
            log.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        if (Red !in messageCard.colors && Blue !in messageCard.colors) {
            log.error("选择的不是真情报")
            r.sendErrorMessage("选择的不是真情报")
            return
        }
        if (pb.targetPlayerId <= 0 || pb.targetPlayerId >= r.game!!.players.size) {
            log.error("目标错误: ${pb.targetPlayerId}")
            r.sendErrorMessage("遇到了bug，试试把牌取消选择重新选一下")
            return
        }
        val target = r.game!!.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        val lockPlayers = pb.lockPlayerIdList.map {
            if (it <= 0 || it >= r.game!!.players.size) {
                log.error("锁定目标错误: $it")
                r.sendErrorMessage("锁定目标错误: $it")
                return
            }
            r.game!!.players[r.getAbstractLocation(it)]!!
        }
        val sendCardError = r.canSendCard(r, messageCard, null, pb.cardDir, target, lockPlayers)
        if (sendCardError != null) {
            log.error(sendCardError)
            (r as? HumanPlayer)?.sendErrorMessage(sendCardError)
            return
        }
        if (card.canUse(r.game!!, r, messageCard, pb.cardDir, target, lockPlayers)) {
            r.incrSeq()
            card.execute(r.game!!, r, messageCard, pb.cardDir, target, lockPlayers)
        }
    }

    companion object {
        private val log = Logger.getLogger(use_yu_qin_gu_zong_tos::class.java)
    }
}