package com.fengsheng

/**
 * 状态机
 */
interface Fsm {
    val whoseTurn: Player

    /**
     * 处理函数。
     *
     * 请注意：如果在处理过程中改变了[Player.skills]，一定不能返回 `null` ，否则会抛出[ConcurrentModificationException]
     *
     * @return 处理的结果 [ResolveResult] ，返回 `null` 表示停留在这个状态机
     */
    fun resolve(): ResolveResult?
}