package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class JieHuo : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card?) : super(id, card)

    /**
     * 仅用于“作为截获使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type: card_type
        get() = card_type.Jie_Huo

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            return false
        }
        if (g.qiangLingTypes.contains(type)) {
            log.error("截获被禁止使用了")
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
                return false
            }
            if (r === fsm.inFrontOfWhom) {
                log.error("情报在自己面前不能使用截获")
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
            val resolveFunc = object : Fsm {
                override fun resolve(): ResolveResult {
                    if (card != null) g.deck.discard(card.getOriginCard())
                    for (player in g.players) {
                        if (player is HumanPlayer) {
                            val builder = Fengsheng.use_jie_huo_toc.newBuilder()
                            builder.playerId = player.getAlternativeLocation(r.location)
                            if (card != null) builder.card = card.toPbCard()
                            player.send(builder.build())
                        }
                    }
                    return ResolveResult(fsm.copy(inFrontOfWhom = r, whoseFightTurn = r), true)
                }
            }
            if (card != null)
                g.resolve(OnUseCard(fsm.whoseTurn, r, null, card, card_type.Jie_Huo, r, resolveFunc))
            else
                g.resolve(resolveFunc)
        }

        fun ai(e: FightPhaseIdle, card: Card): Boolean {
            val player = e.whoseFightTurn
            if (player.game!!.qiangLingTypes.contains(card_type.Jie_Huo)) return false
            val colors = e.messageCard.colors
            if (e.inFrontOfWhom === player || (e.isMessageCardFaceUp || player === e.whoseTurn) && colors.size == 1 && colors[0] == color.Black) return false
            if (Random.nextBoolean()) return false
            GameExecutor.post(player.game!!, { card.execute(player.game!!, player) }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}