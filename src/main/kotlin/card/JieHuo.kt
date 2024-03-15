package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.card_type.Jie_Huo
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.phase.Fight_Phase
import com.fengsheng.protos.notifyPhaseToc
import com.fengsheng.protos.useJieHuoToc
import com.fengsheng.skill.cannotPlayCard
import org.apache.logging.log4j.kotlin.logger

class JieHuo : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为截获使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = Jie_Huo

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用截获")
            r.sendErrorMessage("你被禁止使用截获")
            return false
        }
        return Companion.canUse(g, r)
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        logger.info("${r}使用了$this")
        r.deleteCard(id)
        execute(this, g, r)
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}截获"
    }

    companion object {
        fun canUse(g: Game, r: Player): Boolean {
            val fsm = g.fsm as? FightPhaseIdle
            if (r !== fsm?.whoseFightTurn) {
                logger.error("截获的使用时机不对")
                r.sendErrorMessage("截获的使用时机不对")
                return false
            }
            return true
        }

        /**
         * 执行【截获】的效果
         *
         * @param card 使用的那张【截获】卡牌。可以为 `null` ，因为鄭文先技能【偷天】可以视为使用了【截获】。
         */
        fun execute(card: JieHuo?, g: Game, r: Player) {
            val fsm = g.fsm as FightPhaseIdle
            val resolveFunc = { valid: Boolean ->
                if (valid) {
                    for (player in g.players) {
                        if (player is HumanPlayer) {
                            player.send(useJieHuoToc {
                                playerId = player.getAlternativeLocation(r.location)
                                if (card != null) this.card = card.toPbCard()
                            })
                        }
                    }
                    val newFsm = fsm.copy(inFrontOfWhom = r, whoseFightTurn = r)
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
                    OnFinishResolveCard(fsm.whoseTurn, r, null, card?.getOriginCard(), Jie_Huo, newFsm)
                } else {
                    val newFsm = fsm.copy(whoseFightTurn = fsm.inFrontOfWhom)
                    OnFinishResolveCard(fsm.whoseTurn, r, null, card?.getOriginCard(), Jie_Huo, newFsm)
                }
            }
            g.resolve(
                ResolveCard(fsm.whoseTurn, r, null, card?.getOriginCard(), Jie_Huo, resolveFunc, fsm)
            )
        }
    }
}