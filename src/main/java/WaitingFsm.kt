package com.fengsheng

import com.google.protobuf.GeneratedMessageV3

/**
 * 需要等待玩家操作的状态的状态机
 */
interface WaitingFsm : Fsm {
    /**
     * 玩家发送协议时的处理函数
     *
     * @return 处理的结果 [ResolveResult] ，返回 `null` 表示停留在这个状态机
     */
    fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult?
}