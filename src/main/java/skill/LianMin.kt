package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseSenderSkillimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Fengshengimport com.fengsheng.protos.Roleimport com.fengsheng.skill.LianMin.executeLianMinimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 白菲菲技能【怜悯】：你传出的非黑色情报被接收后，可以从你或接收者的情报区选择一张黑色情报加入你的手牌。
 */
class LianMin : AbstractSkill(), TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.LIAN_MIN
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm !is ReceivePhaseSenderSkill || fsm.whoseTurn.findSkill<Skill>(skillId) == null) return null
        if (fsm.messageCard.getColors().contains(color.Black)) return null
        if (fsm.whoseTurn.getSkillUseCount(skillId) > 0) return null
        fsm.whoseTurn.addSkillUseCount(skillId)
        return ResolveResult(executeLianMin(fsm), true)
    }

    private class executeLianMin(fsm: ReceivePhaseSenderSkill) : WaitingFsm {
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
            if (message !is Role.skill_lian_min_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.whoseTurn
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= r!!.game.players.size) {
                log.error("目标错误")
                return null
            }
            val target = r!!.game.players[r.getAbstractLocation(message.targetPlayerId)]
            if (target !== r && target !== fsm.inFrontOfWhom) {
                log.error("只能以自己或者情报接收者为目标")
                return null
            }
            if (!target.isAlive) {
                log.error("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                return null
            }
            if (!card.colors.contains(color.Black)) {
                log.error("你选择的不是黑色情报")
                return null
            }
            r.incrSeq()
            log.info(r.toString() + "发动了[怜悯]")
            target.deleteMessageCard(card.id)
            r.addCard(card)
            log.info(target.toString() + "面前的" + card + "加入了" + r + "的手牌")
            for (p in r.game.players) {
                (p as? HumanPlayer)?.send(
                    Role.skill_lian_min_toc.newBuilder().setCardId(card.id)
                        .setPlayerId(p.getAlternativeLocation(r.location()))
                        .setTargetPlayerId(p.getAlternativeLocation(target.location())).build()
                )
            }
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
        }

        companion object {
            private val log = Logger.getLogger(executeLianMin::class.java)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeLianMin) return false
            val p: Player = fsm0.fsm.whoseTurn
            for (target in arrayOf(p, fsm0.fsm.inFrontOfWhom)) {
                if (!target!!.isAlive) continue
                for (card in target.messageCards.values) {
                    if (card.colors.contains(color.Black)) {
                        GameExecutor.Companion.post(p.game, Runnable {
                            p.game.tryContinueResolveProtocol(
                                p, Role.skill_lian_min_tos.newBuilder().setCardId(card.id)
                                    .setTargetPlayerId(p.getAlternativeLocation(target.location())).build()
                            )
                        }, 2, TimeUnit.SECONDS)
                        return true
                    }
                }
            }
            return false
        }
    }
}