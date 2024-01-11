package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.ResolveResult
import com.fengsheng.skill.cannotPlayCardAndSkillForFightPhase

/**
 * 争夺阶段即将询问下一个人时
 *
 * @param fightPhase 原先那个人的 [FightPhaseIdle] （不是下一个人的）
 */
data class FightPhaseNext(val fightPhase: FightPhaseIdle) : Fsm {
    override fun resolve(): ResolveResult {
        val game = fightPhase.whoseFightTurn.game!!
        val players = game.players
        var whoseFightTurn = fightPhase.whoseFightTurn.location
        while (true) {
            whoseFightTurn = (whoseFightTurn + 1) % players.size
            if (whoseFightTurn == fightPhase.inFrontOfWhom.location) return ResolveResult(
                OnReceiveCard(
                    fightPhase.whoseTurn,
                    fightPhase.sender,
                    fightPhase.messageCard,
                    fightPhase.inFrontOfWhom
                ), true
            ) else if (players[whoseFightTurn]!!.alive &&
                !players[whoseFightTurn]!!.cannotPlayCardAndSkillForFightPhase(fightPhase)
            ) break
        }
        return ResolveResult(fightPhase.copy(whoseFightTurn = players[whoseFightTurn]!!), true)
    }
}