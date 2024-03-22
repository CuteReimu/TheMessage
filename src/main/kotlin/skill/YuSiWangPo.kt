package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.count
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.skill_yu_si_wang_po_a_tos
import com.fengsheng.protos.Role.skill_yu_si_wang_po_b_tos
import com.fengsheng.protos.skillYuSiWangPoAToc
import com.fengsheng.protos.skillYuSiWangPoATos
import com.fengsheng.protos.skillYuSiWangPoBToc
import com.fengsheng.protos.skillYuSiWangPoBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 秦无命技能【鱼死网破】：出牌阶段限一次，你可以弃置一张手牌，令一名其他角色弃置（你的黑情报数量+1）的手牌（不足则全弃）。
 */
class YuSiWangPo : MainPhaseSkill() {
    override val skillId = SkillId.YU_SI_WANG_PO

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player): Boolean = super.mainPhaseNeedNotify(r) && r.cards.size > 1

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            r.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[鱼死网破]一回合只能发动一次")
            r.sendErrorMessage("[鱼死网破]一回合只能发动一次")
            return
        }
        val pb = message as skill_yu_si_wang_po_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            logger.error("目标错误")
            r.sendErrorMessage("目标错误")
            return
        }
        if (pb.targetPlayerId == 0) {
            logger.error("不能以自己为目标")
            r.sendErrorMessage("不能以自己为目标")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            r.sendErrorMessage("目标已死亡")
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            logger.error("没有这张卡")
            r.sendErrorMessage("没有这张卡")
            return
        }
        val discardCount = r.messageCards.count(Black) + 1
        val discardAll = discardCount >= target.cards.size
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}对${target}发动了[鱼死网破]")
        val timeout = Config.WaitSecond
        g.players.send { p ->
            skillYuSiWangPoAToc {
                playerId = p.getAlternativeLocation(r.location)
                targetPlayerId = p.getAlternativeLocation(target.location)
                if (!discardAll) {
                    waitingSecond = timeout
                    if (p === target) seq = p.seq
                }
            }
        }
        g.playerDiscardCard(r, card)
        g.addEvent(DiscardCardEvent(r, r))
        if (target.cards.isNotEmpty()) g.addEvent(DiscardCardEvent(r, target))
        if (discardAll) {
            g.players.send {
                skillYuSiWangPoBToc {
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                }
            }
            g.playerDiscardCard(target, target.cards.toList())
            g.continueResolve()
        } else {
            g.resolve(executeYuSiWangPo(g.fsm!!, r, target, discardCount, timeout))
        }
    }

    private data class executeYuSiWangPo(
        val fsm: Fsm,
        val r: Player,
        val target: Player,
        val cardCount: Int,
        val waitingSecond: Int
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            if (target is HumanPlayer) {
                val seq = target.seq
                target.timeout = GameExecutor.post(g, {
                    if (target.checkSeq(seq)) {
                        g.tryContinueResolveProtocol(target, skillYuSiWangPoBTos {
                            cardIds.addAll(target.cards.shuffled().take(cardCount).map { it.id })
                            this.seq = seq
                        })
                    }
                }, target.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(g, {
                    g.tryContinueResolveProtocol(target, skillYuSiWangPoBTos {
                        cardIds.addAll(target.cards.sortCards(target.identity, true).take(cardCount).map { it.id })
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== target) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_yu_si_wang_po_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            if (target is HumanPlayer && !target.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${target.seq}, actual Seq: ${message.seq}")
                target.sendErrorMessage("操作太晚了")
                return null
            }
            if (cardCount != message.cardIdsCount) {
                logger.error("选择的卡牌数量不对")
                player.sendErrorMessage("你需要选择${cardCount}张牌")
                return null
            }
            val cards = message.cardIdsList.map {
                val card = target.findCard(it)
                if (card == null) {
                    logger.error("没有这张卡")
                    target.sendErrorMessage("没有这张卡")
                    return null
                }
                card
            }
            target.incrSeq()
            r.game!!.players.send {
                skillYuSiWangPoBToc {
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                }
            }
            target.game!!.playerDiscardCard(target, cards.toList())
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            e.whoseTurn.getSkillUseCount(SkillId.YU_SI_WANG_PO) == 0 || return false
            !e.whoseTurn.game!!.isEarly || return false
            val card = e.whoseTurn.cards.ifEmpty { return false }.bestCard(e.whoseTurn.identity, true)
            val target =
                e.whoseTurn.game!!.players.filter { it!!.alive && it.isEnemy(e.whoseTurn) && it.cards.size >= 2 }
                    .ifEmpty { return false }
                    .run { if (e.whoseTurn.identity != Black) filter { it!!.identity != Black }.ifEmpty { this } else this }
                    .randomOrNull() ?: return false
            GameExecutor.post(e.whoseTurn.game!!, {
                skill.executeProtocol(e.whoseTurn.game!!, e.whoseTurn, skillYuSiWangPoATos {
                    targetPlayerId = e.whoseTurn.getAlternativeLocation(target.location)
                    cardId = card.id
                })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
