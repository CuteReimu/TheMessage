package com.fengsheng.skill

import com.fengsheng.Gameimport

com.fengsheng.GameExecutorimport com.fengsheng.HumanPlayerimport com.fengsheng.Playerimport com.fengsheng.card.LiYouimport com.fengsheng.card.WeiBiimport com.fengsheng.phase.MainPhaseIdleimport com.fengsheng.protos.Common.card_typeimport com.fengsheng.protos.Roleimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 肥原龙川技能【诡诈】：出牌阶段限一次，你可以指定一名角色，然后视为你对其使用了一张【威逼】或【利诱】。
 */
class GuiZha : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.GUI_ZHA
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is MainPhaseIdle || r !== fsm.player) {
            log.error("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[诡诈]一回合只能发动一次")
            return
        }
        val pb = message as Role.skill_gui_zha_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]
        if (!target.isAlive) {
            log.error("目标已死亡")
            return
        }
        if (pb.cardType == card_type.Wei_Bi) {
            if (!WeiBi.Companion.canUse(g, r, target, pb.wantType)) return
        } else if (pb.cardType == card_type.Li_You) {
            if (!LiYou.Companion.canUse(g, r, target)) return
        } else {
            log.error("你只能视为使用了[威逼]或[利诱]：" + pb.cardType)
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        log.info(r.toString() + "对" + target + "发动了[诡诈]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                Role.skill_gui_zha_toc.newBuilder().setPlayerId(p.getAlternativeLocation(r.location()))
                    .setTargetPlayerId(p.getAlternativeLocation(target.location())).setCardType(pb.cardType).build()
            )
        }
        if (pb.cardType == card_type.Wei_Bi) WeiBi.Companion.execute(
            null,
            g,
            r,
            target,
            pb.wantType
        ) else if (pb.cardType == card_type.Li_You) LiYou.Companion.execute(null, g, r, target)
    }

    companion object {
        private val log = Logger.getLogger(GuiZha::class.java)
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.player.getSkillUseCount(SkillId.GUI_ZHA) > 0) return false
            val players: MutableList<Player> = ArrayList()
            for (p in e.player.game.players) if (p.isAlive) players.add(p)
            if (players.isEmpty()) return false
            val p = players[ThreadLocalRandom.current().nextInt(players.size)]
            GameExecutor.Companion.post(e.player.game, Runnable {
                skill.executeProtocol(
                    e.player.game, e.player, Role.skill_gui_zha_tos.newBuilder().setCardType(card_type.Li_You)
                        .setTargetPlayerId(e.player.getAlternativeLocation(p.location())).build()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}