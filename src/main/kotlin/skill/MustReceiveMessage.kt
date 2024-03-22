package com.fengsheng.skill

import com.fengsheng.phase.SendPhaseIdle

/**
 * 有这个技能的玩家，本回合必须接收情报/不能接收情报
 */
abstract class MustReceiveMessage : OneTurnSkill {
    override val skillId = SkillId.UNKNOWN

    /**
     * 是否必须接收情报
     */
    abstract fun mustReceive(sendPhase: SendPhaseIdle): Boolean

    /**
     * 是否不能接收情报
     */
    abstract fun cannotReceive(sendPhase: SendPhaseIdle): Boolean
}

/**
 * 判断[当前玩家][SendPhaseIdle.inFrontOfWhom]是否必须接收情报。
 *
 * @see MustReceiveMessage
 * @see cannotReceiveMessage
 */
fun SendPhaseIdle.mustReceiveMessage() = inFrontOfWhom === sender || lockedPlayers.any { it === inFrontOfWhom } ||
    inFrontOfWhom.skills.any { it is MustReceiveMessage && it.mustReceive(this) }

/**
 * 判断[当前玩家][SendPhaseIdle.inFrontOfWhom]是否不能接收情报。
 *
 * 特别地，如果[mustReceiveMessage]和[cannotReceiveMessage]都返回`true`，必须接收情报优先于不能接收情报。
 *
 * @see MustReceiveMessage
 * @see mustReceiveMessage
 */
fun SendPhaseIdle.cannotReceiveMessage() = inFrontOfWhom.skills.any { it is MustReceiveMessage && it.cannotReceive(this) }
