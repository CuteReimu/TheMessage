package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.card_type.Ping_Heng
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.usePingHengToc
import com.fengsheng.skill.ConvertCardSkill
import com.fengsheng.skill.cannotPlayCard
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class PingHeng : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
        super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为平衡使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = Ping_Heng

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用平衡")
            r.sendErrorMessage("你被禁止使用平衡")
            return false
        }
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("平衡的使用时机不对")
            r.sendErrorMessage("平衡的使用时机不对")
            return false
        }
        val target = args[0] as Player
        if (r === target) {
            logger.error("平衡不能对自己使用")
            r.sendErrorMessage("平衡不能对自己使用")
            return false
        }
        if (!target.alive) {
            logger.error("目标已死亡")
            r.sendErrorMessage("目标已死亡")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        val fsm = g.fsm as MainPhaseIdle
        logger.info("${r}对${target}使用了$this")
        r.deleteCard(id)
        val resolveFunc = { _: Boolean ->
            g.players.send {
                usePingHengToc {
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    pingHengCard = toPbCard()
                }
            }
            if (r.cards.isNotEmpty()) r.game!!.addEvent(DiscardCardEvent(r, r))
            if (target.cards.isNotEmpty()) r.game!!.addEvent(DiscardCardEvent(r, target))
            g.playerDiscardCard(r, r.cards.toList())
            g.playerDiscardCard(target, target.cards.toList())
            r.draw(3)
            target.draw(3)
            OnFinishResolveCard(r, r, target, getOriginCard(), Ping_Heng, fsm)
        }
        g.resolve(ResolveCard(r, r, target, getOriginCard(), Ping_Heng, resolveFunc, fsm))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}平衡"
    }

    companion object {
        fun ai(e: MainPhaseIdle, card: Card, convertCardSkill: ConvertCardSkill?): Boolean {
            val player = e.whoseTurn
            !player.cannotPlayCard(Ping_Heng) || return false
            player.cards.size <= 3 || return false
            val identity = player.identity
            val p = player.game!!.players.filter {
                if (it === player || !it!!.alive) false
                else if (identity != color.Black && identity == it.identity) it.cards.size <= 3
                else it.cards.size >= 3
            }
            p.size >= 1 || return false
            val target = p.maxBy { abs(it!!.cards.size - 3.1) }
            GameExecutor.post(player.game!!, {
                convertCardSkill?.onConvert(player)
                card.asCard(Ping_Heng).execute(player.game!!, player, target!!)
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
