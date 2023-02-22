package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseReceiverSkillimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Fengshengimport com.fengsheng.protos.Roleimport com.fengsheng.skill.YiYaHuanYa.executeYiYaHuanYaimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 王魁技能【以牙还牙】：你接收黑色情报后，可以将一张黑色手牌置入情报传出者或其相邻角色的情报区，然后摸一张牌。
 */
class YiYaHuanYa : AbstractSkill(), TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.YI_YA_HUAN_YA
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm !is ReceivePhaseReceiverSkill || fsm.inFrontOfWhom.findSkill<Skill>(skillId) == null) return null
        if (fsm.inFrontOfWhom.getSkillUseCount(skillId) > 0) return null
        if (!fsm.messageCard.getColors().contains(color.Black)) return null
        fsm.inFrontOfWhom.addSkillUseCount(skillId)
        return ResolveResult(executeYiYaHuanYa(fsm), true)
    }

    private class executeYiYaHuanYa(fsm: ReceivePhaseReceiverSkill) : WaitingFsm {
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
            if (message !is Role.skill_yi_ya_huan_ya_tos) {
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
            if (!card.colors.contains(color.Black)) {
                log.error("你只能选择黑色手牌")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]
            if (!target.isAlive) {
                log.error("目标已死亡")
                return null
            }
            if (target !== fsm.whoseTurn && target !== fsm.whoseTurn.nextLeftAlivePlayer && target !== fsm.whoseTurn.nextRightAlivePlayer) {
                log.error("你只能选择情报传出者或者其左边或右边的角色作为目标：" + message.targetPlayerId)
                return null
            }
            r.incrSeq()
            log.info(r.toString() + "对" + target + "发动了[以牙还牙]")
            r.deleteCard(card.id)
            target.addMessageCard(card)
            fsm.receiveOrder.addPlayerIfHasThreeBlack(target)
            for (p in g.players) {
                (p as? HumanPlayer)?.send(
                    Role.skill_yi_ya_huan_ya_toc.newBuilder().setCard(card.toPbCard())
                        .setPlayerId(p.getAlternativeLocation(r.location()))
                        .setTargetPlayerId(p.getAlternativeLocation(target.location())).build()
                )
            }
            r.draw(1)
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
        }

        companion object {
            private val log = Logger.getLogger(executeYiYaHuanYa::class.java)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeYiYaHuanYa) return false
            val p: Player = fsm0.fsm.inFrontOfWhom
            var target: Player = fsm0.fsm.whoseTurn
            if (p === target) {
                target = if (ThreadLocalRandom.current()
                        .nextBoolean()
                ) target.nextLeftAlivePlayer else target.nextRightAlivePlayer
                if (p === target) return false
            }
            val finalTarget = target
            for (card in p.cards.values) {
                if (card.colors.contains(color.Black)) {
                    GameExecutor.Companion.post(p.game, Runnable {
                        p.game.tryContinueResolveProtocol(
                            p, Role.skill_yi_ya_huan_ya_tos.newBuilder().setCardId(card.id)
                                .setTargetPlayerId(p.getAlternativeLocation(finalTarget.location())).build()
                        )
                    }, 2, TimeUnit.SECONDS)
                    return true
                }
            }
            return false
        }
    }
}