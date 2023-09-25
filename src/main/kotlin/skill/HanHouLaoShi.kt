package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.OnGiveCard
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 哑巴技能【憨厚老实】：你无法传出纯黑色情报（除非你只能传出纯黑色情报），接收你情报的玩家需给你一张手牌。
 */
class HanHouLaoShi : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.HAN_HOU_LAO_SHI

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        askWhom === fsm.sender || return null
        fsm.sender !== fsm.inFrontOfWhom || return null
        askWhom.findSkill(skillId) != null || return null
        fsm.inFrontOfWhom.cards.isNotEmpty() || return null
        askWhom.getSkillUseCount(skillId) == 0 || return null
        askWhom.addSkillUseCount(skillId)
        return ResolveResult(executeHanHouLaoShi(fsm), true)
    }

    private data class executeHanHouLaoShi(val fsm: ReceivePhaseSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val target = fsm.inFrontOfWhom
            for (p in fsm.sender.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_wait_for_han_hou_lao_shi_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(fsm.sender.location)
                    builder.messagePlayerId = p.getAlternativeLocation(target.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (p === target) {
                        val seq = p.seq
                        builder.seq = seq
                        p.timeout = GameExecutor.post(p.game!!, {
                            if (p.checkSeq(seq)) {
                                val builder2 = skill_han_hou_lao_shi_tos.newBuilder()
                                builder2.cardId = p.cards.first().id
                                builder2.seq = seq
                                p.game!!.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (target is RobotPlayer) {
                GameExecutor.post(target.game!!, {
                    val builder2 = skill_han_hou_lao_shi_tos.newBuilder()
                    builder2.cardId = target.cards.random().id
                    target.game!!.tryContinueResolveProtocol(target, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.inFrontOfWhom) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_han_hou_lao_shi_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = fsm.inFrontOfWhom
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            val card = r.findCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                return null
            }
            val target = fsm.sender
            r.incrSeq()
            log.info("${target}发动了[憨厚老实]，${r}交给${target}一张$card")
            r.deleteCard(card.id)
            target.cards.add(card)
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_han_hou_lao_shi_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(target.location)
                    builder.messagePlayerId = p.getAlternativeLocation(r.location)
                    if (p === r || p === target) builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            return ResolveResult(OnGiveCard(fsm.whoseTurn, r, target, fsm), true)
        }

        companion object {
            private val log = Logger.getLogger(executeHanHouLaoShi::class.java)
        }
    }
}