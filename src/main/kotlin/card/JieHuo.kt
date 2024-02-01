package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.cannotPlayCard
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

class JieHuo : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为截获使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = card_type.Jie_Huo

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用截获")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用截获")
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
                (r as? HumanPlayer)?.sendErrorMessage("截获的使用时机不对")
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
                            val builder = Fengsheng.use_jie_huo_toc.newBuilder()
                            builder.playerId = player.getAlternativeLocation(r.location)
                            if (card != null) builder.card = card.toPbCard()
                            player.send(builder.build())
                        }
                    }
                    val newFsm = fsm.copy(inFrontOfWhom = r, whoseFightTurn = r)
                    for (p in g.players) { // 解决客户端动画问题
                        if (p is HumanPlayer) {
                            val builder = Fengsheng.notify_phase_toc.newBuilder()
                            builder.currentPlayerId = p.getAlternativeLocation(newFsm.whoseTurn.location)
                            builder.messagePlayerId = p.getAlternativeLocation(newFsm.inFrontOfWhom.location)
                            builder.waitingPlayerId = p.getAlternativeLocation(newFsm.whoseFightTurn.location)
                            builder.currentPhase = phase.Fight_Phase
                            if (newFsm.isMessageCardFaceUp)
                                builder.messageCard = newFsm.messageCard.toPbCard()
                            p.send(builder.build())
                        }
                    }
                    OnFinishResolveCard(fsm.whoseTurn, r, null, card?.getOriginCard(), card_type.Jie_Huo, newFsm)
                } else {
                    val newFsm = fsm.copy(whoseFightTurn = fsm.inFrontOfWhom)
                    OnFinishResolveCard(fsm.whoseTurn, r, null, card?.getOriginCard(), card_type.Jie_Huo, newFsm)
                }
            }
            g.resolve(
                ResolveCard(fsm.whoseTurn, r, null, card?.getOriginCard(), card_type.Jie_Huo, resolveFunc, fsm)
            )
        }

        fun ai(e: FightPhaseIdle, card: Card): Boolean {
            val player = e.whoseFightTurn
            !player.cannotPlayCard(card_type.Jie_Huo) || return false
            e.inFrontOfWhom !== player || return false
            val oldValue = player.calculateMessageCardValue(e.whoseTurn, e.inFrontOfWhom, e.messageCard)
            val newValue = player.calculateMessageCardValue(e.whoseTurn, player, e.messageCard)
            newValue > oldValue || return false
            GameExecutor.post(player.game!!, { card.execute(player.game!!, player) }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}