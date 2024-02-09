package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.card_type.Diao_Hu_Li_Shan
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Fengsheng.use_diao_hu_li_shan_toc
import com.fengsheng.skill.*
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

class DiaoHuLiShan : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为调虎离山使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = Diao_Hu_Li_Shan

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用调虎离山")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用调虎离山")
            return false
        }
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("调虎离山的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("调虎离山的使用时机不对")
            return false
        }
        val target = args[0] as Player
        if (!target.alive) {
            logger.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        val isSkill = args[1] as Boolean
        val fsm = g.fsm as MainPhaseIdle
        logger.info("${r}对${target}使用了$this，禁用" + if (isSkill) "技能" else "出牌")
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
            OnFinishResolveCard(r, r, target, getOriginCard(), Diao_Hu_Li_Shan, fsm)
        }
        g.resolve(ResolveCard(r, r, target, getOriginCard(), Diao_Hu_Li_Shan, resolveFunc, fsm))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}调虎离山"
    }

    companion object {
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.whoseTurn
            !player.cannotPlayCard(Diao_Hu_Li_Shan) || return false
            !player.game!!.isEarly || return false
            val p = player.game!!.players.filter {
                it!!.alive && it.isEnemy(player)
            }.randomOrNull() ?: return false
            val isSkills = ArrayList<Boolean>()
            if (p.cards.any { it.type in WeiBi.availableCardType }
                && !p.skills.any { it is CannotPlayCard }) isSkills.add(false)
            if ((!p.roleFaceUp || p.isPublicRole) &&
                p.skills.any { it is ActiveSkill && it !is MainPhaseSkill }
            ) isSkills.add(true)
            val isSkill = isSkills.randomOrNull() ?: return false
            GameExecutor.post(player.game!!, {
                card.asCard(Diao_Hu_Li_Shan).execute(player.game!!, player, p, isSkill)
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}