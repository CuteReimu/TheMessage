package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseReceiverSkillimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Fengshengimport com.fengsheng.protos.Roleimport com.fengsheng.skill.JinShen.executeJinShenimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 金生火技能【谨慎】：你接收双色情报后，可以用一张手牌与该情报面朝上互换。
 */
class JinShen : AbstractSkill(), TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.JIN_SHEN
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm !is ReceivePhaseReceiverSkill || fsm.inFrontOfWhom.findSkill<Skill>(skillId) == null) return null
        if (fsm.inFrontOfWhom.getSkillUseCount(skillId) > 0) return null
        if (fsm.messageCard.getColors().size < 2) return null
        fsm.inFrontOfWhom.addSkillUseCount(skillId)
        return ResolveResult(executeJinShen(fsm), true)
    }

    private class executeJinShen(fsm: ReceivePhaseReceiverSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.whoseTurn.game.players) p.notifyReceivePhase(
                fsm.whoseTurn,
                fsm.inFrontOfWhom,
                fsm.messageCard,
                fsm.inFrontOfWhom,
                15
            )
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.inFrontOfWhom) {
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
            if (message !is Role.skill_jin_shen_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.inFrontOfWhom
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            val card = r.findCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                return null
            }
            r.incrSeq()
            log.info(r.toString() + "发动了[谨慎]")
            val messageCard = fsm.messageCard
            r.deleteCard(card.id)
            r.deleteMessageCard(messageCard.id)
            fsm.receiveOrder.removePlayerIfNotHaveThreeBlack(r)
            r.addMessageCard(card)
            fsm.receiveOrder.addPlayerIfHasThreeBlack(r)
            r.addCard(messageCard)
            for (p in g.players) {
                (p as? HumanPlayer)?.send(
                    Role.skill_jin_shen_toc.newBuilder().setCard(card.toPbCard())
                        .setPlayerId(p.getAlternativeLocation(r.location())).build()
                )
            }
            return ResolveResult(fsm, true)
        }

        val fsm: ReceivePhaseReceiverSkill

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
        }

        companion object {
            private val log = Logger.getLogger(executeJinShen::class.java)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeJinShen) return false
            val p: Player = fsm0.fsm.inFrontOfWhom
            for (card in p.cards.values) {
                if (!card.colors.contains(color.Black)) {
                    GameExecutor.Companion.post(
                        p.game,
                        Runnable {
                            p.game.tryContinueResolveProtocol(
                                p,
                                Role.skill_jin_shen_tos.newBuilder().setCardId(card.id).build()
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