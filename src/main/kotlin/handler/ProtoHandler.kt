package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.google.protobuf.GeneratedMessage

interface ProtoHandler {
    fun handle(player: HumanPlayer, message: GeneratedMessage)
}
