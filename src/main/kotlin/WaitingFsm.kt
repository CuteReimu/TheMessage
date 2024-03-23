package com.fengsheng

import com.google.protobuf.GeneratedMessage

/**
 * 需要等待玩家操作的状态的状态机
 */
interface WaitingFsm : Fsm {
    /**
     * 玩家发送协议时的处理函数
     *
     * 请注意：如果在处理过程中改变了[Player.skills]，一定不能返回 `null` ，否则会抛出[ConcurrentModificationException]
     *
     * @return 处理的结果 [ResolveResult] ，返回 `null` 表示停留在这个状态机
     */
    fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult?
}
