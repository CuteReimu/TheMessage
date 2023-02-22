package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.protos.Common.cardimport

com.fengsheng.protos.Common.card_type
/**
 * 使用卡牌时，成为卡牌目标时
 */
class OnUseCard(
    /**
     * 谁的回合
     */
    var whoseTurn: Player,
    /**
     * 出牌的人
     */
    var player: Player,
    /**
     * 目标角色
     */
    var targetPlayer: Player?,
    /**
     * 出的牌，有可能没出牌
     */
    var card: Card?,
    /**
     * 出的牌的类型
     */
    var cardType: card_type,
    /**
     * 遍历到了谁的技能
     */
    var askWhom: Player,
    /**
     * 卡牌效果的结算函数
     */
    var resolveFunc: Fsm
) : Fsm {
    override fun resolve(): ResolveResult? {
        val result = whoseTurn.game.dealListeningSkill()
        return result ?: ResolveResult(OnUseCardNext(this), true)
    }

    override fun toString(): String {
        return player.toString() + "使用" + card + "时"
    }

    private class OnUseCardNext(onUseCard: OnUseCard) : Fsm {
        override fun resolve(): ResolveResult? {
            var askWhom = onUseCard.askWhom.location()
            val players = onUseCard.askWhom.game.players
            while (true) {
                askWhom = (askWhom + 1) % players.size
                if (askWhom == onUseCard.player.location()) {
                    return onUseCard.resolveFunc.resolve()
                }
                if (players[askWhom].isAlive) {
                    onUseCard.askWhom = players[askWhom]
                    return ResolveResult(onUseCard, true)
                }
            }
        }

        val onUseCard: OnUseCard

        init {
            this.card = card
            this.sendPhase = sendPhase
            this.r = r
            this.target = target
            this.card = card
            this.wantType = wantType
            this.r = r
            this.target = target
            this.card = card
            this.player = player
            this.card = card
            this.card = card
            this.drawCards = drawCards
            this.players = players
            this.mainPhaseIdle = mainPhaseIdle
            this.dieSkill = dieSkill
            this.player = player
            this.player = player
            this.onUseCard = onUseCard
        }
    }
}