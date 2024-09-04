package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.Player
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.card_type.Diao_Hu_Li_Shan
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.useDiaoHuLiShanToc
import com.fengsheng.send
import com.fengsheng.skill.CannotPlayCard
import com.fengsheng.skill.ConvertCardSkill
import com.fengsheng.skill.InvalidSkill
import com.fengsheng.skill.cannotPlayCard
import org.apache.logging.log4j.kotlin.logger
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

    override val type = Diao_Hu_Li_Shan

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用调虎离山")
            r.sendErrorMessage("你被禁止使用调虎离山")
            return false
        }
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("调虎离山的使用时机不对")
            r.sendErrorMessage("调虎离山的使用时机不对")
            return false
        }
        val target = args[0] as Player
        if (!target.alive) {
            logger.error("目标已死亡")
            r.sendErrorMessage("目标已死亡")
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
            g.players.send {
                useDiaoHuLiShanToc {
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    card = toPbCard()
                    this.isSkill = isSkill
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
        fun ai(e: MainPhaseIdle, card: Card, convertCardSkill: ConvertCardSkill?): Boolean {
            val player = e.whoseTurn
            !player.cannotPlayCard(Diao_Hu_Li_Shan) || return false
            !player.game!!.isEarly || return false
            fun countImportantCard(p: Player): Int {
                return p.cards.count { c -> c.type in WeiBi.availableCardType }
            }

            // 只有在有阵营听牌时才会打出调虎离山
            player.game!!.players.any {
                it!!.alive && it.identity in listOf(Red, Blue) && it.messageCards.count(it.identity) == 2
            } || return false

            val enemies = player.game!!.players.filter {
                it!!.alive && it.isEnemy(player)
            }.filterNotNull().shuffled()

            var p = enemies.filter {
                !it.skills.any { s -> s is CannotPlayCard && s.forbidAllCard }
            }.maxByOrNull(::countImportantCard)
            var isSkill = false
            if (p != null && countImportantCard(p) == 0)
                p = null
            if (p == null || countImportantCard(p) <= 1 && Random.nextBoolean()) {
                val p2 = enemies.filter {
                    it.skills.any { s -> s.isInitialSkill }
                }.run { find { !it.hasEverFaceUp } ?: find { !it.roleFaceUp } }
                if (p2 != null) {
                    isSkill = true
                    p = p2
                }
            }
            p ?: return false
            GameExecutor.post(player.game!!, {
                convertCardSkill?.onConvert(player)
                card.asCard(Diao_Hu_Li_Shan).execute(player.game!!, player, p, isSkill)
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
