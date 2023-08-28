package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.use_ping_heng_toc
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class PingHeng : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为平衡使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type: card_type
        get() = card_type.Ping_Heng

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁闭了，不能出牌")
            return false
        }
        if (g.qiangLingTypes.contains(type)) {
            log.error("平衡被禁止使用了")
            (r as? HumanPlayer)?.sendErrorMessage("平衡被禁止使用了")
            return false
        }
        if (r !== (g.fsm as? MainPhaseIdle)?.player) {
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
        log.info("${r}对${target}使用了$this")
        r.deleteCard(id)
        val resolveFunc = { _: Boolean ->
            for (player in g.players) {
                (player as? HumanPlayer)?.send(
                    use_ping_heng_toc.newBuilder()
                        .setPlayerId(player.getAlternativeLocation(r.location))
                        .setTargetPlayerId(player.getAlternativeLocation(target.location))
                        .setPingHengCard(toPbCard()).build()
                )
            }
            g.playerDiscardCard(r, *r.cards.toTypedArray())
            g.playerDiscardCard(target, *target.cards.toTypedArray())
            r.draw(3)
            target.draw(3)
            OnFinishResolveCard(r, r, target, this, card_type.Ping_Heng, r, MainPhaseIdle(r))
        }
        g.resolve(OnUseCard(r, r, target, this, card_type.Ping_Heng, r, resolveFunc, g.fsm!!))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}平衡"
    }

    companion object {
        private val log = Logger.getLogger(PingHeng::class.java)
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.player
            if (player.cards.size > 3) return false
            val identity = player.identity
            val players = player.game!!.players.filter {
                if (it === player || !it!!.alive) false
                else if (identity != color.Black && identity == it.identity) it.cards.size <= 3
                else it.cards.size >= 3
            }
            if (players.isEmpty()) return false
            val p = players[Random.nextInt(players.size)]!!
            GameExecutor.post(player.game!!, { card.execute(player.game!!, player, p) }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}