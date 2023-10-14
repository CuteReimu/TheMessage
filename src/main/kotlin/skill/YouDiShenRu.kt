package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.direction.*
import com.fengsheng.protos.Role.skill_you_di_shen_ru_toc
import com.fengsheng.protos.Role.skill_you_di_shen_ru_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 边云疆技能【诱敌深入】：整局限一次，你的传递阶段，改为将一张手牌作为情报明面传出，该情报含有身份颜色的玩家，在本阶段必须选则接收该情报，不含身份颜色的玩家不能选择接收。
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
        if (message.targetPlayerId <= 0 || message.targetPlayerId >= r.game!!.players.size) {
            log.error("目标错误: ${message.targetPlayerId}")
            (r as? HumanPlayer)?.sendErrorMessage("遇到了bug，试试把牌取消选择重新选一下")
            return
        }
        val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
        val lockPlayers = message.lockPlayerIdList.map {
            if (it < 0 || it >= g.players.size) {
                log.error("锁定目标错误: $it")
                (r as? HumanPlayer)?.sendErrorMessage("锁定目标错误: $it")
                return
            }
            g.players[r.getAbstractLocation(it)]!!
        }
        val sendCardError = r.canSendCard(r, card, r.cards, message.cardDir, target, lockPlayers)
        if (sendCardError != null) {
            log.error(sendCardError)
            (r as? HumanPlayer)?.sendErrorMessage(sendCardError)
            return
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
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                lockPlayers.forEach { builder.addLockPlayerIds(p.getAlternativeLocation(it.location)) }
                builder.cardDir = message.cardDir
                p.send(builder.build())
            }
        }
        g.resolve(
            OnSendCard(
                fsm.whoseTurn, fsm.whoseTurn, card, message.cardDir, target,
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

        fun ai(e: SendPhaseStart, skill: ActiveSkill): Boolean {
            val player = e.whoseTurn
            val game = player.game!!
            val messageCard = player.cards.filter { it.direction != Up }.randomOrNull() ?: return false
            val direction = messageCard.direction
            val target = when (direction) {
                Left -> player.getNextLeftAlivePlayer().let { if (it !== player) it else null }
                Right -> player.getNextRightAlivePlayer().let { if (it !== player) it else null }
                else -> null
            } ?: return false
            GameExecutor.post(game, {
                val builder = skill_you_di_shen_ru_tos.newBuilder()
                builder.cardId = messageCard.id
                builder.targetPlayerId = player.getAlternativeLocation(target.location)
                builder.cardDir = direction
                skill.executeProtocol(game, player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}