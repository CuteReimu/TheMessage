package com.fengsheng.phase

import com.fengsheng.Player
import com.fengsheng.protos.Common
import java.util.*

/**
 * 角色接收第三张黑色情报的顺序
 */
class ReceiveOrder : LinkedList<Player?>() {
    fun addPlayerIfHasThreeBlack(player: Player) {
        if (contains(player)) return
        var count = 0
        for (card in player.messageCards.values) {
            for (color in card.colors) {
                if (color == Common.color.Black) count++
            }
        }
        if (count >= 3) add(player)
    }

    fun removePlayerIfNotHaveThreeBlack(player: Player) {
        var count = 0
        for (card in player.messageCards.values) {
            for (color in card.colors) {
                if (color == Common.color.Black) count++
            }
        }
        if (count < 3) remove(player)
    }
}