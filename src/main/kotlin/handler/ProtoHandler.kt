package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.google.protobuf.GeneratedMessageV3

interface ProtoHandler {
    fun handle(player: HumanPlayer, message: GeneratedMessageV3)
}