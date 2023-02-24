package com.fengsheng.skill

com.fengsheng.Player com.google.protobuf.GeneratedMessageV3
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimer

/**
 * 主动技能，一般是在出牌阶段空闲时点、争夺阶段空闲时点由玩家主动发动的技能
 */
interface ActiveSkill : Skill {
    /**
     * 玩家协议或机器人请求发动技能时调用
     */
    fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3)
}