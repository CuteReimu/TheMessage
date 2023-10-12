package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.use_ping_heng_toc
import com.fengsheng.skill.cannotPlayCard
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

class PingHeng : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为平衡使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = card_type.Ping_Heng

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            log.error("你被禁止使用平衡")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用平衡")
            return false
        }
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            log.error("平衡的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("平衡的使用时机不对")
            return false
        }
        val target = args[0] as Player
        if (r === target) {
            log.error("平衡不能对自己使用")
            (r as? HumanPlayer)?.sendErrorMessage("平衡不能对自己使用")
            return false
        }
        if (!target.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        val fsm = g.fsm as MainPhaseIdle
        log.info("${r}对${target}使用了$this")
        r.deleteCard(id)
        val resolveFunc = { _: Boolean ->
            for (player in g.players) {
                if (player is HumanPlayer) {
                    val builder = use_ping_heng_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.targetPlayerId = player.getAlternativeLocation(target.location)
                    builder.pingHengCard = toPbCard()
                    player.send(builder.build())
                }
            }
            if (r.cards.isNotEmpty()) r.game!!.addEvent(DiscardCardEvent(r, r))
            if (target.cards.isNotEmpty()) r.game!!.addEvent(DiscardCardEvent(r, target))
            g.playerDiscardCard(r, *r.cards.toTypedArray())
            g.playerDiscardCard(target, *target.cards.toTypedArray())
            r.draw(3)
            target.draw(3)
            OnFinishResolveCard(r, r, target, getOriginCard(), card_type.Ping_Heng, fsm)
        }
        g.resolve(ResolveCard(r, r, target, getOriginCard(), card_type.Ping_Heng, resolveFunc, fsm))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}平衡"
    }

    companion object {
        private val log = Logger.getLogger(PingHeng::class.java)
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.whoseTurn
            !player.cannotPlayCard(card_type.Ping_Heng) || return false
            player.cards.size <= 3 || return false
            val identity = player.identity
            val p = player.game!!.players.filter {
                if (it === player || !it!!.alive) false
                else if (identity != color.Black && identity == it.identity) it.cards.size <= 3
                else it.cards.size >= 3
            }.randomOrNull() ?: return false
            GameExecutor.post(player.game!!, { card.execute(player.game!!, player, p) }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}