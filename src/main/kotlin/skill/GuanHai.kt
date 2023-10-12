package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.card_type.Jie_Huo
import com.fengsheng.protos.Common.card_type.Wu_Dao
import com.fengsheng.protos.Role.skill_guan_hai_toc
import org.apache.log4j.Logger

/**
 * 池镜海技能【观海】：你使用【截获】或【误导】时，在结算前先查看待收情报。
 */
class GuanHai : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.GUAN_HAI

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<UseCardEvent>(this) { event ->
            askWhom === event.player || return@findEvent false
            event.cardType == Jie_Huo || event.cardType == Wu_Dao
        } ?: return null
        log.info("${askWhom}发动了[观海]")
        val fsm2 = event.currentFsm as FightPhaseIdle
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_guan_hai_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                builder.card = fsm2.messageCard.toPbCard()
                p.send(builder.build())
            }
        }
        return null
    }

    companion object {
        private val log = Logger.getLogger(GuanHai::class.java)
    }
}