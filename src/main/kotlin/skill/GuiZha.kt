package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.LiYou
import com.fengsheng.card.WeiBi
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.card_type.Li_You
import com.fengsheng.protos.Common.card_type.Wei_Bi
import com.fengsheng.protos.Role.skill_gui_zha_tos
import com.fengsheng.protos.skillGuiZhaToc
import com.fengsheng.protos.skillGuiZhaTos
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
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[诡诈]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[诡诈]一回合只能发动一次")
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
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        when (pb.cardType) {
            Wei_Bi -> if (!WeiBi.canUse(g, r, target, pb.wantType)) return
            Li_You -> if (!LiYou.canUse(g, r, target)) return
            else -> {
                logger.error("你只能视为使用了[威逼]或[利诱]：${pb.cardType}")
                (r as? HumanPlayer)?.sendErrorMessage("你只能视为使用了[威逼]或[利诱]：${pb.cardType}")
                return
            }
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}对${target}发动了[诡诈]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                p.send(skillGuiZhaToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    cardType = pb.cardType
                })
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
            return true
        }
    }
}