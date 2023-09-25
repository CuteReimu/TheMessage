package com.fengsheng.phase

import com.fengsheng.Config
import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import org.apache.log4j.Logger
import java.util.*

/**
 * 濒死求澄清
 *
 * @param whoseTurn 谁的回合
 * @param whoDie 正在结算谁的濒死
 * @param askWhom 正在询问谁是否使用澄清
 * @param dyingQueue 结算濒死的顺序
 * @param diedQueue 死亡的顺序
 * @param afterDieResolve 濒死结算后的下一个动作
 */
data class WaitForChengQing(
    val whoseTurn: Player,
    val whoDie: Player,
    val askWhom: Player,
    val dyingQueue: Queue<Player>,
    val diedQueue: ArrayList<Player>,
    val afterDieResolve: Fsm
) : Fsm {
    override fun resolve(): ResolveResult? {
        log.info("正在询问${askWhom}是否使用澄清")
        for (p in askWhom.game!!.players) {
            p!!.notifyAskForChengQing(whoDie, askWhom, Config.WaitSecond)
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