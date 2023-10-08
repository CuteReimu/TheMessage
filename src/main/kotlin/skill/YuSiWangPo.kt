package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnDiscardCard
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 死士技能【鱼死网破】：出牌阶段限一次，你可以弃掉任意张数手牌（至少1），让一名其他玩家弃置对应数量+1的手牌（不足则全弃），然后你们各摸一张牌。
 */
class YuSiWangPo : MainPhaseSkill(), InitialSkill {
    override val skillId = SkillId.YU_SI_WANG_PO

    override fun mainPhaseNeedNotify(r: Player): Boolean =
        super.mainPhaseNeedNotify(r) && r.cards.isNotEmpty()

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (r !== (g.fsm as? MainPhaseIdle)?.player) {
            log.error("现在不是出牌阶段空闲时点")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[鱼死网破]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[鱼死网破]一回合只能发动一次")
            return
        }
        val pb = message as skill_yu_si_wang_po_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        if (pb.targetPlayerId == 0) {
            log.error("不能以自己为目标")
            (r as? HumanPlayer)?.sendErrorMessage("不能以自己为目标")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        val cards = pb.cardIdsList.map {
            val card = r.findCard(it)
            if (card == null) {
                log.error("没有这张卡")
                (r as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                return
            }
            card
        }
        val discardAll = cards.size + 1 >= target.cards.size
        r.incrSeq()
        r.addSkillUseCount(skillId)
        log.info("${r}对${target}发动了[鱼死网破]")
        val timeout = Config.WaitSecond
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_yu_si_wang_po_a_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                builder.cardCount = cards.size + 1
                if (!discardAll) {
                    builder.waitingSecond = timeout
                    if (p === target) builder.seq = p.seq
                }
                p.send(builder.build())
            }
        }
        g.playerDiscardCard(r, *cards.toTypedArray())
        if (discardAll) {
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_yu_si_wang_po_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            var newFsm = g.fsm!!
            if (target.cards.isNotEmpty()) newFsm = OnDiscardCard(r, target, newFsm)
            newFsm = OnDiscardCard(r, r, newFsm)
            g.playerDiscardCard(target, *target.cards.toTypedArray())
            r.draw(1)
            target.draw(1)
            g.resolve(newFsm)
        } else {
            g.resolve(executeYuSiWangPo(r, target, cards.size + 1, timeout))
        }
    }

    private data class executeYuSiWangPo(
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
                        val builder2 = skill_yu_si_wang_po_b_tos.newBuilder()
                        builder2.addAllCardIds(target.cards.subList(0, cardCount).map { it.id })
                        builder2.seq = seq
                        g.tryContinueResolveProtocol(target, builder2.build())
                    }
                }, target.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(g, {
                    val builder2 = skill_yu_si_wang_po_b_tos.newBuilder()
                    builder2.addAllCardIds(target.cards.shuffled().subList(0, cardCount).map { it.id })
                    g.tryContinueResolveProtocol(target, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== target) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_yu_si_wang_po_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (target is HumanPlayer && !target.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${target.seq}, actual Seq: ${message.seq}")
                target.sendErrorMessage("操作太晚了")
                return null
            }
            if (cardCount != message.cardIdsCount) {
                log.error("选择的卡牌数量不对")
                (player as? HumanPlayer)?.sendErrorMessage("你需要选择${cardCount}张牌")
                return null
            }
            val cards = message.cardIdsList.map {
                val card = target.findCard(it)
                if (card == null) {
                    log.error("没有这张卡")
                    (target as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                    return null
                }
                card
            }
            target.incrSeq()
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_yu_si_wang_po_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            target.game!!.playerDiscardCard(target, *cards.toTypedArray())
            r.draw(1)
            target.draw(1)
            return ResolveResult(OnDiscardCard(r, r, OnDiscardCard(r, target, MainPhaseIdle(r))), true)
        }

        companion object {
            private val log = Logger.getLogger(executeYuSiWangPo::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(YuSiWangPo::class.java)
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            e.player.getSkillUseCount(SkillId.YU_SI_WANG_PO) == 0 || return false
            e.player.cards.isNotEmpty() || return false
            val target = e.player.game!!.players.filter { it!!.alive && it.isEnemy(e.player) && it.cards.size >= 2 }
                .randomOrNull() ?: return false
            val count = (1..minOf(e.player.cards.size, target.cards.size - 1)).random()
            val cardIds = e.player.cards.shuffled().subList(0, count).map { it.id }
            GameExecutor.post(e.player.game!!, {
                val builder = skill_yu_si_wang_po_a_tos.newBuilder()
                builder.targetPlayerId = e.player.getAlternativeLocation(target.location)
                builder.addAllCardIds(cardIds)
                skill.executeProtocol(e.player.game!!, e.player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}