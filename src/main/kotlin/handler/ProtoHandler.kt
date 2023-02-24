package com.fengsheng.handler

import com.fengsheng.HumanPlayer

com.google.protobuf.GeneratedMessageV3
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimer

interface ProtoHandler {
    fun handle(player: HumanPlayer, message: GeneratedMessageV3)
}