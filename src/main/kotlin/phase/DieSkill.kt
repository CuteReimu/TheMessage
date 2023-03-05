package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.skill.SkillId

/**
 * 死亡时的技能结算
 */
data class DieSkill(
    /**
     * 谁的回合
     */
    val whoseTurn: Player,
    /**
     * 死亡的顺序
     */
    val diedQueue: List<Player>,
    /**
     * 正在询问谁
     */
    val askWhom: Player,
    /**
     * 死亡结算后的下一个动作
     */
    val afterDieResolve: Fsm
) : Fsm {
    /**
     * 结算到dieQueue的第几个人的死亡事件了
     */
    var diedIndex = 0

    /**
     * 在结算死亡技能时，又有新的人获得三张黑色情报的顺序
     */
    var receiveOrder = ReceiveOrder()
    override fun resolve(): ResolveResult {
        if (askWhom !== diedQueue[diedIndex] && !askWhom.alive) return ResolveResult(DieSkillNext(this), true)
        val result = askWhom.game!!.dealListeningSkill()
        return result ?: ResolveResult(DieSkillNext(this), true)
    }

    /**
     * 进行下一个玩家死亡时的技能结算
     */
    private data class DieSkillNext(val dieSkill: DieSkill) : Fsm {
        override fun resolve(): ResolveResult {
            dieSkill.askWhom.resetSkillUseCount(SkillId.CHENG_ZHI)
            val players = dieSkill.whoseTurn.game!!.players
            var askWhom = dieSkill.askWhom.location
            while (true) {
                askWhom = (askWhom + 1) % players.size
                if (askWhom == dieSkill.whoseTurn.location) {
                    dieSkill.diedIndex++
                    if (dieSkill.diedIndex >= dieSkill.diedQueue.size) return ResolveResult(
                        WaitForDieGiveCard(
                            dieSkill.whoseTurn,
                            dieSkill.diedQueue,
                            dieSkill.receiveOrder,
                            dieSkill.afterDieResolve
                        ), true
                    )
                    return ResolveResult(dieSkill.copy(askWhom = dieSkill.whoseTurn), true)
                }
                if (players[askWhom] === dieSkill.diedQueue[dieSkill.diedIndex] || players[askWhom]!!.alive) {
                    return ResolveResult(dieSkill.copy(askWhom = players[askWhom]!!), true)
                }
            }
        }
    }
}