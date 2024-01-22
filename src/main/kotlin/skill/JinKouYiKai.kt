package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 玄青子技能【金口一开】：你的回合的争夺阶段限一次，你可以查看牌堆顶的一张牌，然后选择一项：
 *  * 你摸一张牌。
 *  * 将牌堆顶的一张牌和待接收情报面朝下互换
 */
class JinKouYiKai : ActiveSkill {
    override val skillId = SkillId.JIN_KOU_YI_KAI

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = fightPhase.whoseTurn === r

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn || r !== fsm.whoseTurn) {
            log.error("现在不是发动[金口一开]的时机")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[金口一开]的时机")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[金口一开]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[金口一开]一回合只能发动一次")
            return
        }
        val pb = message as skill_jin_kou_yi_kai_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val cards = g.deck.peek(1)
        if (cards.isEmpty()) {
            log.error("牌堆没牌了")
            (r as? HumanPlayer)?.sendErrorMessage("牌堆没牌了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.resolve(executeJinKouYiKai(fsm, r, cards))
    }

    private data class executeJinKouYiKai(val fsm: FightPhaseIdle, val r: Player, val cards: List<Card>) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            log.info("${r}发动了[金口一开]")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jin_kou_yi_kai_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (p === r) {
                        builder.card = cards.first().toPbCard()
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_jin_kou_yi_kai_b_tos.newBuilder()
                                builder2.exchange = false
                                builder2.seq = seq2
                                g.tryContinueResolveProtocol(r, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val builder = skill_jin_kou_yi_kai_b_tos.newBuilder()
                    builder.exchange = Random.nextBoolean()
                    g.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jin_kou_yi_kai_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            r.incrSeq()
            for (p in g!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jin_kou_yi_kai_b_toc.newBuilder()
                    builder.exchange = message.exchange
                    builder.playerId = p.getAlternativeLocation(r.location)
                    p.send(builder.build())
                }
            }
            return if (message.exchange) {
                val temp = cards.first()
                log.info("${r}将待接收情报${fsm.messageCard}与牌堆顶的${temp}互换")
                g.deck.draw(1)
                g.deck.addFirst(fsm.messageCard)
                ResolveResult(
                    fsm.copy(
                        whoseFightTurn = fsm.inFrontOfWhom,
                        messageCard = temp,
                        isMessageCardFaceUp = false
                    ), true
                )
            } else {
                r.draw(1)
                ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
            }
        }

        companion object {
            private val log = Logger.getLogger(executeJinKouYiKai::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(JinKouYiKai::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player !== e.whoseTurn || player.getSkillUseCount(SkillId.JIN_KOU_YI_KAI) > 0) return false
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skill_jin_kou_yi_kai_a_tos.getDefaultInstance())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}