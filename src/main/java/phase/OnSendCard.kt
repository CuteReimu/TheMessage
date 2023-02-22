package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.cardimport

com.fengsheng.protos.Common.direction com.fengsheng.card.*
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Loggerimport java.util.*
/**
 * 选择了要传递哪张情报时
 *
 * @param whoseTurn     谁的回合
 * @param messageCard   传递的情报牌
 * @param dir           传递方向
 * @param targetPlayer  传递的目标角色
 * @param lockedPlayers 被锁定的玩家
 */
class OnSendCard(
    whoseTurn: Player,
    messageCard: Card?,
    dir: direction?,
    targetPlayer: Player,
    lockedPlayers: Array<Player?>
) : Fsm {
    override fun resolve(): ResolveResult? {
        val result = whoseTurn.game.dealListeningSkill()
        if (result != null) return result
        var s = whoseTurn.toString() + "传出了" + messageCard + "，方向是" + dir + "，传给了" + targetPlayer
        if (lockedPlayers.size > 0) s += "，并锁定了" + Arrays.toString(lockedPlayers)
        log.info(s)
        whoseTurn.deleteCard(messageCard.getId())
        for (p in whoseTurn.game.players) p.notifySendMessageCard(
            whoseTurn,
            targetPlayer,
            lockedPlayers,
            messageCard,
            dir
        )
        log.info("情报到达" + targetPlayer + "面前")
        return ResolveResult(SendPhaseIdle(whoseTurn, messageCard, dir, targetPlayer, lockedPlayers, false), true)
    }

    val whoseTurn: Player
    val messageCard: Card?
    val dir: direction?
    val targetPlayer: Player
    val lockedPlayers: Array<Player?>

    init {
        this.card = card
        this.sendPhase = sendPhase
        this.r = r
        this.target = target
        this.card = card
        this.wantType = wantType
        this.r = r
        this.target = target
        this.card = card
        this.player = player
        this.card = card
        this.card = card
        this.drawCards = drawCards
        this.players = players
        this.mainPhaseIdle = mainPhaseIdle
        this.dieSkill = dieSkill
        this.player = player
        this.player = player
        this.onUseCard = onUseCard
        this.game = game
        this.whoseTurn = whoseTurn
        this.messageCard = messageCard
        this.dir = dir
        this.targetPlayer = targetPlayer
        this.lockedPlayers = lockedPlayers
    }

    companion object {
        private val log = Logger.getLogger(OnSendCard::class.java)
    }
}