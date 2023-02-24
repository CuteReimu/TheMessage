package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.card_type

/**
 * 使用卡牌时，成为卡牌目标时
 */
data class OnUseCard(
    /**
     * 谁的回合
     */
    val whoseTurn: Player,
    /**
     * 出牌的人
     */
    val player: Player,
    /**
     * 目标角色
     */
    val targetPlayer: Player?,
    /**
     * 出的牌，有可能没出牌
     */
    val card: Card?,
    /**
     * 出的牌的类型
     */
    val cardType: card_type,
    /**
     * 遍历到了谁的技能
     */
    val askWhom: Player,
    /**
     * 卡牌效果的结算函数
     */
    val resolveFunc: Fsm
) : Fsm {
    override fun resolve(): ResolveResult {
        val result = whoseTurn.game!!.dealListeningSkill()
        return result ?: ResolveResult(OnUseCardNext(this), true)
    }

    override fun toString(): String {
        return player.toString() + "使用" + card + "时"
    }

    private data class OnUseCardNext(val onUseCard: OnUseCard) : Fsm {
        override fun resolve(): ResolveResult? {
            var askWhom = onUseCard.askWhom.location
            val players = onUseCard.askWhom.game!!.players
            while (true) {
                askWhom = (askWhom + 1) % players.size
                if (askWhom == onUseCard.player.location) {
                    return onUseCard.resolveFunc.resolve()
                }
                if (players[askWhom]!!.alive) {
                    return ResolveResult(onUseCard.copy(askWhom = players[askWhom]!!), true)
                }
            }
        }
    }
}