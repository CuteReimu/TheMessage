package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.canSendCard
import org.apache.logging.log4j.kotlin.logger

class SendMessageCardTos : AbstractProtoHandler<Fengsheng.send_message_card_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.send_message_card_tos) {
        if (!r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val fsm = r.game!!.fsm as? SendPhaseStart
        if (r !== fsm?.whoseTurn) {
            r.game!!.tryContinueResolveProtocol(r, pb)
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            logger.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
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
        val sendCardError = r.canSendCard(fsm.whoseTurn, card, r.cards, pb.cardDir, target, lockPlayers)
        if (sendCardError != null) {
            logger.error(sendCardError)
            r.sendErrorMessage(sendCardError)
            return
        }
        r.incrSeq()
        r.game!!.resolve(OnSendCard(fsm.whoseTurn, fsm.whoseTurn, card, pb.cardDir, target, lockPlayers))
    }
}
