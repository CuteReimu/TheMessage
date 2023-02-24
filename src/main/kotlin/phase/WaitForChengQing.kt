package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import org.apache.log4j.Logger
import java.util.*

data class WaitForChengQing(
    /**
     * 谁的回合
     */
    val whoseTurn: Player,
    /**
     * 正在结算谁的濒死
     */
    val whoDie: Player,
    /**
     * 正在结算谁是否使用澄清
     */
    val askWhom: Player,
    /**
     * 结算濒死的顺序
     */
    val dyingQueue: Queue<Player>,
    /**
     * 死亡的顺序
     */
    val diedQueue: ArrayList<Player>,
    /**
     * 濒死结算后的下一个动作
     */
    val afterDieResolve: Fsm
) : Fsm {
    override fun resolve(): ResolveResult? {
        log.info("正在询问${askWhom}是否使用澄清")
        for (p in askWhom.game!!.players) {
            p!!.notifyAskForChengQing(whoDie, askWhom, 15)
        }
        return null
    }

    override fun toString(): String {
        return "${whoseTurn}的回合，${whoDie}濒死，向${askWhom}求澄清"
    }

    companion object {
        private val log = Logger.getLogger(WaitForChengQing::class.java)
    }
}