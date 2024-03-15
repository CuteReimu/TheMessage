package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.betterThan
import com.fengsheng.card.Card
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Role.skill_hou_zi_qie_xin_tos
import com.fengsheng.protos.skillHouZiQieXinToc
import com.fengsheng.protos.skillHouZiQieXinTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 小铃铛技能【猴子窃信】：出牌阶段限一次，你可以用手牌和一名其他角色情报区的完全同色的情报互换。
 */
class HouZiQieXin : MainPhaseSkill() {
    override val skillId = SkillId.HOU_ZI_QIE_XIN

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player) =
        super.mainPhaseNeedNotify(r) && r.game!!.players.any {
            it !== r && it!!.alive && it.messageCards.any { card1 ->
                r.cards.any { card2 -> card1.colorExactlyTheSame(card2) }
            }
        }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        val fsm = g.fsm as? MainPhaseIdle
        if (r !== fsm?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            r.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[猴子窃信]一回合只能发动一次")
            r.sendErrorMessage("[猴子窃信]一回合只能发动一次")
            return
        }
        val pb = message as skill_hou_zi_qie_xin_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
            logger.error("目标错误")
            r.sendErrorMessage("目标错误")
            return
        }
        val handCard = r.findCard(message.handCardId)
        if (handCard == null) {
            logger.error("没有这张牌")
            r.sendErrorMessage("目标错误")
            return
        }
        if (message.targetPlayerId == 0) {
            logger.error("不能以自己为目标")
            r.sendErrorMessage("不能以自己为目标")
            return
        }
        val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            r.sendErrorMessage("目标已死亡")
            return
        }
        val messageCard = target.findMessageCard(message.messageCardId)
        if (messageCard == null) {
            logger.error("没有这张情报")
            r.sendErrorMessage("没有这张情报")
            return
        }
        if (!handCard.colorExactlyTheSame(messageCard)) {
            logger.error("选择的两张牌不是完全同色")
            r.sendErrorMessage("选择的两张牌不是完全同色")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}发动了[猴子窃信]，将手牌的${handCard}和${target}情报区的${messageCard}交换")
        r.deleteCard(handCard.id)
        target.deleteMessageCard(messageCard.id)
        r.cards.add(messageCard)
        target.messageCards.add(handCard)
        r.game!!.players.send {
            skillHouZiQieXinToc {
                playerId = it.getAlternativeLocation(r.location)
                this.handCard = handCard.toPbCard()
                targetPlayerId = it.getAlternativeLocation(target.location)
                messageCardId = message.messageCardId
            }
        }
        g.addEvent(AddMessageCardEvent(r))
        g.continueResolve()
    }

    companion object {
        private fun Card.colorExactlyTheSame(card: Card): Boolean {
            if (colors.size != card.colors.size) return false
            val c1 = colors.sortedBy { it.number }
            val c2 = card.colors.sortedBy { it.number }
            c1.forEachIndexed { index, color -> if (color != c2[index]) return false }
            return true
        }

        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseTurn
            player.getSkillUseCount(SkillId.HOU_ZI_QIE_XIN) == 0 || return false
            val playerAndCard = player.game!!.players.flatMap {
                if (it !== player && it!!.alive) {
                    it.messageCards.mapNotNull { card ->
                        if (player.cards.any { c ->
                                card.betterThan(c) && c.colorExactlyTheSame(card)
                            }) PlayerAndCard(it, card)
                        else null
                    }
                } else emptyList()
            }.randomOrNull() ?: return false
            val card = player.cards.filter {
                playerAndCard.card.betterThan(it) && it.colorExactlyTheSame(playerAndCard.card)
            }.randomOrNull() ?: return false
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skillHouZiQieXinTos {
                    handCardId = card.id
                    targetPlayerId = player.getAlternativeLocation(playerAndCard.player.location)
                    messageCardId = playerAndCard.card.id
                })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}