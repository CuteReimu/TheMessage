package com.fengsheng

import com.fengsheng.card.Card
import com.fengsheng.phase.*
import com.fengsheng.protos.*
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.direction.*
import com.fengsheng.protos.Common.phase.*
import com.fengsheng.protos.Common.role.unknown
import com.fengsheng.protos.Errcode.error_message_toc
import com.fengsheng.protos.Fengsheng.notify_player_update_toc
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId.*
import com.fengsheng.skill.cannotPlayCardAndSkillForFightPhase
import com.fengsheng.skill.hasNothingToDoForFightPhase
import com.fengsheng.skill.mustReceiveMessage
import com.google.protobuf.GeneratedMessage
import com.google.protobuf.util.JsonFormat
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.util.Timeout
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class HumanPlayer(var channel: Channel, var needWaitLoad: Boolean = false, val newBodyFun: (String, ByteArray) -> Any) :
    Player() {
    var seq = 0
        private set

    /**
     * 把跟玩家有关的计时器绑定在玩家身上，例如操作超时等待。这样在玩家操作后就可以清掉这个计时器，以节约资源
     */
    var timeout: Timeout? = null
    private var timeoutCount = 0
    private var recorder = Recorder()
    private var autoPlay = false
    var mainPhaseStartTime = 0L
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
        autoPlay = false
    }

    /**
     * 向玩家客户端发送[error_message_toc]
     */
    override fun sendErrorMessage(message: String) {
        send(errorMessageToc { msg = message })
    }

    /**
     * 向玩家客户端发送协议
     */
    override fun send(message: GeneratedMessage) {
        val buf = message.toByteArray()
        val name = message.descriptorForType.name
        recorder.add(name, buf)
        if (isActive && !isReconnecting) send(name, buf, true)
        if (message is notify_player_update_toc) return
        logger.debug(
            "send@${channel.id().asShortText()} len: ${buf.size} $name | " +
                printer.print(message).replace(Regex("\n *"), " ")
        )
    }

    /**
     * 向玩家客户端发送协议
     */
    override fun send(protoName: String, buf: ByteArray, flush: Boolean) {
        val v = newBodyFun(protoName, buf)
        val f = if (flush) channel.writeAndFlush(v) else channel.write(v)
        f.addListener(ChannelFutureListener { future: ChannelFuture ->
            if (!future.isSuccess)
                logger.error("send@${channel.id().asShortText()} failed, proto name: $protoName, len: ${buf.size}")
        })
    }

    fun saveRecord() {
        recorder.save(game!!, this, channel.isActive)
    }

    fun loadRecord(version: Int, recordId: String, skipCount: Int) {
        recorder.load(version, recordId, skipCount, this)
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
        logger.debug("${this}托管状态：$autoPlay")
        timeoutCount = 0
        this.autoPlay = autoPlay
        if (autoPlay) {
            if (timeout != null && timeout!!.cancel()) {
                try {
                    timeout!!.task().run(timeout)
                } catch (e: Exception) {
                    logger.error("time task exception", e)
                }
            }
        } else {
            if (timeout != null && timeout!!.cancel()) {
                var delay = game!!.waitSecond + 1
                if (game!!.fsm is MainPhaseIdle || game!!.fsm is WaitForDieGiveCard)
                    delay = game!!.waitSecond * 4 / 3 + 1
                else if (game!!.fsm is WaitForSelectRole)
                    delay = game!!.waitSecond * 2 + 1
                timeout =
                    GameExecutor.TimeWheel.newTimeout(timeout!!.task(), delay.toLong(), TimeUnit.SECONDS)
            }
        }
        send("auto_play_toc", autoPlayToc { enable = autoPlay }.toByteArray(), true)
        notifyPlayerUpdateStatus()
    }

    override fun init() {
        super.init()
        send(initToc {
            playerCount = game!!.players.size
            identity = this@HumanPlayer.identity
            secretTask = this@HumanPlayer.secretTask
            var l = location
            do {
                val player = game!!.players[l]!!
                roles.add(if (player.roleFaceUp || l == location) player.role else unknown)
                val name =
                    if (player.playerTitle.isEmpty()) player.playerName
                    else "${player.playerName}·${player.playerTitle}"
                names.add(name)
                l = (l + 1) % game!!.players.size
            } while (l != location)
            possibleSecretTask.addAll(game!!.possibleSecretTasks)
        })
    }

    override fun notifyAddHandCard(location: Int, unknownCount: Int, cards: List<Card>) {
        send(addCardToc {
            playerId = getAlternativeLocation(location)
            unknownCardCount = unknownCount
            cards.forEach { this.cards.add(it.toPbCard()) }
        })
    }

    override fun notifyDrawPhase() {
        val player = (game!!.fsm as DrawPhase).whoseTurn
        val playerId = getAlternativeLocation(player.location)
        send(notifyPhaseToc {
            currentPlayerId = playerId
            currentPhase = Draw_Phase
            waitingPlayerId = playerId
        })
    }

    override fun notifyMainPhase(waitSecond: Int) {
        val player = (game!!.fsm as MainPhaseIdle).whoseTurn
        val playerId = getAlternativeLocation(player.location)
        send(notifyPhaseToc {
            currentPlayerId = playerId
            currentPhase = Main_Phase
            waitingPlayerId = playerId
            waitingSecond = waitSecond
            if (this@HumanPlayer === player) {
                mainPhaseStartTime = System.currentTimeMillis()
                val seq2 = this@HumanPlayer.seq
                seq = seq2
                timeout = GameExecutor.post(game!!, {
                    if (checkSeq(seq2)) {
                        incrSeq()
                        game!!.resolve(SendPhaseStart(player))
                    }
                }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
            }
        })
    }

    override fun notifySendPhaseStart(waitSecond: Int) {
        val fsm = game!!.fsm as SendPhaseStart
        val player = fsm.whoseTurn
        val playerId = getAlternativeLocation(player.location)
        send(notifyPhaseToc {
            currentPlayerId = playerId
            currentPhase = Send_Start_Phase
            waitingPlayerId = playerId
            if (player.cards.isNotEmpty()) {
                waitingSecond = waitSecond
                if (this@HumanPlayer === player && waitSecond > 0) {
                    val seq2 = this@HumanPlayer.seq
                    seq = seq2
                    timeout = GameExecutor.post(game!!, {
                        if (checkSeq(seq2)) {
                            incrSeq()
                            autoSendMessageCard(this@HumanPlayer)
                        }
                    }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
                }
            }
        })
        if (this === player && player.cards.isEmpty() && waitSecond > 0) {
            val seq2 = seq
            timeout = GameExecutor.post(game!!, {
                if (checkSeq(seq2)) {
                    val skill = player.findSkill(LENG_XUE_XUN_LIAN) as ActiveSkill
                    skill.executeProtocol(game!!, player, skillLengXueXunLianATos { seq = seq2 })
                }
            }, 100, TimeUnit.MILLISECONDS)
        }
    }

    override fun notifySendMessageCard(
        whoseTurn: Player,
        sender: Player,
        targetPlayer: Player,
        lockedPlayers: List<Player>,
        messageCard: Card,
        dir: direction
    ) {
        send(sendMessageCardToc {
            playerId = getAlternativeLocation(whoseTurn.location)
            targetPlayerId = getAlternativeLocation(targetPlayer.location)
            cardDir = dir
            senderId = getAlternativeLocation(sender.location)
            if (sender === this@HumanPlayer) cardId = messageCard.id
            lockedPlayers.forEach { lockPlayerIds.add(getAlternativeLocation(it.location)) }
        })
    }

    override fun notifySendPhase(waitSecond: Int) {
        val fsm = game!!.fsm as SendPhaseIdle
        send(notifyPhaseToc {
            currentPlayerId = getAlternativeLocation(fsm.whoseTurn.location)
            currentPhase = Send_Phase
            messagePlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location)
            waitingPlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location)
            messageCardDir = fsm.dir
            waitingSecond = waitSecond
            senderId = getAlternativeLocation(fsm.sender.location)
            if (fsm.isMessageCardFaceUp) messageCard = fsm.messageCard.toPbCard()
            if (this@HumanPlayer === fsm.inFrontOfWhom) seq = this@HumanPlayer.seq
        })
    }

    override fun startSendPhaseTimer(waitSecond: Int) {
        val fsm = game!!.fsm as SendPhaseIdle
        val seq = seq
        timeout = GameExecutor.post(game!!, {
            if (checkSeq(seq)) {
                incrSeq()
                if (fsm.mustReceiveMessage()) game!!.resolve( // 如果必须接收，则接收。否则一定不接收
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

    override fun notifyChooseReceiveCard(player: Player) {
        send(chooseReceiveToc { playerId = getAlternativeLocation(player.location) })
    }

    override fun notifyFightPhase(waitSecond: Int) {
        val fsm = game!!.fsm as FightPhaseIdle
        val (skip, skipTime) =
            if (cannotPlayCardAndSkillForFightPhase(fsm)) true to 1
            else if (hasNothingToDoForFightPhase(fsm)) true to 3 + Random.nextInt(5)
            else false to waitSecond + 2
        send(notifyPhaseToc {
            currentPlayerId = getAlternativeLocation(fsm.whoseTurn.location)
            messagePlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location)
            waitingPlayerId = getAlternativeLocation(fsm.whoseFightTurn.location)
            currentPhase = Fight_Phase
            waitingSecond = waitSecond
            if (fsm.isMessageCardFaceUp) messageCard = fsm.messageCard.toPbCard()
            if (this@HumanPlayer === fsm.whoseFightTurn) {
                val seq2 = this@HumanPlayer.seq
                seq = seq2
                timeout = GameExecutor.post(game!!, {
                    if (checkSeq(seq2)) {
                        incrSeq()
                        if (skip) send(errorMessageToc { msg = "无牌可出，已经帮您自动跳过" })
                        game!!.resolve(FightPhaseNext(fsm))
                    }
                }, getWaitSeconds(skipTime).toLong(), TimeUnit.SECONDS)
            }
        })
    }

    override fun notifyReceivePhase() {
        val fsm = game!!.fsm as OnReceiveCard
        send(notifyPhaseToc {
            currentPlayerId = getAlternativeLocation(fsm.whoseTurn.location)
            messagePlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location)
            waitingPlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location)
            currentPhase = Receive_Phase
            messageCard = fsm.messageCard.toPbCard()
        })
    }

    override fun notifyReceivePhase(
        whoseTurn: Player,
        inFrontOfWhom: Player,
        messageCard: Card,
        waitingPlayer: Player,
        waitSecond: Int
    ) {
        send(notifyPhaseToc {
            currentPlayerId = getAlternativeLocation(whoseTurn.location)
            messagePlayerId = getAlternativeLocation(inFrontOfWhom.location)
            waitingPlayerId = getAlternativeLocation(waitingPlayer.location)
            currentPhase = Receive_Phase
            this.messageCard = messageCard.toPbCard()
            waitingSecond = waitSecond
            if (this@HumanPlayer === waitingPlayer) {
                val seq2 = this@HumanPlayer.seq
                seq = seq2
                timeout = GameExecutor.post(game!!, {
                    if (checkSeq(seq2))
                        game!!.tryContinueResolveProtocol(this@HumanPlayer, endReceivePhaseTos { seq = seq2 })
                }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
            }
        })
    }

    override fun notifyDying(location: Int, loseGame: Boolean) {
        super.notifyDying(location, loseGame)
        send(notifyDyingToc {
            playerId = getAlternativeLocation(location)
            this.loseGame = loseGame
        })
    }

    override fun notifyDie(location: Int) {
        super.notifyDie(location)
        send(notifyDieToc { playerId = getAlternativeLocation(location) })
    }

    override fun notifyWin(
        declareWinners: List<Player>,
        winners: List<Player>,
        addScoreMap: HashMap<String, Int>,
        newScoreMap: HashMap<String, Int>
    ) {
        send(notifyWinnerToc {
            declarePlayerIds.addAll(declareWinners.map { getAlternativeLocation(it.location) }.sorted())
            winnerIds.addAll(winners.map { getAlternativeLocation(it.location) }.sorted())
            game!!.players.indices.forEach {
                val player = game!!.players[getAbstractLocation(it)]!!
                identity.add(player.identity)
                secretTasks.add(player.secretTask)
                val ns = newScoreMap[player.playerName] ?: Statistics.getScore(player) ?: 0
                addScore.add(addScoreMap[player.playerName] ?: 0)
                newScore.add(ns)
                newRank.add(ScoreFactory.getRankNameByScore(ns))
                alive.add(player.alive)
            }
        })
    }

    override fun notifyAskForChengQing(whoseTurn: Player, whoDie: Player, askWhom: Player, waitSecond: Int) {
        send(waitForChengQingToc {
            diePlayerId = getAlternativeLocation(whoDie.location)
            waitingPlayerId = getAlternativeLocation(askWhom.location)
            waitingSecond = waitSecond
            if (askWhom === this@HumanPlayer) {
                val seq2 = this@HumanPlayer.seq
                seq = seq2
                timeout = GameExecutor.post(game!!, {
                    if (checkSeq(seq2)) {
                        incrSeq()
                        game!!.resolve(WaitNextForChengQing(game!!.fsm as WaitForChengQing))
                    }
                }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
            }
        })
    }

    override fun waitForDieGiveCard(whoDie: Player, waitSecond: Int) {
        send(waitForDieGiveCardToc {
            playerId = getAlternativeLocation(whoDie.location)
            waitingSecond = waitSecond
            if (whoDie === this@HumanPlayer) {
                val seq2 = this@HumanPlayer.seq
                seq = seq2
                timeout = GameExecutor.post(game!!, {
                    if (checkSeq(seq2)) {
                        incrSeq()
                        game!!.resolve(AfterDieGiveCard(game!!.fsm as WaitForDieGiveCard))
                    }
                }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
            }
        })
    }

    /**
     * 把自己的托管、掉线状态广播给所有玩家
     */
    fun notifyPlayerUpdateStatus() {
        game!!.players.send {
            notifyPlayerUpdateToc {
                playerId = it.getAlternativeLocation(location)
                isAuto = autoPlay
                isOffline = !isActive
            }
        }
    }

    /**
     * 把别人的托管、掉线状态发给自己
     */
    private fun notifyOtherPlayerStatus() {
        for (p in game!!.players) {
            if (p is HumanPlayer && p !== this && (!p.isActive || p.autoPlay)) {
                send(notifyPlayerUpdateToc {
                    playerId = p.getAlternativeLocation(location)
                    isAuto = autoPlay
                    isOffline = !isActive
                })
            }
        }
    }

    fun checkSeq(seq: Int): Boolean = this.seq == seq

    override fun incrSeq() {
        seq++
        timeout?.also { timeout ->
            if (timeout.isExpired) {
                if (game!!.timeoutSecond > 10 && !autoPlay && ++timeoutCount >= 2) setAutoPlay(true)
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
        private val printer = JsonFormat.printer().alwaysPrintFieldsWithNoPresence()

        /**
         * 随机选择一张牌作为情报传出
         */
        private fun autoSendMessageCard(r: Player) {
            val card =
                if (r.findSkill(HAN_HOU_LAO_SHI) == null) r.cards.first()
                else r.cards.firstOrNull { !it.isPureBlack() } ?: r.cards.first()
            val dir =
                if (r.findSkill(LIAN_LUO) == null) card.direction
                else listOf(Up, Left, Right).random()
            val availableTargets = r.game!!.players.filter { it !== r && it!!.alive }
            val target = when (dir) {
                Up -> availableTargets.random()!!
                Left -> r.getNextLeftAlivePlayer()
                else -> r.getNextRightAlivePlayer()
            }
            r.game!!.resolve(OnSendCard(r, r, card, dir, target, emptyList()))
        }
    }
}
