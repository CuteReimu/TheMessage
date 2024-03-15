package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.card_type.Diao_Bao
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.phase.Fight_Phase
import com.fengsheng.protos.notifyPhaseToc
import com.fengsheng.protos.useDiaoBaoToc
import com.fengsheng.skill.cannotPlayCard
import org.apache.logging.log4j.kotlin.logger

class DiaoBao : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为调包使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = Diao_Bao

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用调包")
            r.sendErrorMessage("你被禁止使用调包")
            return false
        }
        if (r !== (g.fsm as? FightPhaseIdle)?.whoseFightTurn) {
            logger.error("调包的使用时机不对")
            r.sendErrorMessage("调包的使用时机不对")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val fsm = g.fsm as FightPhaseIdle
        logger.info("${r}使用了$this")
        r.deleteCard(id)
        val resolveFunc = { _: Boolean ->
            val oldCard = fsm.messageCard
            g.deck.discard(oldCard)
            for (player in g.players) {
                if (player is HumanPlayer) {
                    player.send(useDiaoBaoToc {
                        oldMessageCard = oldCard.toPbCard()
                        playerId = player.getAlternativeLocation(r.location)
                        if (player === r) cardId = id
                    })
                }
            }
            val newFsm = fsm.copy(
                messageCard = getOriginCard(),
                isMessageCardFaceUp = false,
                whoseFightTurn = fsm.inFrontOfWhom
            )
            for (p in g.players) { // 解决客户端动画问题
                if (p is HumanPlayer) {
                    p.send(notifyPhaseToc {
                        currentPlayerId = p.getAlternativeLocation(newFsm.whoseTurn.location)
                        messagePlayerId = p.getAlternativeLocation(newFsm.inFrontOfWhom.location)
                        waitingPlayerId = p.getAlternativeLocation(newFsm.whoseFightTurn.location)
                        currentPhase = Fight_Phase
                    })
                }
            }
            OnFinishResolveCard(
                fsm.whoseTurn,
                r,
                null,
                getOriginCard(),
                Diao_Bao,
                newFsm,
                discardAfterResolve = false
            )
        }
        g.resolve(ResolveCard(fsm.whoseTurn, r, null, getOriginCard(), Diao_Bao, resolveFunc, fsm))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}调包"
    }
}