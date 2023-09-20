package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.card_type

/**
 * 卡牌结算后
 *
 * @param player 出牌的
 * @param targetPlayer 目标角色
 * @param card 出的牌，有可能没出牌
 * @param cardType 出的牌的类型
 * @param nextFsm 接下来是什么阶段
 * @param discardAfterResolve 结算后是否进入弃牌堆
 * @param afterResolveFunc 结算后触发的一些效果，一般是用来清本回合使用次数
 */
data class OnFinishResolveCard(
    val player: Player,
    val targetPlayer: Player?,
    val card: Card?,
    val cardType: card_type,
    val nextFsm: Fsm,
    val discardAfterResolve: Boolean = true,
    val afterResolveFunc: () -> Unit = { },
) : Fsm {
    override fun resolve(): ResolveResult {
        val result = player.game!!.dealListeningSkill(player.location)
        if (result != null) return result
        if (discardAfterResolve)
            card?.let { player.game!!.deck.discard(it.getOriginCard()) }
        afterResolveFunc()
        return ResolveResult(nextFsm, true)
    }

    override fun toString(): String {
        return "${player}使用${card}结算后"
    }
}