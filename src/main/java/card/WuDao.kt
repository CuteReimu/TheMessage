package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.card.WuDao
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.use_wu_dao_toc
import org.apache.log4j.Logger
import java.util.concurrent.*

class WuDao : Card {
    constructor(id: Int, colors: Array<color>, direction: direction, lockable: Boolean) : super(
        id,
        colors,
        direction,
        lockable
    )

    constructor(id: Int, card: Card?) : super(id, card)

    /**
     * 仅用于“作为误导使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type: card_type
        get() = card_type.Wu_Dao

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            return false
        }
        if (g.qiangLingTypes.contains(type)) {
            log.error("误导被禁止使用了")
            return false
        }
        val target = args[0] as Player
        if (g.fsm !is FightPhaseIdle) {
            log.error("误导的使用时机不对")
            return false
        }
        val left: Player = fsm.inFrontOfWhom.getNextLeftAlivePlayer()
        val right: Player = fsm.inFrontOfWhom.getNextRightAlivePlayer()
        if (target === fsm.inFrontOfWhom || target !== left && target !== right) {
            log.error("误导只能选择情报当前人左右两边的人作为目标")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        log.info(r.toString() + "对" + target + "使用了" + this)
        val fsm = g.fsm as FightPhaseIdle
        r.deleteCard(id)
        val resolveFunc = Fsm {
            fsm.inFrontOfWhom = target
            fsm.whoseFightTurn = fsm.inFrontOfWhom
            g.deck.discard(originCard)
            for (player in g.players) {
                if (player is HumanPlayer) {
                    val builder = use_wu_dao_toc.newBuilder().setCard(toPbCard())
                    builder.playerId = player.getAlternativeLocation(r.location())
                    builder.targetPlayerId = player.getAlternativeLocation(target.location())
                    player.send(builder.build())
                }
            }
            ResolveResult(fsm, true)
        }
        g.resolve(OnUseCard(fsm.whoseTurn, r, null, this, card_type.Wu_Dao, r, resolveFunc))
    }

    override fun toString(): String {
        return Card.Companion.cardColorToString(colors) + "误导"
    }

    companion object {
        private val log = Logger.getLogger(WuDao::class.java)
        fun ai(e: FightPhaseIdle, card: Card): Boolean {
            val player = e.whoseFightTurn
            if (player.game.qiangLingTypes.contains(card_type.Wu_Dao)) return false
            val colors = e.messageCard.getColors()
            if (e.inFrontOfWhom === player && (e.isMessageCardFaceUp || player === e.whoseTurn) && colors.size == 1 && colors[0] != color.Black) return false
            val target = when (ThreadLocalRandom.current().nextInt(4)) {
                0 -> e.inFrontOfWhom.nextLeftAlivePlayer
                1 -> e.inFrontOfWhom.nextRightAlivePlayer
                else -> null
            } ?: return false
            GameExecutor.Companion.post(player.game, Runnable {
                val card0 =
                    if (card.type == card_type.Wu_Dao) card else Card.Companion.falseCard(card_type.Wu_Dao, card)
                card0.execute(player.game, player, target)
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}