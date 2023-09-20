package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.card_type

/**
 * 使用卡牌时，成为卡牌目标时
 *
 * @param whoseTurn 谁的回合
 * @param player 出牌的
 * @param targetPlayer 目标角色
 * @param card 出的牌，有可能没出牌
 * @param cardType 出的牌的类型
 * @param resolveFunc 卡牌效果的结算函数
 * @param currentFsm 使用卡牌前的状态
 * @param valid 卡牌是否有效
 */
data class OnUseCard(
    val whoseTurn: Player,
    val player: Player,
    val targetPlayer: Player?,
    val card: Card?,
    val cardType: card_type,
    val resolveFunc: (Boolean) -> Fsm,
    val currentFsm: Fsm,
    val valid: Boolean = true,
) : Fsm {
    override fun resolve(): ResolveResult {
        val result = player.game!!.dealListeningSkill(player.location)
        return result ?: ResolveResult(resolveFunc(valid), true)
    }

    override fun toString(): String {
        return "${player}使用${card}时"
    }
}