package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class send_message_card_tos : AbstractProtoHandler<Fengsheng.send_message_card_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.send_message_card_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val fsm = r.game!!.fsm as? SendPhaseStart
        if (r !== fsm?.player) {
            r.game!!.tryContinueResolveProtocol(r, pb)
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            log.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        if (pb.targetPlayerId <= 0 || pb.targetPlayerId >= r.game!!.players.size) {
            log.error("目标错误: ${pb.targetPlayerId}")
            r.sendErrorMessage("遇到了bug，试试把牌取消选择重新选一下")
            return
        }
        if (r.findSkill(SkillId.LIAN_LUO) == null && pb.cardDir != card.direction) {
            log.error("方向错误: ${pb.cardDir}")
            r.sendErrorMessage("方向错误: ${pb.cardDir}")
            return
        }
        var targetLocation = when (pb.cardDir) {
            direction.Left -> r.getNextLeftAlivePlayer().location
            direction.Right -> r.getNextRightAlivePlayer().location
            else -> 0
        }
        if (pb.cardDir != direction.Up && pb.targetPlayerId != r.getAlternativeLocation(targetLocation)) {
            log.error("不能传给那个人: ${pb.targetPlayerId}")
            r.sendErrorMessage("不能传给那个人: ${pb.targetPlayerId}")
            return
        }
        if (card.canLock()) {
            if (pb.lockPlayerIdCount > 1) {
                log.error("最多锁定一个目标")
                r.sendErrorMessage("最多锁定一个目标")
                return
            } else if (pb.lockPlayerIdCount == 1) {
                if (pb.getLockPlayerId(0) < 0 || pb.getLockPlayerId(0) >= r.game!!.players.size) {
                    log.error("锁定目标错误: ${pb.getLockPlayerId(0)}")
                    r.sendErrorMessage("锁定目标错误: ${pb.getLockPlayerId(0)}")
                    return
                } else if (pb.getLockPlayerId(0) == 0) {
                    log.error("不能锁定自己")
                    r.sendErrorMessage("不能锁定自己")
                    return
                }
            }
        } else {
            if (pb.lockPlayerIdCount > 0) {
                log.error("这张情报没有锁定标记")
                r.sendErrorMessage("这张情报没有锁定标记")
                return
            }
        }
        targetLocation = r.getAbstractLocation(pb.targetPlayerId)
        if (!r.game!!.players[targetLocation]!!.alive) {
            log.error("目标已死亡")
            r.sendErrorMessage("目标已死亡")
            return
        }
        val lockPlayers = ArrayList<Player>()
        for (lockPlayerId in pb.lockPlayerIdList) {
            val lockPlayer = r.game!!.players[r.getAbstractLocation(lockPlayerId)]!!
            if (!lockPlayer.alive) {
                log.error("锁定目标已死亡：$lockPlayer")
                r.sendErrorMessage("锁定目标已死亡：$lockPlayer")
                return
            }
            lockPlayers.add(lockPlayer)
        }
        r.incrSeq()
        r.game!!.resolve(
            OnSendCard(
                fsm.player, fsm.player, card, pb.cardDir, r.game!!.players[targetLocation]!!,
                lockPlayers.toTypedArray()
            )
        )
    }

    companion object {
        private val log = Logger.getLogger(send_message_card_tos::class.java)
    }
}