package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnGiveCard
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 简先生技能【从容应对】：你对一名角色使用的【试探】结算后，或一名角色对你使用的【试探】结算后，你可以抽取该角色的一张手牌，或令你和该角色各摸一张牌。
 */
class CongRongYingDui : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.CONG_RONG_YING_DUI

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? OnFinishResolveCard ?: return null
        fsm.cardType == card_type.Shi_Tan || return null
        askWhom === fsm.player || askWhom === fsm.targetPlayer || return null
        askWhom.findSkill(skillId) != null || return null
        askWhom.getSkillUseCount(skillId) == 0 || return null
        askWhom.addSkillUseCount(skillId)
        val target = if (askWhom === fsm.player) fsm.targetPlayer!! else fsm.player
        val oldAfterResolveFunc = fsm.afterResolveFunc
        val f = {
            askWhom.resetSkillUseCount(skillId)
            oldAfterResolveFunc()
        }
        return ResolveResult(executeCongRongYingDui(fsm.copy(afterResolveFunc = f), askWhom, target), true)
    }

    private data class executeCongRongYingDui(val fsm: OnFinishResolveCard, val r: Player, val target: Player) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = wait_for_skill_cong_rong_ying_dui_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.targetPlayerId = player.getAlternativeLocation(target.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq = player.seq
                        builder.seq = seq
                        player.timeout = GameExecutor.post(r.game!!, {
                            if (player.checkSeq(seq)) {
                                val builder2 = skill_cong_rong_ying_dui_tos.newBuilder()
                                builder2.enable = false
                                builder2.seq = seq
                                r.game!!.tryContinueResolveProtocol(player, builder2.build())
                            }
                        }, player.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val builder2 = skill_cong_rong_ying_dui_tos.newBuilder()
                    builder2.enable = true
                    builder2.drawCard = true
                    r.game!!.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (message !is skill_cong_rong_ying_dui_tos) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                player.incrSeq()
                return ResolveResult(MainPhaseIdle(r), true)
            }
            if (!message.drawCard && target.cards.isEmpty()) {
                log.error("对方没有手牌")
                (player as? HumanPlayer)?.sendErrorMessage("对方没有手牌")
                return null
            }
            player.incrSeq()
            val card = if (!message.drawCard) {
                target.cards.random().also { c ->
                    log.info("${r}发动了[从容应对]，抽取了${target}的$c")
                    target.deleteCard(c.id)
                    player.cards.add(c)
                }
            } else {
                log.info("${r}发动了[从容应对]，选择了双方各摸一张牌")
                null
            }
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_cong_rong_ying_dui_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.enable = message.enable
                    builder.drawCard = message.drawCard
                    if (card != null && (p === r || p === target)) builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            if (message.drawCard) {
                r.game!!.sortedFrom(listOf(r, target), fsm.whoseTurn.location).forEach { it.draw(1) }
                return ResolveResult(fsm, true)
            }
            return ResolveResult(OnGiveCard(fsm.whoseTurn, target, r, fsm), true)
        }

        companion object {
            private val log = Logger.getLogger(executeCongRongYingDui::class.java)
        }
    }
}