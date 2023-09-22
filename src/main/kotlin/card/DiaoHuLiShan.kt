package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.use_diao_hu_li_shan_toc
import com.fengsheng.skill.InvalidSkill
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

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
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁闭了，不能出牌")
            return false
        }
        if (r.location in g.diaoHuLiShanPlayers) {
            log.error("你被调虎离山了，不能出牌")
            (r as? HumanPlayer)?.sendErrorMessage("你被调虎离山了，不能出牌")
            return false
        }
        if (type in g.qiangLingTypes) {
            log.error("调虎离山被禁止使用了")
            (r as? HumanPlayer)?.sendErrorMessage("调虎离山被禁止使用了")
            return false
        }
        if (r !== (g.fsm as? MainPhaseIdle)?.player) {
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
            else g.diaoHuLiShanPlayers.add(target.location)
            OnFinishResolveCard(r, r, target, getOriginCard(), card_type.Diao_Hu_Li_Shan, MainPhaseIdle(r))
        }
        g.resolve(OnUseCard(r, r, target, getOriginCard(), card_type.Diao_Hu_Li_Shan, resolveFunc, g.fsm!!))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}调虎离山"
    }

    companion object {
        private val log = Logger.getLogger(DiaoHuLiShan::class.java)
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.player
            if (player === player.game!!.jinBiPlayer) return false
            if (player.game!!.qiangLingTypes.contains(card_type.Diao_Hu_Li_Shan)) return false
            if (player.location in player.game!!.diaoHuLiShanPlayers) return false
            if (player.cards.size > 3) return false
            val p = player.game!!.players.filter {
                it!!.alive && it.isEnemy(player)
            }.randomOrNull() ?: return false
            val isSkill = p.cards.isEmpty() || Random.nextBoolean()
            GameExecutor.post(player.game!!, { card.execute(player.game!!, player, p, isSkill) }, 2, TimeUnit.SECONDS)
            return true
        }

        fun resetDiaoHuLiShan(g: Game) {
            g.diaoHuLiShanPlayers.clear()
        }
    }
}