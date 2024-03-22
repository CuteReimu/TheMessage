package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.WaitForChengQing
import com.fengsheng.protos.Role.skill_ji_zhi_tos
import com.fengsheng.protos.skillJiZhiToc
import com.fengsheng.protos.skillJiZhiTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 顾小梦技能【集智】：一名角色濒死时，或争夺阶段，你可以翻开此角色牌，然后摸四张牌。
 */
class JiZhi : ActiveSkill {
    override val skillId = SkillId.JI_ZHI

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        val fsm = g.fsm
        if ((fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn) && (fsm !is WaitForChengQing || r !== fsm.askWhom)) {
            logger.error("现在不是发动[急智]的时机")
            r.sendErrorMessage("现在不是发动[急智]的时机")
            return
        }
        if (r.roleFaceUp) {
            logger.error("角色面朝上时不能发动[急智]")
            r.sendErrorMessage("角色面朝上时不能发动[急智]")
            return
        }
        val pb = message as skill_ji_zhi_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}发动了[急智]")
        g.players.send { skillJiZhiToc { playerId = it.getAlternativeLocation(r.location) } }
        g.playerSetRoleFaceUp(r, true)
        r.draw(4)
        g.resolve(if (fsm is FightPhaseIdle) fsm.copy(whoseFightTurn = fsm.inFrontOfWhom) else fsm)
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val p = e.whoseFightTurn
            !p.roleFaceUp || return false
            p.game!!.players.anyoneWillWinOrDie(e) || return false
            GameExecutor.post(p.game!!, {
                skill.executeProtocol(p.game!!, p, skillJiZhiTos { })
            }, 3, TimeUnit.SECONDS)
            return true
        }

        fun ai2(e: WaitForChengQing, skill: ActiveSkill): Boolean {
            val p = e.askWhom
            !p.roleFaceUp || return false
            GameExecutor.post(p.game!!, {
                skill.executeProtocol(p.game!!, p, skillJiZhiTos { })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
