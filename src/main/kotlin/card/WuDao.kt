package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.card_type.Wu_Dao
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.phase.Fight_Phase
import com.fengsheng.protos.notifyPhaseToc
import com.fengsheng.protos.useWuDaoToc
import com.fengsheng.skill.cannotPlayCard
import org.apache.logging.log4j.kotlin.logger

class WuDao : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为误导使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = Wu_Dao

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用误导")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用误导")
            return false
        }
        val target = args[0] as Player
        val fsm = g.fsm as? FightPhaseIdle
        if (fsm == null) {
            logger.error("误导的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("误导的使用时机不对")
            return false
        }
        val left = fsm.inFrontOfWhom.getNextLeftAlivePlayer()
        val right = fsm.inFrontOfWhom.getNextRightAlivePlayer()
        if (target === fsm.inFrontOfWhom || target !== left && target !== right) {
            logger.error("误导只能选择情报当前人左右两边的人作为目标")
            (r as? HumanPlayer)?.sendErrorMessage("误导只能选择情报当前人左右两边的人作为目标")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        logger.info("${r}对${target}使用了$this")
        g.resolve(onUseCard(this, g, r, target))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}误导"
    }

    companion object {
        /**
         * 执行【误导】的效果。示例：
         * ```
         * g.resolve(WuDao.onUseCard(card, g, r, target))
         * ```
         * @param card 使用的那张【误导】卡牌。可以为 `null` ，因为SP阿芙罗拉技能【应变自如】可以视为使用了【误导】。
         * @return 返回[ResolveCard]，要自行调用[Game.resolve]
         */
        fun onUseCard(card: Card?, g: Game, r: Player, target: Player): ResolveCard {
            val fsm = g.fsm as FightPhaseIdle
            card?.apply { r.deleteCard(id) }
            val resolveFunc = { valid: Boolean ->
                if (valid) {
                    for (player in g.players) {
                        if (player is HumanPlayer) {
                            player.send(useWuDaoToc {
                                card?.let { this.card = it.toPbCard() }
                                playerId = player.getAlternativeLocation(r.location)
                                targetPlayerId = player.getAlternativeLocation(target.location)
                            })
                        }
                    }
                    val newFsm = fsm.copy(inFrontOfWhom = target, whoseFightTurn = target)
                    for (p in g.players) { // 解决客户端动画问题
                        if (p is HumanPlayer) {
                            p.send(notifyPhaseToc {
                                currentPlayerId = p.getAlternativeLocation(newFsm.whoseTurn.location)
                                messagePlayerId = p.getAlternativeLocation(newFsm.inFrontOfWhom.location)
                                waitingPlayerId = p.getAlternativeLocation(newFsm.whoseFightTurn.location)
                                currentPhase = Fight_Phase
                                if (newFsm.isMessageCardFaceUp) messageCard = newFsm.messageCard.toPbCard()
                            })
                        }
                    }
                    OnFinishResolveCard(fsm.whoseTurn, r, target, card?.getOriginCard(), Wu_Dao, newFsm)
                } else {
                    val newFsm = fsm.copy(whoseFightTurn = fsm.inFrontOfWhom)
                    OnFinishResolveCard(fsm.whoseTurn, r, target, card?.getOriginCard(), Wu_Dao, newFsm)
                }
            }
            return ResolveCard(
                fsm.whoseTurn, r, target, card?.getOriginCard(), Wu_Dao, resolveFunc, fsm,
                valid = target !== fsm.inFrontOfWhom
            )
        }
    }
}