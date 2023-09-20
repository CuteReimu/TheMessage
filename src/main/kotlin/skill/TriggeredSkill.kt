package com.fengsheng.skill

import com.fengsheng.Fsm
import com.fengsheng.Game
import com.fengsheng.Player
import com.fengsheng.ResolveResult

/**
 * 触发的技能，一般是使用卡牌时、情报接收阶段、死亡前触发的技能
 */
interface TriggeredSkill : Skill {
    /**
     *  * 对于自动发动的技能，判断并发动这个技能时会调用这个函数
     *  * 对于使用卡牌、接收情报、死亡前询问发动的技能，判断并询问这个技能时会调用这个函数
     *
     * @return 如果返回值不为 `null` ，说明满足技能触发的条件，将会进入返回的 [Fsm]
     */
    fun execute(g: Game, askWhom: Player): ResolveResult?
}