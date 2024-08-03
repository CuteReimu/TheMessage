package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Role.skill_cong_rong_ying_dui_tos
import com.fengsheng.protos.skillCongRongYingDuiToc
import com.fengsheng.protos.skillCongRongYingDuiTos
import com.fengsheng.protos.waitForSkillCongRongYingDuiToc
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 简先生技能【从容应对】：你对一名角色使用的【试探】结算后，或一名角色对你使用的【试探】结算后，你可以抽取该角色的一张手牌，或令你和该角色各摸一张牌。
 */
class CongRongYingDui : TriggeredSkill {
    override val skillId = SkillId.CONG_RONG_YING_DUI

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<FinishResolveCardEvent>(this) { event ->
            event.cardType == card_type.Shi_Tan || return@findEvent false
            askWhom === event.player || askWhom === event.targetPlayer
        } ?: return null
        val target = if (askWhom === event.player) event.targetPlayer!! else event.player
        return ResolveResult(ExecuteCongRongYingDui(g.fsm!!, event, askWhom, target), true)
    }

    private data class ExecuteCongRongYingDui(
        val fsm: Fsm,
        val event: FinishResolveCardEvent,
        val r: Player,
        val target: Player
    ) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            r.game!!.players.send { player ->
                waitForSkillCongRongYingDuiToc {
                    playerId = player.getAlternativeLocation(r.location)
                    targetPlayerId = player.getAlternativeLocation(target.location)
                    waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq = player.seq
                        this.seq = seq
                        player.timeout = GameExecutor.post(r.game!!, {
                            if (player.checkSeq(seq)) {
                                r.game!!.tryContinueResolveProtocol(player, skillCongRongYingDuiTos {
                                    enable = false
                                    this.seq = seq
                                })
                            }
                        }, player.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    r.game!!.tryContinueResolveProtocol(r, skillCongRongYingDuiTos {
                        enable = true
                        drawCard = target.cards.isEmpty() || target.isPartnerOrSelf(r)
                    })
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (message !is skill_cong_rong_ying_dui_tos) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (!message.drawCard && target.cards.isEmpty()) {
                logger.error("对方没有手牌")
                player.sendErrorMessage("对方没有手牌")
                return null
            }
            player.incrSeq()
            val card = if (!message.drawCard) {
                target.cards.random().also { c ->
                    logger.info("${r}发动了[从容应对]，抽取了${target}的$c")
                    target.deleteCard(c.id)
                    player.cards.add(c)
                    r.game!!.addEvent(GiveCardEvent(event.whoseTurn, target, r))
                }
            } else {
                logger.info("${r}发动了[从容应对]，选择了双方各摸一张牌")
                null
            }
            r.game!!.players.send { p ->
                skillCongRongYingDuiToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    enable = message.enable
                    drawCard = message.drawCard
                    if (p === r || p === target) card?.let { this.card = it.toPbCard() }
                }
            }
            if (message.drawCard)
                r.game!!.sortedFrom(listOf(r, target), event.whoseTurn.location).forEach { it.draw(1) }
            return ResolveResult(fsm, true)
        }
    }
}
