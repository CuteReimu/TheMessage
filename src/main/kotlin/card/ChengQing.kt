package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.*
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Fengsheng.use_cheng_qing_toc
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

    override val type = card_type.Cheng_Qing

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用澄清")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用澄清")
            return false
        }
        val target = args[0] as Player
        val targetCardId = args[1] as Int
        val fsm = g.fsm
        if (fsm is MainPhaseIdle) {
            if (r !== fsm.whoseTurn) {
                logger.error("澄清的使用时机不对")
                (r as? HumanPlayer)?.sendErrorMessage("澄清的使用时机不对")
                return false
            }
        } else if (fsm is WaitForChengQing) {
            if (r !== fsm.askWhom) {
                logger.error("澄清的使用时机不对")
                (r as? HumanPlayer)?.sendErrorMessage("澄清的使用时机不对")
                return false
            }
            if (target !== fsm.whoDie) {
                logger.error("正在求澄清的人是${fsm.whoDie}")
                (r as? HumanPlayer)?.sendErrorMessage("正在求澄清的人是${fsm.whoDie}")
                return false
            }
        } else {
            logger.error("澄清的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("澄清的使用时机不对")
            return false
        }
        if (!target.alive) {
            logger.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return false
        }
        val targetCard = target.messageCards.find { c -> c.id == targetCardId }
        if (targetCard == null) {
            logger.error("没有这张情报")
            (r as? HumanPlayer)?.sendErrorMessage("没有这张情报")
            return false
        }
        if (!targetCard.isBlack()) {
            logger.error("澄清只能对黑情报使用")
            (r as? HumanPlayer)?.sendErrorMessage("澄清只能对黑情报使用")
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
            for (player in g.players) {
                if (player is HumanPlayer) {
                    val builder = use_cheng_qing_toc.newBuilder()
                    builder.card = toPbCard()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.targetPlayerId = player.getAlternativeLocation(target.location)
                    builder.targetCardId = targetCardId
                    player.send(builder.build())
                }
            }
            if (fsm is MainPhaseIdle) {
                OnFinishResolveCard(fsm.whoseTurn, r, target, getOriginCard(), card_type.Cheng_Qing, fsm)
            } else {
                val newFsm = UseChengQingOnDying(fsm as WaitForChengQing)
                OnFinishResolveCard(fsm.whoseTurn, r, target, getOriginCard(), card_type.Cheng_Qing, newFsm)
            }
        }
        g.resolve(ResolveCard(fsm.whoseTurn, r, target, getOriginCard(), card_type.Cheng_Qing, resolveFunc, fsm))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}澄清"
    }

    companion object {
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.whoseTurn
            !(player.identity == Black && player.secretTask in listOf(Killer, Pioneer, Sweeper)) || return false
            !player.cannotPlayCard(card_type.Cheng_Qing) || return false
            val p1 = player.game!!.players.filter { p -> p!!.alive && p.isPartnerOrSelf(player) } // 伙伴或自己
                .flatMap { p -> p!!.messageCards.filter(Black).map { c -> PlayerAndCard(p, c) } }.run { // 黑情报
                    if (player.identity == Black) this else filterNot { player.identity in it.card.colors } // 非神秘人排除自己身份颜色的情报
                }
            val p2 = player.game!!.players.filter { p -> p!!.alive && p.isEnemy(player) && p.identity != Black } // 敌人
                .flatMap { p ->
                    p!!.messageCards.filter { Black in it.colors && p.identity in it.colors } // 黑情报且有敌人身份颜色
                        .map { c -> PlayerAndCard(p, c) }
                }
            val p = (p1 + p2).randomOrNull() ?: return false
            GameExecutor.post(
                player.game!!,
                { card.execute(player.game!!, player, p.player, p.card.id) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}