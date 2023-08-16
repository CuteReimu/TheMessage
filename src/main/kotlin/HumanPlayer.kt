package com.fengsheng

import com.fengsheng.card.Card
import com.fengsheng.phase.*
import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.role.unknown
import com.fengsheng.protos.Errcode.error_message_toc
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.skill.SkillId
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.TextFormat
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.util.Timeout
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

class HumanPlayer(
    var channel: Channel,
    var needWaitLoad: Boolean = false,
    val newBodyFun: (String, ByteArray) -> Any
) : Player() {
    var seq = 0
        private set

    /**
     * 把跟玩家有关的计时器绑定在玩家身上，例如操作超时等待。这样在玩家操作后就可以清掉这个计时器，以节约资源
     */
    var timeout: Timeout? = null
    private var timeoutCount = 0
    private var recorder = Recorder()
    var device: String? = null
    private var autoPlay = false
    val limiter = Limiter(10, 100, TimeUnit.MILLISECONDS)

    @Volatile
    var isReconnecting = false

    /**
     * 游戏结束重置
     */
    override fun reset() {
        super.reset()
        seq = 0
        timeout?.cancel()
        timeout = null
        timeoutCount = 0
        recorder = Recorder()
        device = null
        autoPlay = false
    }

    /**
     * 向玩家客户端发送[error_message_toc]
     */
    fun sendErrorMessage(message: String) {
        send(error_message_toc.newBuilder().setMsg(message).build())
    }

    /**
     * 向玩家客户端发送协议
     */
    fun send(message: GeneratedMessageV3) {
        val buf = message.toByteArray()
        val name = message.descriptorForType.name
        recorder.add(name, buf)
        if (isActive && !isReconnecting) send(name, buf, true)
        log.debug(
            "send@${channel.id().asShortText()} len: ${buf.size} $name | " +
                    printer.printToString(message).replace(Regex("\n *"), " ")
        )
    }

    fun send(protoName: String, buf: ByteArray, flush: Boolean) {
        val v = newBodyFun(protoName, buf)
        val f = if (flush) channel.writeAndFlush(v) else channel.write(v)
        f.addListener(ChannelFutureListener { future: ChannelFuture ->
            if (!future.isSuccess)
                log.error("send@${channel.id().asShortText()} failed, proto name: $protoName, len: ${buf.size}")
        })
    }

    fun saveRecord() {
        recorder.save(game!!, this, channel.isActive)
    }

    fun loadRecord(version: Int, recordId: String) {
        recorder.load(version, recordId, this)
    }

    val isLoadingRecord: Boolean
        get() = recorder.loading

    fun displayRecord() {
        recorder.displayNext(this)
    }

    fun pauseRecord(pause: Boolean) {
        recorder.pause(pause)
    }

    fun reconnect() {
        isReconnecting = false
        recorder.reconnect(this)
        notifyPlayerUpdateStatus()
        notifyOtherPlayerStatus()
    }

    val isActive: Boolean
        /**
         * Return `true` if the [Channel] of the `HumanPlayer` is active and so connected.
         */
        get() = channel.isActive

    fun setAutoPlay(autoPlay: Boolean) {
        if (this.autoPlay == autoPlay) return
        timeoutCount = 0
        this.autoPlay = autoPlay
        if (autoPlay) {
            if (timeout != null && timeout!!.cancel()) {
                try {
                    timeout!!.task().run(timeout)
                } catch (e: Exception) {
                    log.error("time task exception", e)
                }
            }
        } else {
            if (timeout != null && timeout!!.cancel()) {
                var delay = 16
                if (game!!.fsm is MainPhaseIdle || game!!.fsm is WaitForDieGiveCard) delay =
                    21 else if (game!!.fsm is WaitForSelectRole) delay = 31
                timeout =
                    GameExecutor.TimeWheel.newTimeout(timeout!!.task(), delay.toLong(), TimeUnit.SECONDS)
            }
        }
        send("auto_play_toc", auto_play_toc.newBuilder().setEnable(autoPlay).build().toByteArray(), true)
        notifyPlayerUpdateStatus()
    }

    override fun init() {
        super.init()
        val builder = init_toc.newBuilder()
        builder.playerCount = game!!.players.size
        builder.identity = identity
        builder.secretTask = secretTask
        var l = location
        do {
            builder.addRoles(if (game!!.players[l]!!.roleFaceUp || l == location) game!!.players[l]!!.role else unknown)
            builder.addNames(game!!.players[l]!!.playerName)
            l = (l + 1) % game!!.players.size
        } while (l != location)
        send(builder.build())
    }

    override fun notifyAddHandCard(location: Int, unknownCount: Int, vararg cards: Card) {
        val builder =
            add_card_toc.newBuilder().setPlayerId(getAlternativeLocation(location)).setUnknownCardCount(unknownCount)
        for (card in cards) {
            builder.addCards(card.toPbCard())
        }
        send(builder.build())
    }

    override fun notifyDrawPhase() {
        val player = (game!!.fsm as DrawPhase).player
        val playerId = getAlternativeLocation(player.location)
        val builder = notify_phase_toc.newBuilder()
        builder.setCurrentPlayerId(playerId).setCurrentPhase(Common.phase.Draw_Phase).waitingPlayerId = playerId
        send(builder.build())
    }

    override fun notifyMainPhase(waitSecond: Int) {
        val player = (game!!.fsm as MainPhaseIdle).player
        val playerId = getAlternativeLocation(player.location)
        val builder = notify_phase_toc.newBuilder()
        builder.setCurrentPlayerId(playerId).setCurrentPhase(Common.phase.Main_Phase).waitingPlayerId = playerId
        builder.waitingSecond = waitSecond
        if (this === player) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.post(game!!, {
                if (checkSeq(seq2)) {
                    incrSeq()
                    game!!.resolve(SendPhaseStart(player))
                }
            }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
        }
        send(builder.build())
    }

    override fun notifySendPhaseStart(waitSecond: Int) {
        val fsm = game!!.fsm as SendPhaseStart
        val player = fsm.player
        val playerId = getAlternativeLocation(player.location)
        val builder = notify_phase_toc.newBuilder()
        builder.setCurrentPlayerId(playerId).currentPhase = Common.phase.Send_Start_Phase
        builder.setWaitingPlayerId(playerId).waitingSecond = waitSecond
        if (this === player) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.post(game!!, {
                if (checkSeq(seq2)) {
                    incrSeq()
                    if (cards.isEmpty()) game!!.resolve(fsm.copy(allowUseSkill = false))
                    else autoSendMessageCard(this)
                }
            }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
        }
        send(builder.build())
    }

    override fun notifySendMessageCard(
        whoseTurn: Player,
        sender: Player,
        targetPlayer: Player,
        lockedPlayers: Array<Player>,
        messageCard: Card,
        dir: direction?
    ) {
        val builder = send_message_card_toc.newBuilder()
        builder.playerId = getAlternativeLocation(whoseTurn.location)
        builder.targetPlayerId = getAlternativeLocation(targetPlayer.location)
        builder.cardDir = dir
        builder.senderId = getAlternativeLocation(sender.location)
        if (sender === this) builder.cardId = messageCard.id
        for (p in lockedPlayers) builder.addLockPlayerIds(getAlternativeLocation(p.location))
        send(builder.build())
    }

    override fun notifySendPhase(waitSecond: Int) {
        val fsm = game!!.fsm as SendPhaseIdle
        val builder = notify_phase_toc.newBuilder()
        builder.currentPlayerId = getAlternativeLocation(fsm.whoseTurn.location)
        builder.currentPhase = Common.phase.Send_Phase
        builder.messagePlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location)
        builder.waitingPlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location)
        builder.messageCardDir = fsm.dir
        builder.waitingSecond = waitSecond
        builder.senderId = getAlternativeLocation(fsm.sender.location)
        if (fsm.isMessageCardFaceUp) builder.messageCard = fsm.messageCard.toPbCard()
        if (this === fsm.inFrontOfWhom) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.post(game!!, {
                if (checkSeq(seq2)) {
                    incrSeq()
                    var isLocked = false
                    for (p in fsm.lockedPlayers) {
                        if (p === this) {
                            isLocked = true
                            break
                        }
                    }
                    if (isLocked || fsm.inFrontOfWhom === fsm.sender) game!!.resolve(
                        OnChooseReceiveCard(
                            fsm.whoseTurn,
                            fsm.sender,
                            fsm.messageCard,
                            fsm.inFrontOfWhom,
                            fsm.isMessageCardFaceUp
                        )
                    ) else game!!.resolve(MessageMoveNext(fsm))
                }
            }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
        }
        send(builder.build())
    }

    override fun notifyChooseReceiveCard(player: Player) {
        send(choose_receive_toc.newBuilder().setPlayerId(getAlternativeLocation(player.location)).build())
    }

    override fun notifyFightPhase(waitSecond: Int) {
        val fsm = game!!.fsm as FightPhaseIdle
        val builder = notify_phase_toc.newBuilder()
        builder.currentPlayerId = getAlternativeLocation(fsm.whoseTurn.location)
        builder.messagePlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location)
        builder.waitingPlayerId = getAlternativeLocation(fsm.whoseFightTurn.location)
        builder.setCurrentPhase(Common.phase.Fight_Phase).waitingSecond = waitSecond
        if (fsm.isMessageCardFaceUp) builder.messageCard = fsm.messageCard.toPbCard()
        if (this === fsm.whoseFightTurn) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.post(game!!, {
                if (checkSeq(seq2)) {
                    incrSeq()
                    game!!.resolve(FightPhaseNext(fsm))
                }
            }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
        }
        send(builder.build())
    }

    override fun notifyReceivePhase() {
        val fsm = game!!.fsm as ReceivePhase
        val builder = notify_phase_toc.newBuilder()
        builder.currentPlayerId = getAlternativeLocation(fsm.whoseTurn.location)
        builder.messagePlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location)
        builder.waitingPlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location)
        builder.setCurrentPhase(Common.phase.Receive_Phase).messageCard = fsm.messageCard.toPbCard()
        send(builder.build())
    }

    override fun notifyReceivePhase(
        whoseTurn: Player,
        inFrontOfWhom: Player,
        messageCard: Card,
        waitingPlayer: Player,
        waitSecond: Int
    ) {
        val builder = notify_phase_toc.newBuilder()
        builder.currentPlayerId = getAlternativeLocation(whoseTurn.location)
        builder.messagePlayerId = getAlternativeLocation(inFrontOfWhom.location)
        builder.waitingPlayerId = getAlternativeLocation(waitingPlayer.location)
        builder.setCurrentPhase(Common.phase.Receive_Phase).setMessageCard(messageCard.toPbCard()).waitingSecond =
            waitSecond
        if (this === waitingPlayer) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.post(game!!, {
                if (checkSeq(seq2)) game!!.tryContinueResolveProtocol(
                    this,
                    end_receive_phase_tos.newBuilder().setSeq(seq2).build()
                )
            }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
        }
        send(builder.build())
    }

    override fun notifyDying(location: Int, loseGame: Boolean) {
        super.notifyDying(location, loseGame)
        val builder = notify_dying_toc.newBuilder()
        builder.playerId = getAlternativeLocation(location)
        builder.loseGame = loseGame
        send(builder.build())
    }

    override fun notifyDie(location: Int) {
        super.notifyDie(location)
        val builder = notify_die_toc.newBuilder()
        builder.playerId = getAlternativeLocation(location)
        send(builder.build())
    }

    override fun notifyWin(declareWinners: Array<Player>, winners: Array<Player>) {
        val builder = notify_winner_toc.newBuilder()
        builder.addAllDeclarePlayerIds(declareWinners.map { getAlternativeLocation(it.location) }.sorted())
        builder.addAllWinnerIds(winners.map { getAlternativeLocation(it.location) }.sorted())
        builder.addAllIdentity(game!!.players.indices.map { game!!.players[getAbstractLocation(it)]!!.identity })
        builder.addAllSecretTasks(game!!.players.indices.map { game!!.players[getAbstractLocation(it)]!!.secretTask })
        send(builder.build())
    }

    override fun notifyAskForChengQing(whoDie: Player, askWhom: Player, waitSecond: Int) {
        val builder = wait_for_cheng_qing_toc.newBuilder()
        builder.diePlayerId = getAlternativeLocation(whoDie.location)
        builder.waitingPlayerId = getAlternativeLocation(askWhom.location)
        builder.waitingSecond = waitSecond
        if (askWhom === this) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.post(game!!, {
                if (checkSeq(seq2)) {
                    incrSeq()
                    game!!.resolve(WaitNextForChengQing(game!!.fsm as WaitForChengQing))
                }
            }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
        }
        send(builder.build())
    }

    override fun waitForDieGiveCard(whoDie: Player, waitSecond: Int) {
        val builder = wait_for_die_give_card_toc.newBuilder()
        builder.playerId = getAlternativeLocation(whoDie.location)
        builder.waitingSecond = waitSecond
        if (whoDie === this) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.post(game!!, {
                if (checkSeq(seq2)) {
                    incrSeq()
                    game!!.resolve(AfterDieGiveCard(game!!.fsm as WaitForDieGiveCard))
                }
            }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
        }
        send(builder.build())
    }

    /**
     * 把自己的托管、掉线状态广播给所有玩家
     */
    fun notifyPlayerUpdateStatus() {
        for (p in game!!.players) {
            if (p is HumanPlayer) {
                val builder = notify_player_update_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(location)
                builder.isAuto = autoPlay
                builder.isOffline = !isActive
                p.send(builder.build())
            }
        }
    }

    /**
     * 把别人的托管、掉线状态发给自己
     */
    private fun notifyOtherPlayerStatus() {
        for (p in game!!.players) {
            if (p is HumanPlayer && p !== this && (!p.isActive || p.autoPlay)) {
                val builder = notify_player_update_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(location)
                builder.isAuto = autoPlay
                builder.isOffline = !isActive
                send(builder.build())
            }
        }
    }

    fun checkSeq(seq: Int): Boolean {
        return this.seq == seq
    }

    override fun incrSeq() {
        seq++
        timeout?.also { timeout ->
            if (timeout.isExpired) {
                if (!autoPlay && ++timeoutCount >= 3) setAutoPlay(true)
            } else {
                timeout.cancel()
            }
        }
        timeout = null
    }

    fun clearTimeoutCount() {
        timeoutCount = 0
    }

    fun getWaitSeconds(seconds: Int) = when {
        !isActive -> 5
        autoPlay -> 1
        else -> seconds
    }

    companion object {
        private val log = Logger.getLogger(HumanPlayer::class.java)
        private val printer = TextFormat.printer().escapingNonAscii(false)

        /**
         * 随机选择一张牌作为情报传出
         */
        private fun autoSendMessageCard(r: Player) {
            val card = r.cards.first()
            val dir =
                if (r.findSkill(SkillId.LIAN_LUO) == null) card.direction
                else arrayOf(direction.Up, direction.Left, direction.Right).random()
            val availableTargets = r.game!!.players.filter { it !== r && it!!.alive }
            val target = when (dir) {
                direction.Up -> availableTargets.random()!!
                direction.Left -> r.getNextLeftAlivePlayer()
                else -> r.getNextRightAlivePlayer()
            }
            r.game!!.resolve(OnSendCard(r, r, card, dir, target, arrayOf()))
        }
    }
}