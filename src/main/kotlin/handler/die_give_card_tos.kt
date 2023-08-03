package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.card.Card
import com.fengsheng.phase.AfterDieGiveCard
import com.fengsheng.phase.WaitForDieGiveCard
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class die_give_card_tos : AbstractProtoHandler<Fengsheng.die_give_card_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.die_give_card_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val fsm = r.game!!.fsm as? WaitForDieGiveCard
        if (r !== fsm?.diedQueue?.get(fsm.diedIndex)) {
            log.error("你没有死亡")
            r.sendErrorMessage("你没有死亡")
            return
        }
        if (pb.targetPlayerId == 0) {
            r.incrSeq()
            r.game!!.resolve(AfterDieGiveCard(fsm))
            return
        } else if (pb.targetPlayerId < 0 || pb.targetPlayerId >= r.game!!.players.size) {
            log.error("目标错误: ${pb.targetPlayerId}")
            r.sendErrorMessage("目标错误: ${pb.targetPlayerId}")
            return
        }
        if (HashSet(pb.cardIdList).size != pb.cardIdList.size) {
            log.error("卡牌重复${pb.cardIdList.toTypedArray().contentToString()}")
            r.sendErrorMessage("卡牌重复${pb.cardIdList.toTypedArray().contentToString()}")
            return
        }
        if (pb.cardIdCount == 0) {
            log.warn("参数似乎有些不对，姑且认为不给牌吧")
            r.sendErrorMessage("参数似乎有些不对，姑且认为不给牌吧")
            r.incrSeq()
            r.game!!.resolve(AfterDieGiveCard(fsm))
            return
        }
        val cards = ArrayList<Card>()
        for (cardId in pb.cardIdList) {
            val card = r.findCard(cardId)
            if (card == null) {
                log.error("没有这张牌")
                r.sendErrorMessage("没有这张牌")
                return
            }
            cards.add(card)
        }
        for (card in cards) r.deleteCard(card.id)
        val target = r.game!!.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            log.error("目标已死亡")
            r.sendErrorMessage("目标已死亡")
            return
        }
        target.cards.addAll(cards)
        log.info("${r}给了${target}$${cards.toTypedArray().contentToString()}")
        for (p in r.game!!.players) {
            if (p is HumanPlayer) {
                val builder = Fengsheng.notify_die_give_card_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                if (p === r || p === target)
                    for (card in cards) builder.addCard(card.toPbCard())
                else
                    builder.unknownCardCount = cards.size
                p.send(builder.build())
            }
        }
        r.incrSeq()
        r.game!!.resolve(AfterDieGiveCard(fsm))
    }

    companion object {
        private val log = Logger.getLogger(die_give_card_tos::class.java)
    }
}