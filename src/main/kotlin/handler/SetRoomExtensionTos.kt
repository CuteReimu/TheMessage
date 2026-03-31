package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.setRoomExtensionToc

class SetRoomExtensionTos : AbstractProtoHandler<Fengsheng.set_room_extension_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.set_room_extension_tos) {
        r.roomExtension = pb.extension
        r.send(setRoomExtensionToc {
            extension = r.roomExtension
        })
    }
}
