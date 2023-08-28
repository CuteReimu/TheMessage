package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
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
 * @param askWhom 遍历到了谁的技能
 * @param nextFsm 接下来是什么阶段
 * @param whereToGoFunc 卡牌移到哪
 */
data class OnFinishResolveCard(
    val whoseTurn: Player,
    val player: Player,
    val targetPlayer: Player?,
    val card: Card?,
    val cardType: card_type,
    val askWhom: Player,
    val nextFsm: Fsm,
    val whereToGoFunc: () -> Unit = { card?.let { player.game!!.deck.discard(it.getOriginCard()) } },
) : Fsm {
    override fun resolve(): ResolveResult {
        val result = whoseTurn.game!!.dealListeningSkill()
        return result ?: ResolveResult(OnFinishResolveCardNext(this), true)
    }

    override fun toString(): String {
        return "${player}使用${card}时"
    }

    private data class OnFinishResolveCardNext(val onUseCard: OnFinishResolveCard) : Fsm {
        override fun resolve(): ResolveResult {
            var askWhom = onUseCard.askWhom.location
            val players = onUseCard.askWhom.game!!.players
            while (true) {
                askWhom = (askWhom + 1) % players.size
                if (askWhom == onUseCard.player.location) {
                    onUseCard.whereToGoFunc()
                    return ResolveResult(onUseCard.nextFsm, true)
                }
                if (players[askWhom]!!.alive) {
                    return ResolveResult(onUseCard.copy(askWhom = players[askWhom]!!), true)
                }
            }
        }
    }
}