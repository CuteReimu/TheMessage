package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.count
import com.fengsheng.phase.OnAddMessageCard
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_mian_li_cang_zhen_toc
import com.fengsheng.protos.Role.skill_mian_li_cang_zhen_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 邵秀技能【绵里藏针】：你传出的情报被接收后，可以将一张黑色手牌置入接收者的情报区，然后摸一张牌。
 */
class MianLiCangZhen : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.MIAN_LI_CANG_ZHEN

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        fsm.askWhom == fsm.sender || return null
        fsm.sender.findSkill(skillId) != null || return null
        fsm.sender.cards.isNotEmpty() || return null
        fsm.sender.getSkillUseCount(skillId) == 0 || return null
        fsm.sender.addSkillUseCount(skillId)
        return ResolveResult(executeMianLiCangZhen(fsm), true)
    }

    private data class executeMianLiCangZhen(val fsm: ReceivePhaseSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.sender.game!!.players)
                p!!.notifyReceivePhase(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, fsm.sender, 15)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.sender) {
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
            if (message !is skill_mian_li_cang_zhen_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = fsm.sender
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
                log.error("你选择的不是黑色手牌")
                (player as? HumanPlayer)?.sendErrorMessage("你选择的不是黑色手牌")
                return null
            }
            val target = fsm.inFrontOfWhom
            if (!target.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            r.incrSeq()
            log.info("${r}发动了[绵里藏针]")
            r.deleteCard(card.id)
            target.messageCards.add(card)
            fsm.receiveOrder.addPlayerIfHasThreeBlack(target)
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_mian_li_cang_zhen_toc.newBuilder()
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
            private val log = Logger.getLogger(executeMianLiCangZhen::class.java)
        }
    }

    companion object {
        fun ai(fsm: Fsm): Boolean {
            if (fsm !is executeMianLiCangZhen) return false
            val p = fsm.fsm.sender
            val target = fsm.fsm.inFrontOfWhom
            if (!target.alive) return false
            val cards = p.cards.filter { it.isBlack() }.ifEmpty { return false }
            val c1 = target.identity
            val card =
                if (p.isPartnerOrSelf(target)) {
                    if (c1 == color.Black || p.messageCards.count(color.Black) >= 2) null
                    else cards.find { c1 in it.colors }
                } else cards.find { c1 == color.Black || c1 !in it.colors }
            if (card == null) return false
            GameExecutor.post(
                p.game!!,
                {
                    val builder = skill_mian_li_cang_zhen_tos.newBuilder()
                    builder.cardId = card.id
                    p.game!!.tryContinueResolveProtocol(p, builder.build())
                },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}