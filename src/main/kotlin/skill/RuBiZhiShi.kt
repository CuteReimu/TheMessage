package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.WaitForChengQing
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Fengsheng.*
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
            r.sendErrorMessage("操作太晚了")
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

    data class excuteRuBiZhiShi(val fsm: Fsm, val r: Player, val target: Player) : WaitingFsm {
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
            if (message is skill_ru_bi_zhi_shi_b_tos) {
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
                log.info("${r}选择弃掉${target}的$card")
                r.incrSeq()
                for (p in g.players) {
                    if (p is HumanPlayer) {
                        val builder = skill_ru_bi_zhi_shi_b_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(r.location)
                        builder.targetPlayerId = p.getAlternativeLocation(target.location)
                        builder.enable = message.enable
                        p.send(builder.build())
                    }
                }
                r.game!!.playerDiscardCard(target, card)
                return ResolveResult(fsm, true)
            }
            when (message) {
                is use_jie_huo_tos -> {
                    if (fsm !is FightPhaseIdle) {
                        log.error("截获的使用时机错误")
                        (r as? HumanPlayer)?.sendErrorMessage("截获的使用时机错误")
                        return null
                    }
                    if (Jie_Huo in r.game!!.qiangLingTypes) {
                        log.error("截获被禁止使用了")
                        (r as? HumanPlayer)?.sendErrorMessage("截获被禁止使用了")
                        return null
                    }
                    if (fsm.inFrontOfWhom === target) {
                        log.error("情报在目标角色面前，目标角色不能使用截获")
                        (r as? HumanPlayer)?.sendErrorMessage("情报在目标角色面前，目标角色不能使用截获")
                        return null
                    }
                    val card = target.findCard(message.cardId)
                    if (card == null) {
                        log.error("没有这张牌")
                        (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                        return null
                    }
                    if (card.type != Jie_Huo) {
                        log.error("这张牌不是截获")
                        (r as? HumanPlayer)?.sendErrorMessage("这张牌不是截获")
                        return null
                    }
                    log.info("${r}让${target}使用了$card")
                    r.game!!.fsm = fsm.copy(whoseFightTurn = target)
                    card.execute(r.game!!, target)
                    return null
                }

                is use_wu_dao_tos -> {
                    if (fsm !is FightPhaseIdle) {
                        log.error("误导的使用时机错误")
                        (r as? HumanPlayer)?.sendErrorMessage("误导的使用时机错误")
                        return null
                    }
                    if (Wu_Dao in r.game!!.qiangLingTypes) {
                        log.error("误导被禁止使用了")
                        (r as? HumanPlayer)?.sendErrorMessage("误导被禁止使用了")
                        return null
                    }
                    val card = target.findCard(message.cardId)
                    if (card == null) {
                        log.error("没有这张牌")
                        (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                        return null
                    }
                    if (card.type != Wu_Dao) {
                        log.error("这张牌不是误导")
                        (r as? HumanPlayer)?.sendErrorMessage("这张牌不是误导")
                        return null
                    }
                    if (message.targetPlayerId < 0 || message.targetPlayerId >= r.game!!.players.size) {
                        log.error("目标错误")
                        (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                        return null
                    }
                    val target2 = r.game!!.players[r.getAbstractLocation(message.targetPlayerId)]!!
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
                    log.info("${r}让${target}对${target2}使用了$card")
                    r.game!!.fsm = fsm.copy(whoseFightTurn = target)
                    card.execute(r.game!!, target, target2)
                    return null
                }

                is use_diao_bao_tos -> {
                    if (fsm !is FightPhaseIdle) {
                        log.error("调包的使用时机错误")
                        (r as? HumanPlayer)?.sendErrorMessage("调包的使用时机错误")
                        return null
                    }
                    if (Diao_Bao in r.game!!.qiangLingTypes) {
                        log.error("调包被禁止使用了")
                        (r as? HumanPlayer)?.sendErrorMessage("调包被禁止使用了")
                        return null
                    }
                    val card = target.findCard(message.cardId)
                    if (card == null) {
                        log.error("没有这张牌")
                        (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                        return null
                    }
                    if (card.type != Diao_Bao) {
                        log.error("这张牌不是调包")
                        (r as? HumanPlayer)?.sendErrorMessage("这张牌不是调包")
                        return null
                    }
                    log.info("${r}让${target}使用了$card")
                    r.game!!.fsm = fsm.copy(whoseFightTurn = target)
                    card.execute(r.game!!, target)
                    return null
                }

                is use_cheng_qing_tos -> {
                    if (fsm !is WaitForChengQing) {
                        log.error("澄清的使用时机错误")
                        (r as? HumanPlayer)?.sendErrorMessage("澄清的使用时机错误")
                        return null
                    }
                    if (Cheng_Qing in r.game!!.qiangLingTypes) {
                        log.error("澄清被禁止使用了")
                        (r as? HumanPlayer)?.sendErrorMessage("澄清被禁止使用了")
                        return null
                    }
                    val card = target.findCard(message.cardId)
                    if (card == null) {
                        log.error("没有这张牌")
                        (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                        return null
                    }
                    if (card.type != Cheng_Qing) {
                        log.error("这张牌不是澄清")
                        (r as? HumanPlayer)?.sendErrorMessage("这张牌不是澄清")
                        return null
                    }
                    if (message.playerId < 0 || message.playerId >= r.game!!.players.size) {
                        log.error("目标错误")
                        (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                        return null
                    }
                    val target2 = r.game!!.players[r.getAbstractLocation(message.playerId)]!!
                    if (!target2.alive) {
                        log.error("目标已死亡")
                        (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                        return null
                    }
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
                    log.info("${r}让${target}对${target2}面前的${card2}使用了$card")
                    card.execute(r.game!!, target, target2, card2)
                    return null
                }

                else -> {
                    log.error("错误的协议")
                    (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                    return null
                }
            }
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