package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.ReceivePhaseReceiverSkillimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Fengshengimport com.fengsheng.protos.Roleimport com.fengsheng.protos.Role.skill_jian_ren_a_tocimport com.fengsheng.protos.Role.skill_jian_ren_b_tocimport com.fengsheng.skill.JianRen.executeJianRenAimport com.fengsheng.skill.JianRen.executeJianRenBimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 吴志国技能【坚韧】：你接收黑色情报后，可以展示牌堆顶的一张牌，若是黑色牌，则将展示的牌加入你的手牌，并从一名角色的情报区弃置一张黑色情报。
 */
class JianRen : AbstractSkill(), TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.JIAN_REN
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm !is ReceivePhaseReceiverSkill || fsm.inFrontOfWhom.findSkill<Skill>(skillId) == null) return null
        if (fsm.inFrontOfWhom.getSkillUseCount(skillId) > 0) return null
        if (!fsm.messageCard.getColors().contains(color.Black)) return null
        fsm.inFrontOfWhom.addSkillUseCount(skillId)
        return ResolveResult(executeJianRenA(fsm), true)
    }

    private class executeJianRenA(fsm: ReceivePhaseReceiverSkill) : WaitingFsm {
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
            if (message !is Role.skill_jian_ren_a_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.inFrontOfWhom
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            val cards = g.deck.peek(1)
            if (cards.isEmpty()) {
                log.error("牌堆没有牌了")
                return null
            }
            r.incrSeq()
            log.info(r.toString() + "发动了[坚韧]，展示了" + cards[0])
            return ResolveResult(executeJianRenB(fsm, cards), true)
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
        }

        companion object {
            private val log = Logger.getLogger(executeJianRenA::class.java)
        }
    }

    private class executeJianRenB(fsm: ReceivePhaseReceiverSkill, cards: MutableList<Card>) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.inFrontOfWhom
            val autoChoose = chooseBlackMessageCard(r)
            val card = cards[0]
            val isBlack = card.colors.contains(color.Black)
            if (isBlack) {
                cards.clear()
                r.addCard(card)
                log.info(r.toString() + "将" + card + "加入手牌")
            }
            val g = r.game
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jian_ren_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.card = card.toPbCard()
                    if (isBlack && autoChoose != null) {
                        builder.waitingSecond = 15
                        if (p === r) {
                            val seq2: Int = p.seq
                            builder.seq = seq2
                            p.setTimeout(GameExecutor.Companion.post(g, Runnable {
                                if (p.checkSeq(seq2)) {
                                    p.getGame().tryContinueResolveProtocol(
                                        p, Role.skill_jian_ren_b_tos.newBuilder()
                                            .setTargetPlayerId(p.getAlternativeLocation(autoChoose.player.location()))
                                            .setCardId(autoChoose.card.id).setSeq(seq2).build()
                                    )
                                }
                            }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                        }
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer && isBlack && autoChoose != null) {
                GameExecutor.Companion.post(g, Runnable {
                    r.getGame().tryContinueResolveProtocol(
                        r,
                        Role.skill_jian_ren_b_tos.newBuilder()
                            .setTargetPlayerId(r.getAlternativeLocation(autoChoose.player.location()))
                            .setCardId(autoChoose.card.id).build()
                    )
                }, 2, TimeUnit.SECONDS)
            }
            return if (isBlack && autoChoose != null) null else ResolveResult(fsm, true)
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.inFrontOfWhom) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is Role.skill_jian_ren_b_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.inFrontOfWhom
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
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
            val messageCard = target.findMessageCard(message.cardId)
            if (messageCard == null) {
                log.error("没有这张情报")
                return null
            }
            if (!messageCard.colors.contains(color.Black)) {
                log.error("目标情报不是黑色的")
                return null
            }
            r.incrSeq()
            log.info(r.toString() + "弃掉了" + target + "面前的" + messageCard)
            target.deleteMessageCard(messageCard.id)
            fsm.receiveOrder.removePlayerIfNotHaveThreeBlack(target)
            g.deck.discard(messageCard)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jian_ren_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    builder.cardId = messageCard.id
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        val fsm: ReceivePhaseReceiverSkill
        val cards: MutableList<Card>

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
        }

        companion object {
            private val log = Logger.getLogger(executeJianRenB::class.java)
        }
    }

    companion object {
        private fun chooseBlackMessageCard(r: Player): PlayerAndCard? {
            for (card in r.messageCards.values) {
                if (card.colors.contains(color.Black)) return PlayerAndCard(r, card)
            }
            for (p in r.game.players) {
                if (p !== r && p.isAlive) {
                    for (card in p.messageCards.values) {
                        if (card.colors.contains(color.Black)) return PlayerAndCard(p, card)
                    }
                }
            }
            return null
        }

        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeJianRenA) return false
            val p: Player = fsm0.fsm.inFrontOfWhom
            GameExecutor.Companion.post(
                p.game,
                Runnable { p.game.tryContinueResolveProtocol(p, Role.skill_jian_ren_a_tos.newBuilder().build()) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}