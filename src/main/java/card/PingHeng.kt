package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.card.PingHengimport

com.fengsheng.phase.MainPhaseIdleimport com.fengsheng.phase.OnUseCardimport com.fengsheng.protos.Common.*import com.fengsheng.protos.Fengshengimport

org.apache.log4j.Loggerimport java.util.concurrent.*
class PingHeng : Card {
    constructor(id: Int, colors: Array<color>, direction: direction, lockable: Boolean) : super(
        id,
        colors,
        direction,
        lockable
    )

    constructor(id: Int, card: Card?) : super(id, card)

    /**
     * 仅用于“作为平衡使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type: card_type
        get() = card_type.Ping_Heng

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            return false
        }
        if (g.qiangLingTypes.contains(type)) {
            log.error("平衡被禁止使用了")
            return false
        }
        if (g.fsm !is MainPhaseIdle || r !== fsm.player) {
            log.error("平衡的使用时机不对")
            return false
        }
        val target = args[0] as Player
        if (r === target) {
            log.error("平衡不能对自己使用")
            return false
        }
        if (!target.isAlive) {
            log.error("目标已死亡")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        log.info(r.toString() + "对" + target + "使用了" + this)
        r.deleteCard(id)
        val resolveFunc = Fsm {
            for (player in g.players) {
                (player as? HumanPlayer)?.send(
                    Fengsheng.use_ping_heng_toc.newBuilder()
                        .setPlayerId(player.getAlternativeLocation(r.location()))
                        .setTargetPlayerId(player.getAlternativeLocation(target.location()))
                        .setPingHengCard(toPbCard()).build()
                )
            }
            g.playerDiscardCard(r, *r.cards.values.toTypedArray())
            g.playerDiscardCard(target, *target.cards.values.toTypedArray())
            r.draw(3)
            target.draw(3)
            g.deck.discard(originCard)
            ResolveResult(MainPhaseIdle(r), true)
        }
        g.resolve(OnUseCard(r, r, target, this, card_type.Ping_Heng, r, resolveFunc))
    }

    override fun toString(): String {
        return Card.Companion.cardColorToString(colors) + "平衡"
    }

    companion object {
        private val log = Logger.getLogger(PingHeng::class.java)
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.player
            if (player.cards.size > 3) return false
            val players: MutableList<Player> = ArrayList()
            for (p in player.game.players) if (p !== player && p.isAlive) players.add(p)
            if (players.isEmpty()) return false
            val p = players[ThreadLocalRandom.current().nextInt(players.size)]
            GameExecutor.Companion.post(
                player.game,
                Runnable { card.execute(player.game, player, p) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}