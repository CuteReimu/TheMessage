package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.card_type.Jie_Huo
import com.fengsheng.protos.Common.card_type.Wu_Dao
import com.fengsheng.protos.skillGuanHaiToc
import org.apache.logging.log4j.kotlin.logger

/**
 * 池镜海技能【观海】：你使用【截获】或【误导】时，在结算前先查看待收情报。
 */
class GuanHai : TriggeredSkill {
    override val skillId = SkillId.GUAN_HAI

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<UseCardEvent>(this) { event ->
            askWhom === event.player || return@findEvent false
            event.cardType == Jie_Huo || event.cardType == Wu_Dao
        } ?: return null
        logger.info("${askWhom}发动了[观海]")
        val fsm2 = event.currentFsm as FightPhaseIdle
        g.players.send {
            skillGuanHaiToc {
                playerId = it.getAlternativeLocation(askWhom.location)
                card = fsm2.messageCard.toPbCard()
            }
        }
        return null
    }
}