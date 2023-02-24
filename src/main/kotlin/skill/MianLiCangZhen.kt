package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseSenderSkill
import com.fengsheng.protos.Common.card
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Role
import com.fengsheng.skill.MianLiCangZhen.executeMianLiCangZhen
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.*

/**
 * 邵秀技能【绵里藏针】：你传出的情报被接收后，可以将一张黑色手牌置入接收者的情报区，然后摸一张牌。
 */
class MianLiCangZhen : AbstractSkill(), TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.MIAN_LI_CANG_ZHEN
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm !is ReceivePhaseSenderSkill || fsm.whoseTurn.findSkill<Skill>(skillId) == null) return null
        if (fsm.whoseTurn.getSkillUseCount(skillId) > 0) return null
        fsm.whoseTurn.addSkillUseCount(skillId)
        return ResolveResult(executeMianLiCangZhen(fsm), true)
    }

    private class executeMianLiCangZhen(fsm: ReceivePhaseSenderSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.whoseTurn.game.players) p.notifyReceivePhase(
                fsm.whoseTurn,
                fsm.inFrontOfWhom,
                fsm.messageCard,
                fsm.whoseTurn,
                15
            )
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.whoseTurn) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message is Fengsheng.end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    log.error("操作太晚了, required Seq: " + player.seq + ", actual Seq: " + message.seq)
                    return null
                }
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message !is Role.skill_mian_li_cang_zhen_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.whoseTurn
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            val card = r!!.findCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                return null
            }
            if (!card.colors.contains(color.Black)) {
                log.error("你选择的不是黑色手牌")
                return null
            }
            val target = fsm.inFrontOfWhom
            if (!target!!.isAlive) {
                log.error("目标已死亡")
                return null
            }
            r.incrSeq()
            log.info(r.toString() + "发动了[绵里藏针]")
            r.deleteCard(card.id)
            target.addMessageCard(card)
            fsm.receiveOrder.addPlayerIfHasThreeBlack(target)
            for (p in r.game.players) {
                (p as? HumanPlayer)?.send(
                    Role.skill_mian_li_cang_zhen_toc.newBuilder().setCard(card.toPbCard())
                        .setPlayerId(p.getAlternativeLocation(r.location()))
                        .setTargetPlayerId(p.getAlternativeLocation(target.location())).build()
                )
            }
            r.draw(1)
            return ResolveResult(fsm, true)
        }

        val fsm: ReceivePhaseSenderSkill

        init {
            this.card = card
            this.sendPhase = sendPhase
            this.r = r
            this.target = target
            this.card = card
            this.wantType = wantType
            this.r = r
            this.target = target
            this.card = card
            this.player = player
            this.card = card
            this.card = card
            this.drawCards = drawCards
            this.players = players
            this.mainPhaseIdle = mainPhaseIdle
            this.dieSkill = dieSkill
            this.player = player
            this.player = player
            this.onUseCard = onUseCard
            this.game = game
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.dir = dir
            this.targetPlayer = targetPlayer
            this.lockedPlayers = lockedPlayers
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.inFrontOfWhom = inFrontOfWhom
            this.player = player
            this.whoseTurn = whoseTurn
            this.diedQueue = diedQueue
            this.afterDieResolve = afterDieResolve
            this.fightPhase = fightPhase
            this.player = player
            this.sendPhase = sendPhase
            this.dieGiveCard = dieGiveCard
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.inFrontOfWhom = inFrontOfWhom
            this.isMessageCardFaceUp = isMessageCardFaceUp
            this.waitForChengQing = waitForChengQing
            this.waitForChengQing = waitForChengQing
            this.whoseTurn = whoseTurn
            this.dyingQueue = dyingQueue
            this.diedQueue = diedQueue
            this.afterDieResolve = afterDieResolve
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.receiveOrder = receiveOrder
            this.inFrontOfWhom = inFrontOfWhom
            this.r = r
            this.fsm = fsm
            this.r = r
            this.playerAndCards = playerAndCards
            this.fsm = fsm
            this.selection = selection
            this.fromPlayer = fromPlayer
            this.waitingPlayer = waitingPlayer
            this.card = card
            this.r = r
            this.r = r
            this.target = target
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.target = target
            this.fsm = fsm
            this.fsm = fsm
            this.target = target
            this.needReturnCount = needReturnCount
            this.fsm = fsm
            this.fsm = fsm
            this.cards = cards
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.target = target
            this.fsm = fsm
            this.r = r
            this.target = target
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.color = color
            this.fsm = fsm
            this.color = color
            this.fsm = fsm
            this.r = r
            this.cards = cards
            this.fsm = fsm
            this.r = r
            this.target = target
            this.card = card
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.r = r
            this.cards = cards
            this.colors = colors
            this.defaultSelection = defaultSelection
            this.fsm = fsm
        }

        companion object {
            private val log = Logger.getLogger(executeMianLiCangZhen::class.java)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeMianLiCangZhen) return false
            val p: Player = fsm0.fsm.whoseTurn
            val target: Player = fsm0.fsm.inFrontOfWhom
            if (p === target || !target.isAlive) return false
            for (card in p.cards.values) {
                if (card.colors.contains(color.Black)) {
                    GameExecutor.Companion.post(
                        p.game,
                        Runnable {
                            p.game.tryContinueResolveProtocol(
                                p,
                                Role.skill_mian_li_cang_zhen_tos.newBuilder().setCardId(card.id).build()
                            )
                        },
                        2,
                        TimeUnit.SECONDS
                    )
                    return true
                }
            }
            return false
        }
    }
}