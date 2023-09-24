package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.phase.NextTurn
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 速记员技能【妙手快记】：你相邻玩家的回合结束时，你可以选择获得弃牌堆顶的牌，然后弃一张牌。
 */
class MiaoShouKuaiJi : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.MIAO_SHOU_KUAI_JI

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? NextTurn ?: return null
        askWhom.alive || return null
        fsm.player == askWhom.getNextLeftAlivePlayer() || fsm.player == askWhom.getNextRightAlivePlayer() || return null
        fsm.player !== askWhom || return null // 只剩自己一个人存活了，不能发动技能
        askWhom.getSkillUseCount(skillId) == 0 || return null
        val card = g.deck.popDiscardPile() ?: return null
        if (askWhom.cards.isEmpty()) {
            (askWhom as? HumanPlayer)?.sendErrorMessage("没有手牌，默认不发动【妙手快记】")
            return null
        }
        askWhom.addSkillUseCount(skillId)
        return ResolveResult(executeMiaoShouKuaiJi(fsm, askWhom, card), true)
    }

    private data class executeMiaoShouKuaiJi(val fsm: NextTurn, val r: Player, val card: Card) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            log.info("${r}发动了[妙手快记]，将弃牌堆的${card}加入手牌")
            val autoDiscard = r.cards.isEmpty()
            r.cards.add(card)
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_miao_shou_kuai_ji_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.card = card.toPbCard()
                    if (!autoDiscard) {
                        builder.waitingSecond = Config.WaitSecond
                        if (p === r) {
                            val seq = p.seq
                            builder.seq = seq
                            p.timeout = GameExecutor.post(p.game!!, {
                                if (p.checkSeq(seq)) {
                                    val builder2 = skill_miao_shou_kuai_ji_b_tos.newBuilder()
                                    builder2.cardId = card.id
                                    builder2.seq = seq
                                    p.game!!.tryContinueResolveProtocol(p, builder2.build())
                                }
                            }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        }
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer && !autoDiscard) {
                GameExecutor.post(r.game!!, {
                    val builder2 = skill_miao_shou_kuai_ji_b_tos.newBuilder()
                    builder2.cardId = card.id
                    r.game!!.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            if (autoDiscard) {
                r.game!!.playerDiscardCard(r, card)
                return ResolveResult(fsm, true)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_miao_shou_kuai_ji_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
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
            r.incrSeq()
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_miao_shou_kuai_ji_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    p.send(builder.build())
                }
            }
            g.playerDiscardCard(r, card)
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeMiaoShouKuaiJi::class.java)
        }
    }
}