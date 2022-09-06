package com.fengsheng;

/**
 * 状态机
 */
public interface Fsm {
    /**
     * 处理函数
     *
     * @return 处理的结果 {@link ResolveResult} ，不能返回 {@code null}
     */
    ResolveResult resolve();
}
