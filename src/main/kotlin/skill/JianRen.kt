package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.phase.ReceivePhaseReceiverSkill
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 吴志国技能【坚韧】：你接收黑色情报后，可以展示牌堆顶的一张牌，若是黑色牌，则将展示的牌加入你的手牌，并从一名角色的情报区弃置一张黑色情报。
 */
class JianRen : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.JIAN_REN

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseReceiverSkill
        if (fsm == null || fsm.inFrontOfWhom.findSkill(skillId) == null) return null
        if (fsm.inFrontOfWhom.getSkillUseCount(skillId) > 0) return null
        if (!fsm.messageCard.colors.contains(color.Black)) return null
        fsm.inFrontOfWhom.addSkillUseCount(skillId)
        return ResolveResult(executeJianRenA(fsm), true)
    }

    private data class executeJianRenA(val fsm: ReceivePhaseReceiverSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.whoseTurn.game!!.players)
                p!!.notifyReceivePhase(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, fsm.inFrontOfWhom, 15)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.inFrontOfWhom) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message is Fengsheng.end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                    return null
                }
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message !is skill_jian_ren_a_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.inFrontOfWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                return null
            }
            val cards = g.deck.peek(1)
            if (cards.isEmpty()) {
                log.error("牌堆没有牌了")
                return null
            }
            r.incrSeq()
            log.info("${r}发动了[坚韧]，展示了${cards[0]}")
            return ResolveResult(executeJianRenB(fsm, cards), true)
        }

        companion object {
            private val log = Logger.getLogger(executeJianRenA::class.java)
        }
    }

    private data class executeJianRenB(val fsm: ReceivePhaseReceiverSkill, val cards: MutableList<Card>) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.inFrontOfWhom
            val autoChoose = chooseBlackMessageCard(r)
            val card = cards[0]
            val isBlack = card.colors.contains(color.Black)
            if (isBlack) {
                cards.clear()
                r.cards.add(card)
                log.info("${r}将${card}加入手牌")
            }
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jian_ren_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.card = card.toPbCard()
                    if (isBlack && autoChoose != null) {
                        builder.waitingSecond = 15
                        if (p === r) {
                            val seq2: Int = p.seq
                            builder.seq = seq2
                            p.timeout = GameExecutor.post(g, {
                                if (p.checkSeq(seq2)) {
                                    val builder2 = skill_jian_ren_b_tos.newBuilder()
                                    builder2.targetPlayerId = p.getAlternativeLocation(autoChoose.player.location)
                                    builder2.cardId = autoChoose.card.id
                                    builder2.seq = seq2
                                    p.game!!.tryContinueResolveProtocol(p, builder2.build())
                                }
                            }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        }
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer && isBlack && autoChoose != null) {
                GameExecutor.post(g, {
                    val builder = skill_jian_ren_b_tos.newBuilder()
                    builder.targetPlayerId = r.getAlternativeLocation(autoChoose.player.location)
                    builder.cardId = autoChoose.card.id
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return if (isBlack && autoChoose != null) null else ResolveResult(fsm, true)
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.inFrontOfWhom) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is skill_jian_ren_b_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.inFrontOfWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
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
            log.info("${r}弃掉了${target}面前的$messageCard")
            target.deleteMessageCard(messageCard.id)
            fsm.receiveOrder.removePlayerIfNotHaveThreeBlack(target)
            g.deck.discard(messageCard)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jian_ren_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.cardId = messageCard.id
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeJianRenB::class.java)
        }
    }

    companion object {
        private fun chooseBlackMessageCard(r: Player): PlayerAndCard? {
            for (card in r.messageCards) {
                if (card.colors.contains(color.Black)) return PlayerAndCard(r, card)
            }
            for (p in r.game!!.players) {
                if (p !== r && p!!.alive) {
                    for (card in p.messageCards) {
                        if (card.colors.contains(color.Black)) return PlayerAndCard(p, card)
                    }
                }
            }
            return null
        }

        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeJianRenA) return false
            val p: Player = fsm0.fsm.inFrontOfWhom
            GameExecutor.post(
                p.game!!,
                { p.game!!.tryContinueResolveProtocol(p, skill_jian_ren_a_tos.newBuilder().build()) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}