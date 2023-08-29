package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.OnAddMessageCard
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_yi_ya_huan_ya_toc
import com.fengsheng.protos.Role.skill_yi_ya_huan_ya_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 王魁技能【以牙还牙】：你接收黑色情报后，可以将一张黑色手牌置入情报传出者或其相邻角色的情报区，然后摸一张牌。
 */
class YiYaHuanYa : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.YI_YA_HUAN_YA

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        fsm.askWhom === fsm.inFrontOfWhom || return null
        fsm.inFrontOfWhom.findSkill(skillId) != null || return null
        fsm.inFrontOfWhom.getSkillUseCount(skillId) == 0 || return null
        fsm.messageCard.isBlack() || return null
        fsm.inFrontOfWhom.cards.isNotEmpty() || return null
        fsm.inFrontOfWhom.addSkillUseCount(skillId)
        return ResolveResult(executeYiYaHuanYa(fsm), true)
    }

    private data class executeYiYaHuanYa(val fsm: ReceivePhaseSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.whoseTurn.game!!.players)
                p!!.notifyReceivePhase(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, fsm.inFrontOfWhom)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.inFrontOfWhom) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message is end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                    player.sendErrorMessage("操作太晚了")
                    return null
                }
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message !is skill_yi_ya_huan_ya_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = fsm.inFrontOfWhom
            val g = r.game!!
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
            if (!card.colors.contains(color.Black)) {
                log.error("你只能选择黑色手牌")
                (player as? HumanPlayer)?.sendErrorMessage("你只能选择黑色手牌")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            if (target !== fsm.sender && target !== fsm.sender.getNextLeftAlivePlayer() && target !== fsm.sender.getNextRightAlivePlayer()) {
                log.error("你只能选择情报传出者或者其左边或右边的角色作为目标：${message.targetPlayerId}")
                (player as? HumanPlayer)?.sendErrorMessage("你只能选择情报传出者或者其左边或右边的角色作为目标：${message.targetPlayerId}")
                return null
            }
            r.incrSeq()
            log.info("${r}对${target}发动了[以牙还牙]")
            r.deleteCard(card.id)
            target.messageCards.add(card)
            fsm.receiveOrder.addPlayerIfHasThreeBlack(target)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_yi_ya_huan_ya_toc.newBuilder()
                    builder.card = card.toPbCard()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            r.draw(1)
            return ResolveResult(OnAddMessageCard(fsm.whoseTurn, fsm), true)
        }

        companion object {
            private val log = Logger.getLogger(executeYiYaHuanYa::class.java)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeYiYaHuanYa) return false
            val p = fsm0.fsm.inFrontOfWhom
            var target = fsm0.fsm.whoseTurn
            if (p === target) {
                target = if (Random.nextBoolean()) target.getNextLeftAlivePlayer() else target.getNextRightAlivePlayer()
                if (p === target) return false
            }
            val finalTarget = target
            for (card in p.cards) {
                if (card.colors.contains(color.Black)) {
                    GameExecutor.post(p.game!!, {
                        val builder = skill_yi_ya_huan_ya_tos.newBuilder()
                        builder.cardId = card.id
                        builder.targetPlayerId = p.getAlternativeLocation(finalTarget.location)
                        p.game!!.tryContinueResolveProtocol(p, builder.build())
                    }, 2, TimeUnit.SECONDS)
                    return true
                }
            }
            return false
        }
    }
}