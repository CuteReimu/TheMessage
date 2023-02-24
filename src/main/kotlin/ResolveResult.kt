package com.fengsheng

/**
 * 状态机的处理结果
 *
 * @param next            下一个状态机
 * @param continueResolve 是否直接继续处理下一个状态机
 */
data class ResolveResult(val next: Fsm?, val continueResolve: Boolean)