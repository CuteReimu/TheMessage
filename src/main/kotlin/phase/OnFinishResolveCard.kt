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
    override val needCheckWinAndDying = false

    override fun onSwitch() {
        whoseTurn.game!!.addEvent(FinishResolveCardEvent(this))
    }

    override fun resolve0(): ResolveResult {
        if (discardAfterResolve)
            card?.let { player.game!!.deck.discard(it.getOriginCard()) }

        // 更新AI身份推断系统：基于卡牌使用
        if (card != null) {
            whoseTurn.game!!.players.forEach { aiPlayer ->
                if (aiPlayer is RobotPlayer && aiPlayer.identityInference != null) {
                    // 确定使用行为是否有恶意
                    val isHostile = determineCardUsageHostility(player, targetPlayer, cardType)
                    aiPlayer.identityInference!!.updateBasedOnCardUsage(
                        playerLocation = player.location,
                        cardType = cardType,
                        targetLocation = targetPlayer?.location,
                        isHostile = isHostile
                    )
                }
            }
        }

        return ResolveResult(nextFsm, true)
    }

    /**
     * 判断卡牌使用是否具有恶意
     */
    private fun determineCardUsageHostility(player: Player, targetPlayer: Player?, cardType: card_type): Boolean {
        if (targetPlayer == null) return false

        // 基本的恶意判断逻辑
        return when (cardType) {
            card_type.Wei_Bi, card_type.Wu_Dao, card_type.Diao_Bao,
            card_type.Jie_Huo, card_type.Mi_Ling, card_type.Diao_Hu_Li_Shan -> {
                // 攻击性卡牌：如果对推测的敌人使用则认为是合理的，否则可能是恶意的
                !player.isInferredPartnerOrSelf(targetPlayer)
            }
            card_type.Cheng_Qing, card_type.Ping_Heng -> {
                // 澄清和平衡需要根据具体情况判断，这里简化处理
                // 可能需要更复杂的逻辑来判断是攻击性还是保护性使用
                false // 暂时认为不具恶意
            }
            card_type.Li_You -> {
                // 利诱通常不具恶意，除非是对明显的敌人使用
                !player.isInferredPartnerOrSelf(targetPlayer)
            }
            else -> false
        }
    }

    override fun toString(): String {
        return "${player}使用${card}结算后"
    }
}
