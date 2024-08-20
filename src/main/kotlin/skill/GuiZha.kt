package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.LiYou
import com.fengsheng.card.WeiBi
import com.fengsheng.card.count
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Common.card_type.Li_You
import com.fengsheng.protos.Common.card_type.Wei_Bi
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Role.skill_gui_zha_tos
import com.fengsheng.protos.skillGuiZhaToc
import com.fengsheng.protos.skillGuiZhaTos
import com.fengsheng.skill.SkillId.SHOU_KOU_RU_PING
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 肥原龙川技能【诡诈】：出牌阶段限一次，你可以指定一名角色，然后视为你对其使用了一张【威逼】或【利诱】。
 */
class GuiZha : MainPhaseSkill() {
    override val skillId = SkillId.GUI_ZHA

    override val isInitialSkill = true

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            r.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[诡诈]一回合只能发动一次")
            r.sendErrorMessage("[诡诈]一回合只能发动一次")
            return
        }
        val pb = message as skill_gui_zha_tos
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
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            r.sendErrorMessage("目标已死亡")
            return
        }
        when (pb.cardType) {
            Wei_Bi -> if (!WeiBi.canUse(g, r, target, pb.wantType)) return
            Li_You -> if (!LiYou.canUse(g, r, target)) return
            else -> {
                logger.error("你只能视为使用了[威逼]或[利诱]：${pb.cardType}")
                r.sendErrorMessage("你只能视为使用了[威逼]或[利诱]：${pb.cardType}")
                return
            }
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}对${target}发动了[诡诈]")
        g.players.send {
            skillGuiZhaToc {
                playerId = it.getAlternativeLocation(r.location)
                targetPlayerId = it.getAlternativeLocation(target.location)
                cardType = pb.cardType
            }
        }
        if (pb.cardType == Wei_Bi) WeiBi.execute(null, g, r, target, pb.wantType)
        else if (pb.cardType == Li_You) LiYou.execute(null, g, r, target)
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseTurn
            player.getSkillUseCount(SkillId.GUI_ZHA) == 0 || return false
            val game = player.game!!

            // 当前场上只要有一个队友（包括自己）没听牌，或自己是搅局者，就利诱
            if (player.identity == Black && player.secretTask == Disturber || player.identity != Black &&
                !game.players.any { it!!.alive && it.isPartnerOrSelf(player) && it.messageCards.count(it.identity) == 2 }) {
                var target = player
                if (!game.isEarly) {
                    var value = 0.9
                    for (p in game.sortedFrom(game.players, player.location)) {
                        p.alive || continue
                        val result = player.calculateMessageCardValue(player, p, true)
                        if (result > value) {
                            value = result
                            target = p
                        }
                    }
                }
                GameExecutor.post(game, {
                    skill.executeProtocol(game, e.whoseTurn, skillGuiZhaTos {
                        cardType = Li_You
                        targetPlayerId = e.whoseTurn.getAlternativeLocation(target.location)
                    })
                }, 3, TimeUnit.SECONDS)
            }
            // 当前场上只要有一个队友（包括自己）听牌，或自己是除搅局者以外的神秘人，就威逼
            else {
                val availableCardType = listOf(Cheng_Qing, Jie_Huo, Diao_Bao, Wu_Dao)
                val yaPao = player.game!!.players.find {
                    it!!.alive && it.findSkill(SHOU_KOU_RU_PING) != null
                }
                if (yaPao === player) {
                    val p = player.game!!.players.run {
                        filter { it!!.alive && it.isPartner(player) }.randomOrNull()
                            ?: filter { it !== player && it!!.alive }.randomOrNull()
                    } ?: return false
                    val chosenCard = availableCardType.random()
                    GameExecutor.post(game, {
                        skill.executeProtocol(game, e.whoseTurn, skillGuiZhaTos {
                            cardType = Wei_Bi
                            targetPlayerId = p.location
                            wantType = chosenCard
                        })
                    }, 3, TimeUnit.SECONDS)
                    return true
                } else if (yaPao != null && player.isPartner(yaPao) && yaPao.getSkillUseCount(SHOU_KOU_RU_PING) == 0) {
                    val chosenCard = availableCardType.random()
                    GameExecutor.post(game, {
                        skill.executeProtocol(game, e.whoseTurn, skillGuiZhaTos {
                            cardType = Wei_Bi
                            targetPlayerId = yaPao.location
                            wantType = chosenCard
                        })
                    }, 3, TimeUnit.SECONDS)
                    return true
                }
                val p = player.game!!.players.filter {
                    it !== player && it!!.alive &&
                        (!it.roleFaceUp || !it.skills.any { s -> s is ChengFu || s is ShouKouRuPing || s is CunBuBuRang }) &&
                        it.isEnemy(player) &&
                        it.cards.any { card -> card.type in availableCardType }
                }.run {
                    filter { it!!.cards.any { card -> card.type in listOf(Jie_Huo, Wu_Dao, Diao_Bao) } }.ifEmpty { this }
                        .run { if (player.identity != Black) filter { it!!.identity != Black }.ifEmpty { this } else this }
                }.randomOrNull() ?: return false
                val chosenCard =
                    if (player.weiBiFailRate > 0) listOf(Jie_Huo, Wu_Dao, Diao_Bao).random() // 威逼成功后一定纯随机
                    else availableCardType.filter { cardType -> p.cards.any { it.type == cardType } }.run {
                        filter { it != Cheng_Qing }.ifEmpty { this }
                    }.random()
                GameExecutor.post(game, {
                    skill.executeProtocol(game, e.whoseTurn, skillGuiZhaTos {
                        cardType = Wei_Bi
                        targetPlayerId = e.whoseTurn.getAlternativeLocation(p.location)
                        wantType = chosenCard
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return true
        }
    }
}
