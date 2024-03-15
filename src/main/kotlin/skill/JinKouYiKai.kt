package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Role.skill_jin_kou_yi_kai_a_tos
import com.fengsheng.protos.Role.skill_jin_kou_yi_kai_b_tos
import com.fengsheng.protos.skillJinKouYiKaiAToc
import com.fengsheng.protos.skillJinKouYiKaiATos
import com.fengsheng.protos.skillJinKouYiKaiBToc
import com.fengsheng.protos.skillJinKouYiKaiBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 玄青子技能【金口一开】：你的回合的争夺阶段限一次，你可以查看牌堆顶的一张牌，然后选择一项：
 *  * 你摸一张牌。
 *  * 将牌堆顶的一张牌和待接收情报面朝下互换
 */
class JinKouYiKai : ActiveSkill {
    override val skillId = SkillId.JIN_KOU_YI_KAI

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = fightPhase.whoseTurn === r

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn || r !== fsm.whoseTurn) {
            logger.error("现在不是发动[金口一开]的时机")
            r.sendErrorMessage("现在不是发动[金口一开]的时机")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[金口一开]一回合只能发动一次")
            r.sendErrorMessage("[金口一开]一回合只能发动一次")
            return
        }
        val pb = message as skill_jin_kou_yi_kai_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val cards = g.deck.peek(1)
        if (cards.isEmpty()) {
            logger.error("牌堆没牌了")
            r.sendErrorMessage("牌堆没牌了")
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
            logger.info("${r}发动了[金口一开]")
            g.players.send { p ->
                skillJinKouYiKaiAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond
                    if (p === r) {
                        card = cards.first().toPbCard()
                        val seq2: Int = p.seq
                        seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2))
                                g.tryContinueResolveProtocol(r, skillJinKouYiKaiBTos { seq = seq2 })
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val oldValue = r.calculateMessageCardValue(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard)
                    val newValue = r.calculateMessageCardValue(fsm.whoseTurn, fsm.inFrontOfWhom, cards.first())
                    g.tryContinueResolveProtocol(r, skillJinKouYiKaiBTos { exchange = newValue > oldValue + 10 })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jin_kou_yi_kai_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            r.incrSeq()
            g.players.send {
                skillJinKouYiKaiBToc {
                    exchange = message.exchange
                    playerId = it.getAlternativeLocation(r.location)
                }
            }
            return if (message.exchange) {
                val temp = cards.first()
                logger.info("${r}将待接收情报${fsm.messageCard}与牌堆顶的${temp}互换")
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
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player !== e.whoseTurn || player.getSkillUseCount(SkillId.JIN_KOU_YI_KAI) > 0) return false
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skillJinKouYiKaiATos { })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}