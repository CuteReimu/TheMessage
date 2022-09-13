package com.fengsheng;

/**
 * 状态机
 */
public interface Fsm {
    /**
     * 处理函数
     *
     * @return 处理的结果 {@link ResolveResult} ，返回 {@code null} 表示停留在这个状态机
     */
    ResolveResult resolve();
}
