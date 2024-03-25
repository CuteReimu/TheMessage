package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.card.Card
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.skill_tan_xu_bian_shi_a_tos
import com.fengsheng.protos.Role.skill_tan_xu_bian_shi_b_tos
import com.fengsheng.protos.skillTanXuBianShiAToc
import com.fengsheng.protos.skillTanXuBianShiATos
import com.fengsheng.protos.skillTanXuBianShiBToc
import com.fengsheng.protos.skillTanXuBianShiBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 凌素秋技能【探虚辨实】：出牌阶段一次，你可以给一名角色一张手牌，该角色还你一张手牌，且需优先选择含其身份颜色的牌。（潜伏=红，军情=蓝，神秘人=任意颜色）
 */
class TanXuBianShi : MainPhaseSkill() {
    override val skillId = SkillId.TAN_XU_BIAN_SHI

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player): Boolean = super.mainPhaseNeedNotify(r) && r.cards.isNotEmpty()

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            r.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[探虚辨实]一回合只能发动一次")
            r.sendErrorMessage("[探虚辨实]一回合只能发动一次")
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
            logger.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.resolve(ExecuteTanXuBianShi(g.fsm!!, r, target, card))
    }

    private data class ExecuteTanXuBianShi(
        val fsm: Fsm,
        val r: Player,
        val target: Player,
        val card: Card
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            logger.info("${r}发动了[探虚辨实]，给了${target}一张$card")
            r.deleteCard(card.id)
            target.cards.add(card)
            val mustGiveColor = if (target.identity == Black || target.cards.all { target.identity !in it.colors }) null
            else target.identity
            r.game!!.players.send { p ->
                skillTanXuBianShiAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target) card = this@ExecuteTanXuBianShi.card.toPbCard()
                    waitingSecond = Config.WaitSecond
                    if (p === target) {
                        val seq = p.seq
                        this.seq = seq
                        p.timeout = GameExecutor.post(p.game!!, {
                            if (p.checkSeq(seq)) {
                                p.game!!.tryContinueResolveProtocol(p, skillTanXuBianShiBTos {
                                    cardId = if (mustGiveColor == null) target.cards.first().id
                                    else target.cards.first { mustGiveColor in it.colors }.id
                                    this.seq = seq
                                })
                            }
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (target is RobotPlayer) GameExecutor.post(target.game!!, {
                target.game!!.tryContinueResolveProtocol(target, skillTanXuBianShiBTos {
                    cardId = if (mustGiveColor == null) target.cards.bestCard(target.identity, true).id
                    else target.cards.filter { mustGiveColor in it.colors }.bestCard(target.identity, true).id
                })
            }, 3, TimeUnit.SECONDS)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== target) {
                logger.error("你不是被探虚辨实的目标")
                player.sendErrorMessage("你不是被探虚辨实的目标")
                return null
            }
            if (message !is skill_tan_xu_bian_shi_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
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
                player.sendErrorMessage("没有这张牌")
                return null
            }
            val mustGiveColor = if (target.identity == Black || target.cards.all { target.identity !in it.colors }) null
            else target.identity
            if (mustGiveColor != null && mustGiveColor !in card.colors) {
                logger.error("你必须选择含你身份颜色的牌")
                player.sendErrorMessage("你必须选择含你身份颜色的牌")
                return null
            }
            target.incrSeq()
            target.deleteCard(card.id)
            r.cards.add(card)
            logger.info("${target}给了${r}$card")
            g.players.send { p ->
                skillTanXuBianShiBToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target) this.card = card.toPbCard()
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
            val card = e.whoseTurn.cards.ifEmpty { return false }.bestCard(e.whoseTurn.identity, true)
            val player = e.whoseTurn.game!!.players.filter { p ->
                p !== e.whoseTurn && p!!.alive && p.cards.isNotEmpty()
            }.run {
                if (e.whoseTurn.game!!.isEarly) this
                else filter { it!!.isEnemy(e.whoseTurn) }.ifEmpty { return false }
                    .run { if (e.whoseTurn.identity != Black) filter { it!!.identity != Black }.ifEmpty { this } else this }
            }.randomOrNull() ?: return false
            GameExecutor.post(e.whoseTurn.game!!, {
                skill.executeProtocol(e.whoseTurn.game!!, e.whoseTurn, skillTanXuBianShiATos {
                    targetPlayerId = e.whoseTurn.getAlternativeLocation(player.location)
                    cardId = card.id
                })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
