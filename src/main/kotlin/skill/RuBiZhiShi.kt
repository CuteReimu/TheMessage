package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.WaitForChengQing
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 盛老板技能【如臂指使】：一名角色濒死时，或争夺阶段，你可以翻开此角色牌，查看一名角色的手牌，然后可以从中选择一张弃置，或选择一张符合使用时机的牌，由该角色使用（若如【误导】等需要做出选择的，则由你选择）。
 */
class RuBiZhiShi : ActiveSkill {
    override val skillId = SkillId.RU_BI_ZHI_SHI

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm
        if ((fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn) && (fsm !is WaitForChengQing || r !== fsm.askWhom)) {
            logger.error("现在不是发动[如臂指使]的时机")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[如臂指使]的时机")
            return
        }
        if (r.roleFaceUp) {
            logger.error("角色面朝上时不能发动[如臂指使]")
            (r as? HumanPlayer)?.sendErrorMessage("角色面朝上时不能发动[如臂指使]")
            return
        }
        val pb = message as skill_ru_bi_zhi_shi_a_tos
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
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        if (target.cards.isEmpty()) {
            logger.error("目标没有手牌")
            (r as? HumanPlayer)?.sendErrorMessage("目标没有手牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}发动了[如臂指使]，查看了${target}的手牌")
        g.playerSetRoleFaceUp(r, true)
        g.resolve(executeRuBiZhiShi(fsm as ProcessFsm, r, target))
    }

    data class executeRuBiZhiShi(val fsm: ProcessFsm, val r: Player, val target: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_ru_bi_zhi_shi_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.waitingSecond = Config.WaitSecond * 2
                    if (p === r) {
                        target.cards.forEach { builder.addCards(it.toPbCard()) }
                        val seq = p.seq
                        builder.seq = seq
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq)) {
                                val builder2 = skill_ru_bi_zhi_shi_b_tos.newBuilder()
                                builder2.enable = true
                                builder2.cardId = target.cards.random().id
                                builder2.seq = seq
                                g.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    if (fsm is FightPhaseIdle) {
                        val result = r.calFightPhase(fsm, target, target.cards)
                        if (result != null) {
                            when (result.cardType) {
                                Jie_Huo -> {
                                    val builder = use_jie_huo_tos.newBuilder()
                                    builder.cardId = result.card.id
                                    g.tryContinueResolveProtocol(r, builder.build())
                                }

                                Diao_Bao -> {
                                    val builder = use_diao_bao_tos.newBuilder()
                                    builder.cardId = result.card.id
                                    g.tryContinueResolveProtocol(r, builder.build())
                                }

                                else -> { // Wu_Dao
                                    val builder = use_wu_dao_tos.newBuilder()
                                    builder.cardId = result.card.id
                                    builder.targetPlayerId = r.getAlternativeLocation(result.wuDaoTarget!!.location)
                                    g.tryContinueResolveProtocol(r, builder.build())
                                }
                            }
                            return@post
                        }
                    } else if (fsm is WaitForChengQing) {
                        if (!target.cannotPlayCard(Cheng_Qing)) {
                            for (card in target.cards.sortCards(r.identity)) {
                                target.canUseCardTypes(Cheng_Qing, card, true).first || continue
                                val black =
                                    fsm.whoDie.messageCards.filter { it.isBlack() }.run run1@{
                                        if (fsm.whoDie.identity == Black) {
                                            when (fsm.whoDie.secretTask) {
                                                Killer, Pioneer ->
                                                    return@run1 find { it.colors.size > 1 } ?: firstOrNull()

                                                Sweeper ->
                                                    return@run1 find { it.colors.size == 1 } ?: firstOrNull()

                                                else -> {}
                                            }
                                        }
                                        find { it.colors.size == 1 }
                                            ?: find { r.identity !in it.colors }
                                            ?: firstOrNull()
                                    } ?: break
                                val builder = cheng_qing_save_die_tos.newBuilder()
                                builder.use = true
                                builder.cardId = card.id
                                builder.targetCardId = black.id
                                g.tryContinueResolveProtocol(r, builder.build())
                                return@post
                            }
                        }
                    }
                    val builder = skill_ru_bi_zhi_shi_b_tos.newBuilder()
                    builder.enable = true
                    builder.cardId = target.cards.bestCard(r.identity).id
                    g.tryContinueResolveProtocol(r, builder.build())
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message is skill_ru_bi_zhi_shi_b_tos) {
                if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                    logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                    r.sendErrorMessage("操作太晚了")
                    return null
                }
                if (!message.enable) {
                    r.incrSeq()
                    notifyUseSkill(enable = false, useCard = false)
                    if (fsm is FightPhaseIdle)
                        return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
                    return ResolveResult(fsm, true)
                }
                val card = target.findCard(message.cardId)
                if (card == null) {
                    logger.error("没有这张牌")
                    (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                    return null
                }
                logger.info("${r}选择弃掉${target}的$card")
                r.incrSeq()
                notifyUseSkill(enable = true, useCard = false)
                r.game!!.playerDiscardCard(target, card)
                r.game!!.addEvent(DiscardCardEvent(fsm.whoseTurn, target))
                if (fsm is FightPhaseIdle)
                    return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
                return ResolveResult(fsm, true)
            }
            when (message) {
                is use_jie_huo_tos -> {
                    if (fsm !is FightPhaseIdle) {
                        logger.error("截获的使用时机错误")
                        (r as? HumanPlayer)?.sendErrorMessage("截获的使用时机错误")
                        return null
                    }
                    if (target.cannotPlayCard(Jie_Huo)) {
                        logger.error("对方被禁止使用截获")
                        (r as? HumanPlayer)?.sendErrorMessage("对方被禁止使用截获")
                        return null
                    }
                    val card = target.findCard(message.cardId)
                    if (card == null) {
                        logger.error("没有这张牌")
                        (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                        return null
                    }
                    val (ok, convertCardSkill) = target.canUseCardTypes(Jie_Huo, card, true)
                    if (!ok) {
                        logger.error("这张${card}不能当作截获使用")
                        (r as? HumanPlayer)?.sendErrorMessage("这张${card}不能当作截获使用")
                        return null
                    }
                    r.incrSeq()
                    logger.info("${r}让${target}使用了$card")
                    notifyUseSkill(enable = true, useCard = true)
                    r.game!!.fsm = fsm.copy(whoseFightTurn = target)
                    convertCardSkill?.onConvert(target)
                    card.execute(r.game!!, target)
                    return null
                }

                is use_wu_dao_tos -> {
                    if (fsm !is FightPhaseIdle) {
                        logger.error("误导的使用时机错误")
                        (r as? HumanPlayer)?.sendErrorMessage("误导的使用时机错误")
                        return null
                    }
                    if (target.cannotPlayCard(Wu_Dao)) {
                        logger.error("对方被禁止使用误导")
                        (r as? HumanPlayer)?.sendErrorMessage("对方被禁止使用误导")
                        return null
                    }
                    val card = target.findCard(message.cardId)
                    if (card == null) {
                        logger.error("没有这张牌")
                        (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                        return null
                    }
                    val (ok, convertCardSkill) = target.canUseCardTypes(Wu_Dao, card, true)
                    if (!ok) {
                        logger.error("这张${card}不能当作误导使用")
                        (r as? HumanPlayer)?.sendErrorMessage("这张${card}不能当作误导使用")
                        return null
                    }
                    if (message.targetPlayerId < 0 || message.targetPlayerId >= r.game!!.players.size) {
                        logger.error("目标错误")
                        (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                        return null
                    }
                    val target2 = r.game!!.players[r.getAbstractLocation(message.targetPlayerId)]!!
                    if (!target2.alive) {
                        logger.error("目标已死亡")
                        (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                        return null
                    }
                    val left = fsm.inFrontOfWhom.getNextLeftAlivePlayer()
                    val right = fsm.inFrontOfWhom.getNextRightAlivePlayer()
                    if (target2 === fsm.inFrontOfWhom || target2 !== left && target2 !== right) {
                        logger.error("误导只能选择情报当前人左右两边的人作为目标")
                        (r as? HumanPlayer)?.sendErrorMessage("误导只能选择情报当前人左右两边的人作为目标")
                        return null
                    }
                    r.incrSeq()
                    logger.info("${r}让${target}对${target2}使用了$card")
                    notifyUseSkill(enable = true, useCard = true)
                    r.game!!.fsm = fsm.copy(whoseFightTurn = target)
                    convertCardSkill?.onConvert(target)
                    card.execute(r.game!!, target, target2)
                    return null
                }

                is use_diao_bao_tos -> {
                    if (fsm !is FightPhaseIdle) {
                        logger.error("调包的使用时机错误")
                        (r as? HumanPlayer)?.sendErrorMessage("调包的使用时机错误")
                        return null
                    }
                    if (target.cannotPlayCard(Diao_Bao)) {
                        logger.error("对方被禁止使用调包")
                        (r as? HumanPlayer)?.sendErrorMessage("对方被禁止使用调包")
                        return null
                    }
                    val card = target.findCard(message.cardId)
                    if (card == null) {
                        logger.error("没有这张牌")
                        (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                        return null
                    }
                    val (ok, convertCardSkill) = target.canUseCardTypes(Diao_Bao, card, true)
                    if (!ok) {
                        logger.error("这张${card}不能当作调包使用")
                        (r as? HumanPlayer)?.sendErrorMessage("这张${card}不能当作调包使用")
                        return null
                    }
                    r.incrSeq()
                    logger.info("${r}让${target}使用了$card")
                    notifyUseSkill(enable = true, useCard = true)
                    r.game!!.fsm = fsm.copy(whoseFightTurn = target)
                    convertCardSkill?.onConvert(target)
                    card.execute(r.game!!, target)
                    return null
                }

                is cheng_qing_save_die_tos -> {
                    if (fsm !is WaitForChengQing) {
                        logger.error("澄清的使用时机错误")
                        (r as? HumanPlayer)?.sendErrorMessage("澄清的使用时机错误")
                        return null
                    }
                    if (!message.use) {
                        logger.error("参数错误")
                        (r as? HumanPlayer)?.sendErrorMessage("参数错误")
                        return null
                    }
                    if (target.cannotPlayCard(Cheng_Qing)) {
                        logger.error("对方被禁止使用澄清")
                        (r as? HumanPlayer)?.sendErrorMessage("对方被禁止使用澄清")
                        return null
                    }
                    val card = target.findCard(message.cardId)
                    if (card == null) {
                        logger.error("没有这张牌")
                        (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                        return null
                    }
                    val (ok, convertCardSkill) = target.canUseCardTypes(Cheng_Qing, card, true)
                    if (!ok) {
                        logger.error("这张${card}不能当作澄清使用")
                        (r as? HumanPlayer)?.sendErrorMessage("这张${card}不能当作澄清使用")
                        return null
                    }
                    val target2 = fsm.whoDie
                    val card2 = target2.messageCards.find { c -> c.id == message.targetCardId }
                    if (card2 == null) {
                        logger.error("没有这张情报")
                        (r as? HumanPlayer)?.sendErrorMessage("没有这张情报")
                        return null
                    }
                    if (!card2.isBlack()) {
                        logger.error("澄清只能对黑情报使用")
                        (r as? HumanPlayer)?.sendErrorMessage("澄清只能对黑情报使用")
                        return null
                    }
                    r.incrSeq()
                    logger.info("${r}让${target}对${target2}面前的${card2}使用了$card")
                    notifyUseSkill(enable = true, useCard = true)
                    r.game!!.fsm = fsm
                    convertCardSkill?.onConvert(target)
                    card.execute(r.game!!, target, target2, card2.id)
                    return null
                }

                else -> {
                    logger.error("错误的协议")
                    (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                    return null
                }
            }
        }

        private fun notifyUseSkill(enable: Boolean, useCard: Boolean) {
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_ru_bi_zhi_shi_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.enable = enable
                    builder.useCard = useCard
                    p.send(builder.build())
                }
            }
        }
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val r = e.whoseFightTurn
            !r.roleFaceUp || return false
            !r.game!!.isEarly || r.game!!.players.anyoneWillWinOrDie(e) || return false
            val target = r.game!!.players.filter {
                it!!.alive && it.isEnemy(r) && it.cards.isNotEmpty()
            }.randomOrNull() ?: return false
            GameExecutor.post(r.game!!, {
                val builder = skill_ru_bi_zhi_shi_a_tos.newBuilder()
                builder.targetPlayerId = r.getAlternativeLocation(target.location)
                skill.executeProtocol(r.game!!, r, builder.build())
            }, 3, TimeUnit.SECONDS)
            return true
        }

        fun ai2(e: WaitForChengQing, skill: ActiveSkill): Boolean {
            val r = e.askWhom
            !r.roleFaceUp || return false
            r.wantToSave(e.whoseTurn, e.whoDie) || return false
            val target = r.game!!.players.filter {
                it!!.alive && it.isEnemy(r) && it.cards.isNotEmpty()
            }.randomOrNull() ?: return false
            GameExecutor.post(r.game!!, {
                val builder = skill_ru_bi_zhi_shi_a_tos.newBuilder()
                builder.targetPlayerId = r.getAlternativeLocation(target.location)
                skill.executeProtocol(r.game!!, r, builder.build())
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}