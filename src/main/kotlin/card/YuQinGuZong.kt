package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.OnUseCard
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.use_yu_qin_gu_zong_toc
import org.apache.log4j.Logger

class YuQinGuZong : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为欲擒故纵使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type: card_type
        get() = card_type.Yu_Qin_Gu_Zong

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
            log.error("欲擒故纵被禁止使用了")
            (r as? HumanPlayer)?.sendErrorMessage("欲擒故纵被禁止使用了")
            return false
        }
        if (r !== (g.fsm as? SendPhaseStart)?.player) {
            log.error("欲擒故纵的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("欲擒故纵的使用时机不对")
            return false
        }
        val target = args[0] as Player
        if (r === target) {
            log.error("欲擒故纵不能对自己使用")
            (r as? HumanPlayer)?.sendErrorMessage("欲擒故纵不能对自己使用")
            return false
        }
        if (!target.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return false
        }
        if (target.cards.isEmpty()) {
            log.error("目标没有手牌")
            (r as? HumanPlayer)?.sendErrorMessage("目标没有手牌")
            return false
        }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun execute(g: Game, r: Player, vararg args: Any) {
        val fsm = g.fsm as SendPhaseStart
        val messageCard = args[0] as Card
        val dir = args[1] as direction
        val target = args[2] as Player
        val lockPlayers = args[3] as List<Player>
        log.info("${r}使用了$this")
        r.deleteCard(id)
        val resolveFunc = { _: Boolean ->
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = use_yu_qin_gu_zong_toc.newBuilder()
                    builder.card = toPbCard()
                    builder.messageCardId = messageCard.id
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    lockPlayers.forEach { builder.addLockPlayerIds(p.getAlternativeLocation(it.location)) }
                    p.send(builder.build())
                }
            }
            r.messageCards.remove(messageCard) // 欲擒故纵可能传出面前的情报
            r.draw(1)
            OnFinishResolveCard( // 这里先触发卡牌结算后，再触发情报传出时
                r, target, this, type, OnSendCard(
                    fsm.player, fsm.player, messageCard, dir, target, lockPlayers.toTypedArray()
                )
            )
        }
        g.resolve(OnUseCard(r, r, target, this, card_type.Yu_Qin_Gu_Zong, resolveFunc, fsm))
    }

    override fun toPbCard(): card {
        val builder = card.newBuilder()
        builder.cardId = id
        builder.cardDir = direction
        builder.canLock = lockable
        builder.cardType = type
        builder.addAllCardColor(colors)
        return builder.build()
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}欲擒故纵"
    }

    companion object {
        private val log = Logger.getLogger(YuQinGuZong::class.java)
        fun ai(e: SendPhaseStart, card: Card): Boolean {
            return false // 传情报AI太复杂，不做
        }
    }
}