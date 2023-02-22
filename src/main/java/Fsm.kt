package com.fengsheng

/**
 * 状态机
 */
interface Fsm {
    /**
     * 处理函数
     *
     * @return 处理的结果 [ResolveResult] ，返回 `null` 表示停留在这个状态机
     */
    fun resolve(): ResolveResult?
}