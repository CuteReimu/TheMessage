package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.WaitForChengQing
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 盛老板技能【如臂指使】：一名角色濒死时，或争夺阶段，你可以翻开此角色牌，查看一名角色的手牌，然后可以从中选择一张弃置，或选择一张符合使用时机的牌，由该角色使用（若如【误导】等需要做出选择的，则由你选择）。
 */
class RuBiZhiShi : AbstractSkill(), ActiveSkill {
    override val skillId = SkillId.RU_BI_ZHI_SHI

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm
        if ((fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn) && (fsm !is WaitForChengQing || r !== fsm.askWhom)) {
            log.error("现在不是发动[如臂指使]的时机")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[如臂指使]的时机")
            return
        }
        if (r.roleFaceUp) {
            log.error("角色面朝上时不能发动[如臂指使]")
            (r as? HumanPlayer)?.sendErrorMessage("角色面朝上时不能发动[如臂指使]")
            return
        }
        val pb = message as skill_ru_bi_zhi_shi_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            (r as? HumanPlayer)?.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        if (target.cards.isEmpty()) {
            log.error("目标没有手牌")
            (r as? HumanPlayer)?.sendErrorMessage("目标没有手牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        log.info("${r}发动了[如臂指使]，查看了${target}的手牌")
        g.playerSetRoleFaceUp(r, true)
        g.resolve(excuteRuBiZhiShi(fsm, r, target))
    }

    private data class excuteRuBiZhiShi(val fsm: Fsm, val r: Player, val target: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_ru_bi_zhi_shi_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.waitingSecond = 15
                    if (p === r) {
                        target.cards.forEach { builder.addCards(it.toPbCard()) }
                        val seq = p.seq
                        builder.seq = seq
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq)) {
                                val builder2 = skill_ru_bi_zhi_shi_b_tos.newBuilder()
                                builder2.enable = false
                                builder.seq = seq
                                g.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val builder2 = skill_ru_bi_zhi_shi_b_tos.newBuilder()
                    builder2.enable = true
                    builder2.cardId = target.cards.random().id
                    builder2.useCard = false
                    g.tryContinueResolveProtocol(r, builder2.build())
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
            if (message !is skill_ru_bi_zhi_shi_b_tos) {
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
            if (!message.enable) {
                r.incrSeq()
                return ResolveResult(fsm, true)
            }
            val card = target.findCard(message.cardId)
            if (card == null) {
                log.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            var newFsm = fsm
            var targetCardId: Int? = null
            var targetPlayerLocation: Int? = null
            if (!message.useCard) {
                log.info("${r}弃掉了${target}的$card")
                r.game!!.playerDiscardCard(target, card)
            } else {
                if (g.qiangLingTypes.contains(card.type)) {
                    log.error("${card}被禁止使用了")
                    (r as? HumanPlayer)?.sendErrorMessage("${card}被禁止使用了")
                    return null
                }
                if (card.type == Cheng_Qing && fsm !is WaitForChengQing || card.type != Cheng_Qing && fsm !is FightPhaseIdle) {
                    log.error("${card}的使用时机错误")
                    (r as? HumanPlayer)?.sendErrorMessage("${card}的使用时机错误")
                    return null
                }
                when (card.type) {
                    Jie_Huo -> {
                        fsm as FightPhaseIdle
                        if (fsm.inFrontOfWhom === target) {
                            log.error("情报在目标角色面前，目标角色不能使用截获")
                            (r as? HumanPlayer)?.sendErrorMessage("情报在目标角色面前，目标角色不能使用截获")
                            return null
                        }
                        log.info("${r}让${target}使用了$card")
                        GameExecutor.post(r.game!!) { card.execute(r.game!!, target) }
                        newFsm = fsm.copy(whoseFightTurn = target)
                    }

                    Wu_Dao -> {
                        fsm as FightPhaseIdle
                        if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                            log.error("目标错误")
                            (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                            return null
                        }
                        val target2 = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
                        if (!target2.alive) {
                            log.error("目标已死亡")
                            (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                            return null
                        }
                        val left = fsm.inFrontOfWhom.getNextLeftAlivePlayer()
                        val right = fsm.inFrontOfWhom.getNextRightAlivePlayer()
                        if (target2 === fsm.inFrontOfWhom || target2 !== left && target2 !== right) {
                            log.error("误导只能选择情报当前人左右两边的人作为目标")
                            (r as? HumanPlayer)?.sendErrorMessage("误导只能选择情报当前人左右两边的人作为目标")
                            return null
                        }
                        targetPlayerLocation = target2.location
                        log.info("${r}让${target}对${target2}使用了$card")
                        GameExecutor.post(r.game!!) { card.execute(r.game!!, target, target2) }
                        newFsm = fsm.copy(whoseFightTurn = target)
                    }

                    Diao_Bao -> {
                        fsm as FightPhaseIdle
                        log.info("${r}让${target}使用了$card")
                        GameExecutor.post(r.game!!) { card.execute(r.game!!, target) }
                        newFsm = fsm.copy(whoseFightTurn = target)
                    }

                    Cheng_Qing -> {
                        fsm as WaitForChengQing
                        if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                            log.error("目标错误")
                            (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                            return null
                        }
                        val target2 = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
                        if (target2 !== fsm.whoDie) {
                            log.error("正在求澄清的人是${fsm.whoDie}")
                            (r as? HumanPlayer)?.sendErrorMessage("正在求澄清的人是${fsm.whoDie}")
                            return null
                        }
                        val card2 = target2.messageCards.find { c -> c.id == message.targetCardId }
                        if (card2 == null) {
                            log.error("没有这张情报")
                            (r as? HumanPlayer)?.sendErrorMessage("没有这张情报")
                            return null
                        }
                        if (!card2.isBlack()) {
                            log.error("澄清只能对黑情报使用")
                            (r as? HumanPlayer)?.sendErrorMessage("澄清只能对黑情报使用")
                            return null
                        }
                        targetPlayerLocation = target2.location
                        targetCardId = card2.id
                        log.info("${r}让${target}对${target2}面前的${card2}使用了$card")
                        GameExecutor.post(r.game!!) { card.execute(r.game!!, target, target2, card2) }
                        newFsm = fsm
                    }

                    else -> {
                        log.error("${card}的使用时机错误")
                        (player as? HumanPlayer)?.sendErrorMessage("${card}的使用时机错误")
                        return null
                    }
                }
            }
            r.incrSeq()
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_ru_bi_zhi_shi_b_toc.newBuilder()
                    builder.enable = message.enable
                    builder.card = card.toPbCard()
                    builder.useCard = message.useCard
                    if (targetPlayerLocation != null)
                        builder.targetPlayerId = p.getAlternativeLocation(targetPlayerLocation)
                    if (targetCardId != null)
                        builder.targetCardId = targetCardId
                    p.send(builder.build())
                }
            }
            return ResolveResult(newFsm, false)
        }

        companion object {
            private val log = Logger.getLogger(excuteRuBiZhiShi::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(RuBiZhiShi::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val r = e.whoseFightTurn
            if (r.roleFaceUp) return false
            val target = r.game!!.players.filter {
                it !== r && it!!.alive && it.cards.isNotEmpty() && it.isEnemy(r)
            }.randomOrNull() ?: return false
            GameExecutor.post(r.game!!, {
                val builder = skill_ru_bi_zhi_shi_a_tos.newBuilder()
                builder.targetPlayerId = r.getAlternativeLocation(target.location)
                skill.executeProtocol(r.game!!, r, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}