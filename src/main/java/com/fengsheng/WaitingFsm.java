package com.fengsheng;

import com.google.protobuf.GeneratedMessageV3;

/**
 * 需要等待玩家操作的状态的状态机
 */
public interface WaitingFsm extends Runnable {
    /**
     * 玩家发送协议时的处理函数
     */
    void resolveProtocol(Player r, GeneratedMessageV3 message);
}
