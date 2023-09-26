package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.skill_you_di_shen_ru_toc
import com.fengsheng.protos.Role.skill_you_di_shen_ru_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger

/**
 * 军人技能【诱敌深入】：整局限一次，你的传递阶段，改为将一张手牌作为情报明面传出，该情报含有身份颜色的玩家，在本阶段必须选则接收该情报，不含身份颜色的玩家不能选择接收。
 * （潜伏=红色，特工=蓝色，神秘人不受限）
 */
class YouDiShenRu : InitialSkill, ActiveSkill {
    override val skillId = SkillId.YOU_DI_SHEN_RU

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        message as skill_you_di_shen_ru_tos
        if (r is HumanPlayer && !r.checkSeq(message.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val fsm = g.fsm as? SendPhaseStart
        if (fsm == null) {
            log.error("[诱敌深入]的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("[诱敌深入]的使用时机不对")
            return
        }
        val card = r.findCard(message.cardId)
        if (card == null) {
            log.error("没有这张牌")
            (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
            return
        }
        if (r.findSkill(SkillId.HAN_HOU_LAO_SHI) != null) {
            if (card.isPureBlack() && !r.cards.all { it.isPureBlack() }) {
                log.error("你无法传出纯黑色情报：$card")
                (r as? HumanPlayer)?.sendErrorMessage("你无法传出纯黑色情报")
                return
            }
        }
        if (message.targetPlayerId <= 0 || message.targetPlayerId >= r.game!!.players.size) {
            log.error("目标错误: ${message.targetPlayerId}")
            (r as? HumanPlayer)?.sendErrorMessage("遇到了bug，试试把牌取消选择重新选一下")
            return
        }
        if (r.findSkill(SkillId.LIAN_LUO) == null && message.cardDir != card.direction) {
            log.error("方向错误: ${message.cardDir}")
            (r as? HumanPlayer)?.sendErrorMessage("方向错误: ${message.cardDir}")
            return
        }
        var targetLocation = when (message.cardDir) {
            Common.direction.Left -> r.getNextLeftAlivePlayer().location
            Common.direction.Right -> r.getNextRightAlivePlayer().location
            else -> 0
        }
        if (message.cardDir != Common.direction.Up && message.targetPlayerId != r.getAlternativeLocation(targetLocation)) {
            log.error("不能传给那个人: ${message.targetPlayerId}")
            (r as? HumanPlayer)?.sendErrorMessage("不能传给那个人: ${message.targetPlayerId}")
            return
        }
        if (message.lockPlayerIdList.toSet().size != message.lockPlayerIdCount) {
            log.error("锁定目标重复")
            (r as? HumanPlayer)?.sendErrorMessage("锁定目标重复")
            return
        }
        if (card.canLock()) {
            if (message.lockPlayerIdCount > 1) {
                log.error("最多锁定一个目标")
                (r as? HumanPlayer)?.sendErrorMessage("最多锁定一个目标")
                return
            } else if (message.lockPlayerIdCount == 1) {
                if (message.getLockPlayerId(0) < 0 || message.getLockPlayerId(0) >= r.game!!.players.size) {
                    log.error("锁定目标错误: ${message.getLockPlayerId(0)}")
                    (r as? HumanPlayer)?.sendErrorMessage("锁定目标错误: ${message.getLockPlayerId(0)}")
                    return
                } else if (message.getLockPlayerId(0) == 0) {
                    log.error("不能锁定自己")
                    (r as? HumanPlayer)?.sendErrorMessage("不能锁定自己")
                    return
                }
            }
        } else {
            if (message.lockPlayerIdCount > 0) {
                log.error("这张情报没有锁定标记")
                (r as? HumanPlayer)?.sendErrorMessage("这张情报没有锁定标记")
                return
            }
        }
        targetLocation = r.getAbstractLocation(message.targetPlayerId)
        if (!r.game!!.players[targetLocation]!!.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        val lockPlayers = ArrayList<Player>()
        for (lockPlayerId in message.lockPlayerIdList) {
            val lockPlayer = r.game!!.players[r.getAbstractLocation(lockPlayerId)]!!
            if (!lockPlayer.alive) {
                log.error("锁定目标已死亡：$lockPlayer")
                (r as? HumanPlayer)?.sendErrorMessage("锁定目标已死亡：$lockPlayer")
                return
            }
            lockPlayers.add(lockPlayer)
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        r.skills = r.skills.filterNot { it is YouDiShenRu }
        log.info("${r}发动了[诱敌深入]")
        r.deleteCard(card.id)
        g.players.forEach { it!!.skills += YouDiShenRu2() }
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_you_di_shen_ru_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.card = card.toPbCard()
                builder.targetPlayerId = p.getAlternativeLocation(targetLocation)
                lockPlayers.forEach { builder.addLockPlayerIds(p.getAlternativeLocation(it.location)) }
                builder.cardDir = message.cardDir
                p.send(builder.build())
            }
        }
        g.resolve(
            OnSendCard(
                fsm.player, fsm.player, card, message.cardDir, g.players[targetLocation]!!,
                lockPlayers.toTypedArray(), isMessageCardFaceUp = true, needRemoveCardAndNotify = false
            )
        )
    }

    private class YouDiShenRu2 : MustReceiveMessage() {
        override fun mustReceive(sendPhase: SendPhaseIdle) =
            sendPhase.inFrontOfWhom.identity.let { it != Black && it in sendPhase.messageCard.colors }

        override fun cannotReceive(sendPhase: SendPhaseIdle) =
            sendPhase.inFrontOfWhom.identity.let { it != Black && it !in sendPhase.messageCard.colors }
    }

    companion object {
        private val log = Logger.getLogger(YouDiShenRu::class.java)
    }
}