package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.card_type

/**
 * 结算卡牌效果
 *
 * @param whoseTurn 谁的回合
 * @param player 出牌的
 * @param targetPlayer 目标角色
 * @param card 出的牌，有可能没出牌
 * @param cardType 出的牌的类型
 * @param resolveFunc 卡牌效果的结算函数
 * @param currentFsm 使用卡牌前的状态
 * @param valid 卡牌是否有效
 * @param discardAfterResolve 结算后是否置入弃牌堆
 */
class ResolveCard(
    override val whoseTurn: Player,
    val player: Player,
    val targetPlayer: Player?,
    val card: Card?,
    val cardType: card_type,
    val resolveFunc: (Boolean) -> Fsm,
    val currentFsm: ProcessFsm,
    var valid: Boolean = true
) : ProcessFsm() {
    override val needCheckWinAndDying = false
    
    override fun onSwitch() {
        whoseTurn.game!!.addEvent(UseCardEvent(this))
    }

    override fun resolve0(): ResolveResult {
        return ResolveResult(resolveFunc(valid), true)
    }

    override fun toString(): String {
        return "${player}使用${card}结算卡牌效果时"
    }
}