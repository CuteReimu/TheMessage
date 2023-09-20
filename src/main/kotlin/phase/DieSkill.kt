package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.skill.SkillId

/**
 * 死亡时的技能结算
 *
 * @param whoseTurn 谁的回合
 * @param diedQueue 死亡的顺序
 * @param afterDieResolve 死亡结算后的下一个动作
 * @param diedIndex 结算到dieQueue的第几个人的死亡事件了
 * @param receiveOrder 在结算死亡技能时，又有新的人获得三张黑色情报的顺序
 */
data class DieSkill(
    val whoseTurn: Player,
    val diedQueue: List<Player>,
    val afterDieResolve: Fsm,
    val diedIndex: Int = 0,
    override val receiveOrder: ReceiveOrder = ReceiveOrder()
) : Fsm, HasReceiveOrder {
    override fun resolve(): ResolveResult {
        val result = whoseTurn.game!!.dealListeningSkill(whoseTurn.location, true)
        return result ?: ResolveResult(DieSkillNext(this), true)
    }

    /**
     * 进行下一个玩家死亡时的技能结算
     */
    private data class DieSkillNext(val dieSkill: DieSkill) : Fsm {
        override fun resolve(): ResolveResult {
            val players = dieSkill.whoseTurn.game!!.players
            players.forEach { it!!.resetSkillUseCount(SkillId.CHENG_ZHI) }
            val index = dieSkill.diedIndex + 1
            if (index < dieSkill.diedQueue.size)
                return ResolveResult(dieSkill.copy(diedIndex = index), true)
            return ResolveResult(
                WaitForDieGiveCard(
                    dieSkill.whoseTurn,
                    dieSkill.diedQueue,
                    dieSkill.receiveOrder,
                    dieSkill.afterDieResolve
                ), true
            )
        }
    }
}