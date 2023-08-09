package com.fengsheng.phase

import com.fengsheng.Player
import com.fengsheng.card.count
import com.fengsheng.protos.Common.color.Black
import java.util.*

/**
 * 角色接收第三张黑色情报的顺序
 */
class ReceiveOrder : LinkedList<Player>() {
    fun addPlayerIfHasThreeBlack(player: Player) {
        if (contains(player)) return
        val count = player.messageCards.count(Black)
        if (count >= 3) add(player)
    }

    fun removePlayerIfNotHaveThreeBlack(player: Player) {
        val count = player.messageCards.count(Black)
        if (count < 3) remove(player)
    }
}

interface HasReceiveOrder {
    val receiveOrder: ReceiveOrder
}