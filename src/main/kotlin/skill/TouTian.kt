package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.card.JieHuo
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Role.skill_tou_tian_toc
import com.fengsheng.protos.Role.skill_tou_tian_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 鄭文先技能【偷天】：争夺阶段你可以翻开此角色牌，然后视为你使用了一张【截获】。
 */
class TouTian : InitialSkill, ActiveSkill {
    override val skillId = SkillId.TOU_TIAN

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (!JieHuo.canUse(g, r)) return
        if (r.roleFaceUp) {
            log.error("你现在正面朝上，不能发动[偷天]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[偷天]")
            return
        }
        val pb = message as skill_tou_tian_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        log.info("${r}发动了[偷天]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                skill_tou_tian_toc.newBuilder().setPlayerId(p.getAlternativeLocation(r.location)).build()
            )
        }
        JieHuo.execute(null, g, r)
    }

    companion object {
        private val log = Logger.getLogger(TouTian::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            !e.whoseFightTurn.roleFaceUp || return false
            val player = e.whoseFightTurn
            e.inFrontOfWhom !== player || return false
            !e.messageCard.isPureBlack() || return false
            Random.nextBoolean() || return false
            GameExecutor.post(e.whoseFightTurn.game!!, {
                skill.executeProtocol(
                    e.whoseFightTurn.game!!, e.whoseFightTurn, skill_tou_tian_tos.getDefaultInstance()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}