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
 * @param askWhom 遍历到了谁的技能
 * @param resolveFunc 卡牌效果的结算函数
 * @param currentFsm 使用卡牌前的状态
 */
data class OnUseCard(
    val whoseTurn: Player,
    val player: Player,
    val targetPlayer: Player?,
    val card: Card?,
    val cardType: card_type,
    val resolveFunc: (Boolean) -> Fsm,
    val currentFsm: Fsm,
    val askWhom: Player = player,
    val valid: Boolean = true,
) : Fsm {
    override fun resolve(): ResolveResult {
        val result = player.game!!.dealListeningSkill()
        return result ?: ResolveResult(OnUseCardNext(this), true)
    }

    override fun toString(): String {
        return "${player}使用${card}时"
    }

    private data class OnUseCardNext(val onUseCard: OnUseCard) : Fsm {
        override fun resolve(): ResolveResult {
            var askWhom = onUseCard.askWhom.location
            val players = onUseCard.askWhom.game!!.players
            while (true) {
                askWhom = (askWhom + 1) % players.size
                if (askWhom == onUseCard.player.location) {
                    return ResolveResult(onUseCard.resolveFunc(onUseCard.valid), true)
                }
                if (players[askWhom]!!.alive) {
                    return ResolveResult(onUseCard.copy(askWhom = players[askWhom]!!), true)
                }
            }
        }
    }
}