package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.card.LiYou
import com.fengsheng.card.WeiBi
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Role.skill_gui_zha_toc
import com.fengsheng.protos.Role.skill_gui_zha_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 肥原龙川技能【诡诈】：出牌阶段限一次，你可以指定一名角色，然后视为你对其使用了一张【威逼】或【利诱】。
 */
class GuiZha : AbstractSkill(), ActiveSkill {
    override val skillId = SkillId.GUI_ZHA

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (r !== (g.fsm as? MainPhaseIdle)?.player) {
            log.error("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[诡诈]一回合只能发动一次")
            return
        }
        val pb = message as skill_gui_zha_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            log.error("目标已死亡")
            return
        }
        if (pb.cardType == card_type.Wei_Bi) {
            if (!WeiBi.canUse(g, r, target, pb.wantType)) return
        } else if (pb.cardType == card_type.Li_You) {
            if (!LiYou.canUse(g, r, target)) return
        } else {
            log.error("你只能视为使用了[威逼]或[利诱]：${pb.cardType}")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        log.info("${r}对${target}发动了[诡诈]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_gui_zha_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                builder.cardType = pb.cardType
                p.send(builder.build())
            }
        }
        if (pb.cardType == card_type.Wei_Bi) WeiBi.execute(null, g, r, target, pb.wantType)
        else if (pb.cardType == card_type.Li_You) LiYou.execute(null, g, r, target)
    }

    companion object {
        private val log = Logger.getLogger(GuiZha::class.java)
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.player
            if (player.getSkillUseCount(SkillId.GUI_ZHA) > 0) return false
            val game = player.game!!
            val nextCard = game.deck.peek(1).firstOrNull()
            val players =
                if (nextCard == null || nextCard.colors.size == 2) {
                    game.players.filter { it!!.alive }
                } else {
                    val (partners, enemies) = game.players.filter { it!!.alive }
                        .partition { player.isPartnerOrSelf(it!!) }
                    if (nextCard.isBlack()) enemies else partners
                }
            val p = players.randomOrNull() ?: return false
            GameExecutor.post(game, {
                val builder = skill_gui_zha_tos.newBuilder()
                builder.cardType = card_type.Li_You
                builder.targetPlayerId = e.player.getAlternativeLocation(p.location)
                skill.executeProtocol(game, e.player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}