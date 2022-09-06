package com.fengsheng;

/**
 * 状态机
 */
public interface Fsm {
    /**
     * 处理函数
     *
     * @return 处理函数
     */
    ResolveResult resolve();
}
