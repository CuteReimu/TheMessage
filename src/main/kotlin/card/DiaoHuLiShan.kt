package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.use_diao_hu_li_shan_toc
import com.fengsheng.skill.*
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

class DiaoHuLiShan : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为调虎离山使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = card_type.Diao_Hu_Li_Shan

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            log.error("你被禁止使用调虎离山")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用调虎离山")
            return false
        }
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            log.error("调虎离山的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("调虎离山的使用时机不对")
            return false
        }
        val target = args[0] as Player
        if (!target.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        val isSkill = args[1] as Boolean
        log.info("${r}对${target}使用了$this，isSkill: $isSkill")
        r.deleteCard(id)
        val resolveFunc = { _: Boolean ->
            for (player in g.players) {
                if (player is HumanPlayer) {
                    val builder = use_diao_hu_li_shan_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.targetPlayerId = player.getAlternativeLocation(target.location)
                    builder.card = toPbCard()
                    builder.isSkill = isSkill
                    player.send(builder.build())
                }
            }
            if (isSkill) InvalidSkill.deal(target)
            else target.skills += CannotPlayCard(forbidAllCard = true)
            OnFinishResolveCard(r, r, target, getOriginCard(), card_type.Diao_Hu_Li_Shan, MainPhaseIdle(r))
        }
        g.resolve(ResolveCard(r, r, target, getOriginCard(), card_type.Diao_Hu_Li_Shan, resolveFunc, g.fsm!!))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}调虎离山"
    }

    companion object {
        private val log = Logger.getLogger(DiaoHuLiShan::class.java)
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.whoseTurn
            !player.cannotPlayCard(card_type.Diao_Hu_Li_Shan) || return false
            val p = player.game!!.players.filter {
                it!!.alive && it.isEnemy(player)
            }.randomOrNull() ?: return false
            val isSkills = ArrayList<Boolean>()
            if (p.cards.isNotEmpty() && !p.skills.any { it is CannotPlayCard }) isSkills.add(false)
            if (p.skills.any { it is ActiveSkill && it !is MainPhaseSkill }) isSkills.add(true)
            val isSkill = isSkills.randomOrNull() ?: return false
            GameExecutor.post(player.game!!, { card.execute(player.game!!, player, p, isSkill) }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}