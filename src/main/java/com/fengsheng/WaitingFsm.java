package com.fengsheng;

import com.google.protobuf.GeneratedMessageV3;

/**
 * 需要等待玩家操作的状态的状态机
 */
public interface WaitingFsm extends Fsm {
    /**
     * 玩家发送协议时的处理函数
     *
     * @return 处理的结果 {@link ResolveResult} ，不能返回 {@code null}
     */
    ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message);
}
