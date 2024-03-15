package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.JieHuo
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.card_type.Jie_Huo
import com.fengsheng.protos.Common.card_type.Wu_Dao
import com.fengsheng.protos.Role.skill_tou_tian_tos
import com.fengsheng.protos.skillTouTianToc
import com.fengsheng.protos.skillTouTianTos
import com.fengsheng.skill.SkillId.TOU_TIAN
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 鄭文先技能【偷天】：争夺阶段你可以翻开此角色牌，然后视为你使用了一张【截获】。
 */
class TouTian : ActiveSkill {
    override val skillId = TOU_TIAN

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        if (!JieHuo.canUse(g, r)) return
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[偷天]")
            r.sendErrorMessage("你现在正面朝上，不能发动[偷天]")
            return
        }
        val pb = message as skill_tou_tian_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        logger.info("${r}发动了[偷天]")
        g.players.send { skillTouTianToc { playerId = it.getAlternativeLocation(r.location) } }
        JieHuo.execute(null, g, r)
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            !player.roleFaceUp || return false
            !player.game!!.isEarly || player.game!!.players.anyoneWillWinOrDie(e) || return false
            e.inFrontOfWhom !== player || return false
            val oldValue = player.calculateMessageCardValue(e.whoseTurn, e.inFrontOfWhom, e.messageCard)
            val newValue = player.calculateMessageCardValue(e.whoseTurn, player, e.messageCard)
            newValue > oldValue || return false
            val result = player.calFightPhase(e)
            if (result != null && result.cardType in listOf(Jie_Huo, Wu_Dao) && result.value >= newValue) return false
            GameExecutor.post(e.whoseFightTurn.game!!, {
                skill.executeProtocol(e.whoseFightTurn.game!!, e.whoseFightTurn, skillTouTianTos { })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}