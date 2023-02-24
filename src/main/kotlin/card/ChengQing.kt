package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.cardimport.PlayerAndCard
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnUseCard
import com.fengsheng.phase.UseChengQingOnDying
import com.fengsheng.phase.WaitForChengQing
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger
import java.util.concurrent.*

class ChengQing : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card?) : super(id, card)

    /**
     * 仅用于“作为澄清使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type: card_type
        get() = card_type.Cheng_Qing

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            return false
        }
        if (g.qiangLingTypes.contains(type)) {
            log.error("澄清被禁止使用了")
            return false
        }
        val target = args[0] as Player
        val targetCardId = args[1] as Int
        val fsm = g.fsm
        if (fsm is MainPhaseIdle) {
            if (r !== fsm.player) {
                log.error("澄清的使用时机不对")
                return false
            }
        } else if (fsm is WaitForChengQing) {
            if (r !== fsm.askWhom) {
                log.error("澄清的使用时机不对")
                return false
            }
        } else {
            log.error("澄清的使用时机不对")
            return false
        }
        if (!target.alive) {
            log.error("目标已死亡")
            return false
        }
        val targetCard = target.messageCards.find { c -> c.id == targetCardId }
        if (targetCard == null) {
            log.error("没有这张情报")
            return false
        }
        if (!targetCard.colors.contains(color.Black)) {
            log.error("澄清只能对黑情报使用")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        val targetCardId = args[1] as Int
        log.info(r.toString() + "对" + target + "使用了" + this)
        r.deleteCard(id)
        val fsm = g.fsm
        val resolveFunc = object : Fsm {
            override fun resolve(): ResolveResult {
                val targetCard = target.deleteMessageCard(targetCardId)
                log.info("${target}面前的${targetCard}被置入弃牌堆")
                g.deck.discard(targetCard)
                for (player in g.players) {
                    (player as? HumanPlayer)?.send(
                        Fengsheng.use_cheng_qing_toc.newBuilder()
                            .setCard(toPbCard()).setPlayerId(player.getAlternativeLocation(r.location))
                            .setTargetPlayerId(player.getAlternativeLocation(target.location))
                            .setTargetCardId(targetCardId).build()
                    )
                }
                g.deck.discard(getOriginCard())
                return if (fsm is MainPhaseIdle) ResolveResult(fsm, true) else ResolveResult(
                    UseChengQingOnDying(fsm as WaitForChengQing),
                    true
                )
            }
        }
        if (fsm is MainPhaseIdle) g.resolve(
            OnUseCard(fsm.player, r, target, this, card_type.Cheng_Qing, r, resolveFunc)
        ) else if (fsm is WaitForChengQing) g.resolve(
            OnUseCard(fsm.whoseTurn, r, target, this, card_type.Cheng_Qing, r, resolveFunc)
        )
    }

    override fun toString(): String {
        return cardColorToString(colors) + "澄清"
    }

    companion object {
        private val log = Logger.getLogger(ChengQing::class.java)
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.player
            if (player.game!!.qiangLingTypes.contains(card_type.Cheng_Qing)) return false
            val playerAndCards = ArrayList<PlayerAndCard>()
            val identity = player.identity
            for (p in player.game!!.players) {
                if ((p === player || identity != color.Black && identity == p!!.identity) && p.alive) {
                    for (c in p.messageCards) {
                        if (c.colors.contains(color.Black)) playerAndCards.add(PlayerAndCard(p, c))
                    }
                }
            }
            if (playerAndCards.isEmpty()) return false
            val p = playerAndCards[ThreadLocalRandom.current().nextInt(playerAndCards.size)]
            GameExecutor.post(
                player.game!!,
                { card.execute(player.game!!, player, p.player, p.card.getId()) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}