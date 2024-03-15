package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.skill_you_di_shen_ru_tos
import com.fengsheng.protos.skillYouDiShenRuToc
import com.fengsheng.protos.skillYouDiShenRuTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 边云疆技能【诱敌深入】：整局限一次，你的传递阶段，改为将一张手牌作为情报明面传出，该情报含有身份颜色的玩家，在本阶段必须选则接收该情报，不含身份颜色的玩家不能选择接收。
 * （潜伏=红色，特工=蓝色，神秘人不受限）
 */
class YouDiShenRu : ActiveSkill {
    override val skillId = SkillId.YOU_DI_SHEN_RU

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = false

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        message as skill_you_di_shen_ru_tos
        if (r is HumanPlayer && !r.checkSeq(message.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val fsm = g.fsm as? SendPhaseStart
        if (fsm == null) {
            logger.error("[诱敌深入]的使用时机不对")
            r.sendErrorMessage("[诱敌深入]的使用时机不对")
            return
        }
        val card = r.findCard(message.cardId)
        if (card == null) {
            logger.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        if (message.targetPlayerId <= 0 || message.targetPlayerId >= r.game!!.players.size) {
            logger.error("目标错误: ${message.targetPlayerId}")
            r.sendErrorMessage("遇到了bug，试试把牌取消选择重新选一下")
            return
        }
        val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
        val lockPlayers = message.lockPlayerIdList.map {
            if (it < 0 || it >= g.players.size) {
                logger.error("锁定目标错误: $it")
                r.sendErrorMessage("锁定目标错误: $it")
                return
            }
            g.players[r.getAbstractLocation(it)]!!
        }
        val sendCardError = r.canSendCard(r, card, r.cards, message.cardDir, target, lockPlayers)
        if (sendCardError != null) {
            logger.error(sendCardError)
            r.sendErrorMessage(sendCardError)
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        r.skills = r.skills.filterNot { it === this }
        logger.info("${r}发动了[诱敌深入]")
        r.deleteCard(card.id)
        g.players.forEach { it!!.skills += YouDiShenRu2() }
        g.players.send { p ->
            skillYouDiShenRuToc {
                playerId = p.getAlternativeLocation(r.location)
                this.card = card.toPbCard()
                targetPlayerId = p.getAlternativeLocation(target.location)
                lockPlayers.forEach { lockPlayerIds.add(p.getAlternativeLocation(it.location)) }
                cardDir = message.cardDir
            }
        }
        g.resolve(
            OnSendCard(
                fsm.whoseTurn, fsm.whoseTurn, card, message.cardDir, target,
                lockPlayers, isMessageCardFaceUp = true, needRemoveCard = false, needNotify = false
            )
        )
    }

    private class YouDiShenRu2 : MustReceiveMessage() {
        override val isInitialSkill = false

        override fun mustReceive(sendPhase: SendPhaseIdle) =
            sendPhase.inFrontOfWhom.identity.let { it != Black && it in sendPhase.messageCard.colors }

        override fun cannotReceive(sendPhase: SendPhaseIdle) =
            sendPhase.inFrontOfWhom.identity.let { it != Black && it !in sendPhase.messageCard.colors }
    }

    companion object {
        fun ai(e: SendPhaseStart, skill: ActiveSkill): Boolean {
            val player = e.whoseTurn
            val game = player.game!!
            val result = player.calSendMessageCard()
            GameExecutor.post(game, {
                skill.executeProtocol(game, player, skillYouDiShenRuTos {
                    cardId = result.card.id
                    targetPlayerId = player.getAlternativeLocation(result.target.location)
                    cardDir = result.dir
                    result.lockedPlayers.forEach { lockPlayerId.add(player.getAlternativeLocation(it.location)) }
                })
            }, 1, TimeUnit.SECONDS)
            return true
        }
    }
}