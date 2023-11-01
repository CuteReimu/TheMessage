package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.cannotPlayCard
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

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
            log.error("你被禁止使用截获")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用截获")
            return false
        }
        return Companion.canUse(g, r)
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        log.info("${r}使用了$this")
        r.deleteCard(id)
        execute(this, g, r)
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}截获"
    }

    companion object {
        private val log = Logger.getLogger(JieHuo::class.java)
        fun canUse(g: Game, r: Player): Boolean {
            val fsm = g.fsm as? FightPhaseIdle
            if (r !== fsm?.whoseFightTurn) {
                log.error("截获的使用时机不对")
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
            !e.messageCard.isPureBlack() || player.identity == color.Black && player.secretTask == secret_task.Pioneer || return false
            Random.nextBoolean() || return false
            GameExecutor.post(player.game!!, { card.execute(player.game!!, player) }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}