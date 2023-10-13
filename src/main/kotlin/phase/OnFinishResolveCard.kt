package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.card_type

/**
 * 卡牌结算后
 *
 * @param whoseTurn 谁的回合
 * @param player 出牌的
 * @param targetPlayer 目标角色
 * @param card 出的牌，有可能没出牌
 * @param cardType 出的牌的类型
 * @param nextFsm 接下来是什么阶段
 * @param discardAfterResolve 结算后是否进入弃牌堆
 */
class OnFinishResolveCard(
    override val whoseTurn: Player,
    val player: Player,
    val targetPlayer: Player?,
    val card: Card?,
    val cardType: card_type,
    val nextFsm: Fsm,
    var discardAfterResolve: Boolean = true
) : ProcessFsm() {
    override fun onSwitch() {
        whoseTurn.game!!.addEvent(FinishResolveCardEvent(this))
    }

    override fun resolve0(): ResolveResult {
        if (discardAfterResolve)
            card?.let { player.game!!.deck.discard(it.getOriginCard()) }
        return ResolveResult(nextFsm, true)
    }

    override fun toString(): String {
        return "${player}使用${card}结算后"
    }
}