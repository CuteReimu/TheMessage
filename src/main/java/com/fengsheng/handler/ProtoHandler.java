package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.google.protobuf.GeneratedMessageV3;

public interface ProtoHandler {
    void handle(final HumanPlayer player, final GeneratedMessageV3 message);
}
