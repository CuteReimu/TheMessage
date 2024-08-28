package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.*
import com.fengsheng.protos.Common.card_type.Cheng_Qing
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.useChengQingToc
import com.fengsheng.skill.ConvertCardSkill
import com.fengsheng.skill.SkillId.*
import com.fengsheng.skill.cannotPlayCard
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

class ChengQing : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
        super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为澄清使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = Cheng_Qing

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用澄清")
            r.sendErrorMessage("你被禁止使用澄清")
            return false
        }
        val target = args[0] as Player
        val targetCardId = args[1] as Int
        val fsm = g.fsm
        if (fsm is MainPhaseIdle) {
            if (r !== fsm.whoseTurn) {
                logger.error("澄清的使用时机不对")
                r.sendErrorMessage("澄清的使用时机不对")
                return false
            }
        } else if (fsm is WaitForChengQing) {
            if (r !== fsm.askWhom) {
                logger.error("澄清的使用时机不对")
                r.sendErrorMessage("澄清的使用时机不对")
                return false
            }
            if (target !== fsm.whoDie) {
                logger.error("正在求澄清的人是${fsm.whoDie}")
                r.sendErrorMessage("正在求澄清的人是${fsm.whoDie}")
                return false
            }
        } else {
            logger.error("澄清的使用时机不对")
            r.sendErrorMessage("澄清的使用时机不对")
            return false
        }
        if (!target.alive) {
            logger.error("目标已死亡")
            r.sendErrorMessage("目标已死亡")
            return false
        }
        val targetCard = target.messageCards.find { c -> c.id == targetCardId }
        if (targetCard == null) {
            logger.error("没有这张情报")
            r.sendErrorMessage("没有这张情报")
            return false
        }
        if (!targetCard.isBlack()) {
            logger.error("澄清只能对黑情报使用")
            r.sendErrorMessage("澄清只能对黑情报使用")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        val targetCardId = args[1] as Int
        logger.info("${r}对${target}使用了$this")
        r.deleteCard(id)
        val fsm = g.fsm as ProcessFsm
        val resolveFunc = { _: Boolean ->
            val targetCard = target.deleteMessageCard(targetCardId)!!
            logger.info("${target}面前的${targetCard}被置入弃牌堆")
            g.deck.discard(targetCard)
            g.players.send {
                useChengQingToc {
                    card = toPbCard()
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    this.targetCardId = targetCardId
                }
            }
            if (fsm is MainPhaseIdle) {
                OnFinishResolveCard(fsm.whoseTurn, r, target, getOriginCard(), Cheng_Qing, fsm)
            } else {
                val newFsm = UseChengQingOnDying(fsm as WaitForChengQing)
                OnFinishResolveCard(fsm.whoseTurn, r, target, getOriginCard(), Cheng_Qing, newFsm)
            }
        }
        g.resolve(ResolveCard(fsm.whoseTurn, r, target, getOriginCard(), Cheng_Qing, resolveFunc, fsm))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}澄清"
    }

    companion object {
        fun ai(e: MainPhaseIdle, card: Card, convertCardSkill: ConvertCardSkill?): Boolean {
            val player = e.whoseTurn
            !(player.identity == Black && player.secretTask in listOf(Killer, Pioneer, Sweeper)) || return false
            !player.cannotPlayCard(Cheng_Qing) || return false
            // 自己是秦无命和裴玲，如果还没有使用技能就不用澄清
            player.findSkill(PIN_MING_SAN_LANG) == null || player.getSkillUseCount(PIN_MING_SAN_LANG) > 0 || return false
            player.findSkill(JIAO_JI) == null || player.getSkillUseCount(JIAO_JI) > 0 || return false

            val g = player.game!!
            var value = 10
            var playerAndCard: PlayerAndCard? = null
            for (p in g.sortedFrom(g.players, player.location)) {
                p.alive && (p === player || !g.isEarly) && p.isPartnerOrSelf(player) || continue
                for (c in p.messageCards.toList()) {
                    c.isBlack() || continue
                    val v = player.calculateRemoveCardValue(player, p, c)
                    if (v > value) {
                        value = v
                        playerAndCard = PlayerAndCard(p, c)
                    }
                }
            }
            playerAndCard ?: return false
            GameExecutor.post(g, {
                convertCardSkill?.onConvert(player)
                card.asCard(Cheng_Qing).execute(g, player, playerAndCard.player, playerAndCard.card.id)
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
