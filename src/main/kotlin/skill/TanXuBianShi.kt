package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.card.Card
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 凌素秋技能【探虚辨实】：出牌阶段一次，你可以给一名角色一张手牌，该角色还你一张手牌，且需优先选择含其身份颜色的牌。（潜伏=红，军情=蓝，神秘人=任意颜色）
 */
class TanXuBianShi : MainPhaseSkill() {
    override val skillId = SkillId.TAN_XU_BIAN_SHI

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player): Boolean =
        super.mainPhaseNeedNotify(r) && r.cards.isNotEmpty()

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[探虚辨实]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[探虚辨实]一回合只能发动一次")
            return
        }
        val pb = message as skill_tan_xu_bian_shi_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            logger.error("目标错误")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        if (pb.targetPlayerId == 0) {
            logger.error("不能以自己为目标")
            (r as? HumanPlayer)?.sendErrorMessage("不能以自己为目标")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            logger.error("没有这张牌")
            (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.resolve(executeTanXuBianShi(g.fsm!!, r, target, card))
    }

    private data class executeTanXuBianShi(
        val fsm: Fsm,
        val r: Player,
        val target: Player,
        val card: Card
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            logger.info("${r}发动了[探虚辨实]，给了${target}一张$card")
            r.deleteCard(card.id)
            target.cards.add(card)
            val mustGiveColor =
                if (target.identity == Black || target.cards.all { target.identity !in it.colors }) null
                else target.identity
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_tan_xu_bian_shi_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target) builder.card = card.toPbCard()
                    builder.waitingSecond = Config.WaitSecond
                    if (p === target) {
                        val seq = p.seq
                        builder.seq = seq
                        p.timeout = GameExecutor.post(p.game!!, {
                            if (p.checkSeq(seq)) {
                                val builder2 = skill_tan_xu_bian_shi_b_tos.newBuilder()
                                builder2.cardId =
                                    if (mustGiveColor == null) target.cards.first().id
                                    else target.cards.first { mustGiveColor in it.colors }.id
                                builder2.seq = seq
                                p.game!!.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (target is RobotPlayer)
                GameExecutor.post(target.game!!, {
                    val builder2 = skill_tan_xu_bian_shi_b_tos.newBuilder()
                    builder2.cardId =
                        if (mustGiveColor == null) target.cards.bestCard(target.identity, true).id
                        else target.cards.filter { mustGiveColor in it.colors }.bestCard(target.identity, true).id
                    target.game!!.tryContinueResolveProtocol(target, builder2.build())
                }, 3, TimeUnit.SECONDS)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== target) {
                logger.error("你不是被探虚辨实的目标")
                (player as? HumanPlayer)?.sendErrorMessage("你不是被探虚辨实的目标")
                return null
            }
            if (message !is skill_tan_xu_bian_shi_b_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = target.game!!
            if (target is HumanPlayer && !target.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${target.seq}, actual Seq: ${message.seq}")
                target.sendErrorMessage("操作太晚了")
                return null
            }
            val card = target.findCard(message.cardId)
            if (card == null) {
                logger.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            val mustGiveColor =
                if (target.identity == Black || target.cards.all { target.identity !in it.colors }) null
                else target.identity
            if (mustGiveColor != null && mustGiveColor !in card.colors) {
                logger.error("你必须选择含你身份颜色的牌")
                (player as? HumanPlayer)?.sendErrorMessage("你必须选择含你身份颜色的牌")
                return null
            }
            target.incrSeq()
            target.deleteCard(card.id)
            r.cards.add(card)
            logger.info("${target}给了${r}$card")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_tan_xu_bian_shi_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target) builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            r.game!!.addEvent(GiveCardEvent(r, r, target))
            r.game!!.addEvent(GiveCardEvent(r, target, r))
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            e.whoseTurn.getSkillUseCount(SkillId.TAN_XU_BIAN_SHI) == 0 || return false
            val card = e.whoseTurn.cards.randomOrNull() ?: return false
            val player = e.whoseTurn.game!!.players.filter { p ->
                p !== e.whoseTurn && p!!.alive && p.cards.isNotEmpty()
            }.run {
                if (e.whoseTurn.game!!.isEarly) this
                else filter { it!!.isEnemy(e.whoseTurn) }.ifEmpty { this }
            }.randomOrNull() ?: return false
            GameExecutor.post(e.whoseTurn.game!!, {
                val builder = skill_tan_xu_bian_shi_a_tos.newBuilder()
                builder.targetPlayerId = e.whoseTurn.getAlternativeLocation(player.location)
                builder.cardId = card.id
                skill.executeProtocol(e.whoseTurn.game!!, e.whoseTurn, builder.build())
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}