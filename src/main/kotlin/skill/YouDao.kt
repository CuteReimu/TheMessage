package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Role.skill_you_dao_toc
import org.apache.logging.log4j.kotlin.logger

/**
 * SP李宁玉技能【诱导】：你使用【误导】后，摸一张牌。
 */
class YouDao : TriggeredSkill {
    override val skillId = SkillId.YOU_DAO

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        g.findEvent<FinishResolveCardEvent>(this) { event ->
            askWhom === event.player || return@findEvent false
            event.cardType == card_type.Wu_Dao
        } ?: return null
        logger.info("${askWhom}发动了[诱导]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_you_dao_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                p.send(builder.build())
            }
        }
        askWhom.draw(1)
        return null
    }
}