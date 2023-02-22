package com.fengsheng

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.*
import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.*
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.TextFormat
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.util.Timeout
import org.apache.log4j.Logger
import java.util.*
import java.util.concurrent.*

com.fengsheng.protos.Common.card_type
import java.lang.Runnable
import java.lang.IllegalStateException
import com.fengsheng.gm.addcard
import java.lang.NumberFormatException
import java.lang.NullPointerException
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.card
import java.lang.StringBuilder
import java.lang.RuntimeException
import com.fengsheng.protos.Fengsheng
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.card.PoYi.executePoYi
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Fengsheng.use_po_yi_toc
import com.fengsheng.protos.Fengsheng.po_yi_show_toc
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Fengsheng.use_li_you_toc
import com.fengsheng.skill.SkillId
import com.fengsheng.protos.Role.skill_jiu_ji_b_toc
import com.fengsheng.protos.Fengsheng.wei_bi_wait_for_give_card_toc
import com.fengsheng.card.WeiBi.executeWeiBi
import com.fengsheng.protos.Fengsheng.wei_bi_give_card_toc
import com.fengsheng.skill.Skill
import com.fengsheng.protos.Role.skill_cheng_fu_toc
import com.fengsheng.protos.Fengsheng.wei_bi_show_hand_card_toc
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Fengsheng.use_wu_dao_toc
import com.fengsheng.protos.Fengsheng.use_jie_huo_toc
import com.fengsheng.protos.Fengsheng.use_shi_tan_toc
import com.fengsheng.card.ShiTan.executeShiTan
import com.fengsheng.protos.Fengsheng.show_shi_tan_toc
import com.fengsheng.protos.Fengsheng.use_diao_bao_toc
import com.fengsheng.phase.WaitForChengQing
import com.fengsheng.phase.UseChengQingOnDying
import com.fengsheng.protos.Fengsheng.use_feng_yun_bian_huan_toc
import com.fengsheng.card.FengYunBianHuan.executeFengYunBianHuan
import com.fengsheng.protos.Fengsheng.wait_for_feng_yun_bian_huan_choose_card_toc
import com.fengsheng.phase.ReceiveOrder
import com.fengsheng.phase.CheckWin
import com.fengsheng.phase.StartWaitForChengQing
import com.fengsheng.phase.DieSkill.DieSkillNext
import com.fengsheng.phase.DieSkill
import com.fengsheng.phase.WaitForDieGiveCard
import com.fengsheng.skill.JinBi
import com.fengsheng.skill.QiangLing
import com.fengsheng.skill.JiangHuLing
import com.fengsheng.phase.DrawPhase
import com.fengsheng.phase.NextTurn
import com.fengsheng.phase.OnUseCard.OnUseCardNext
import com.fengsheng.phase.StartGame
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.ReceivePhase
import com.fengsheng.phase.ReceivePhaseSenderSkill
import com.fengsheng.phase.CheckKillerWin
import com.fengsheng.phase.FightPhaseNext
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.phase.MessageMoveNext
import com.fengsheng.protos.Fengsheng.notify_phase_toc
import com.fengsheng.skill.RoleSkillsData
import com.fengsheng.protos.Fengsheng.wait_for_select_role_toc
import com.fengsheng.protos.Common.role
import com.fengsheng.phase.WaitForSelectRole
import com.fengsheng.skill.JiBan
import com.fengsheng.skill.YingBian
import com.fengsheng.skill.YouDao
import com.fengsheng.phase.AfterDieGiveCard
import com.fengsheng.phase.OnChooseReceiveCard
import com.fengsheng.phase.WaitNextForChengQing
import com.fengsheng.phase.ReceivePhaseReceiverSkill
import com.fengsheng.skill.AbstractSkill
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.BoAi
import com.fengsheng.skill.BoAi.executeBoAi
import com.fengsheng.protos.Role.skill_bo_ai_a_toc
import com.fengsheng.protos.Role.skill_bo_ai_b_toc
import com.fengsheng.skill.DuJi
import com.fengsheng.protos.Role.skill_du_ji_a_toc
import com.fengsheng.skill.DuJi.TwoPlayersAndCard
import com.fengsheng.skill.DuJi.executeDuJiA
import com.fengsheng.protos.Role.skill_wait_for_du_ji_b_toc
import com.fengsheng.skill.DuJi.executeDuJiB
import com.fengsheng.protos.Role.skill_du_ji_b_toc
import com.fengsheng.protos.Role.skill_du_ji_c_toc
import com.fengsheng.skill.TriggeredSkill
import com.fengsheng.skill.FuHei
import com.fengsheng.skill.JiBan.executeJiBan
import com.fengsheng.protos.Role.skill_ji_ban_a_toc
import com.fengsheng.protos.Role.skill_ji_ban_b_toc
import com.fengsheng.skill.JinBi.executeJinBi
import com.fengsheng.protos.Role.skill_jin_bi_a_toc
import com.fengsheng.protos.Role.skill_jin_bi_b_toc
import com.fengsheng.skill.JinBi.JinBiSkill
import com.fengsheng.skill.JiuJi
import com.fengsheng.skill.JiZhi
import com.fengsheng.skill.RuGui.executeRuGui
import com.fengsheng.protos.Role.skill_wait_for_ru_gui_toc
import com.fengsheng.skill.ShiSi
import com.fengsheng.skill.SouJi
import com.fengsheng.skill.SouJi.executeSouJi
import com.fengsheng.protos.Role.skill_sou_ji_a_toc
import com.fengsheng.protos.Role.skill_sou_ji_b_toc
import com.fengsheng.skill.YiXin.executeYiXin
import com.fengsheng.protos.Role.skill_wait_for_yi_xin_toc
import com.fengsheng.skill.GuiZha
import com.fengsheng.skill.HuanRi
import com.fengsheng.skill.JiaoJi
import com.fengsheng.protos.Role.skill_jiao_ji_a_toc
import com.fengsheng.skill.JiaoJi.executeJiaoJi
import com.fengsheng.protos.Role.skill_jiao_ji_b_toc
import com.fengsheng.skill.JiSong
import com.fengsheng.protos.Role.skill_ji_song_toc
import com.fengsheng.skill.MingEr
import com.fengsheng.skill.ZhiYin
import com.fengsheng.skill.JianRen.executeJianRenA
import com.fengsheng.skill.JianRen.executeJianRenB
import com.fengsheng.skill.JianRen
import com.fengsheng.protos.Role.skill_jian_ren_a_toc
import com.fengsheng.protos.Role.skill_jian_ren_b_toc
import com.fengsheng.skill.JinShen.executeJinShen
import com.fengsheng.skill.LianMin.executeLianMin
import com.fengsheng.skill.TouTian
import com.fengsheng.skill.ChengZhi.executeChengZhi
import com.fengsheng.protos.Role.skill_wait_for_cheng_zhi_toc
import com.fengsheng.skill.JingMeng.executeJingMengA
import com.fengsheng.skill.JingMeng.executeJingMengB
import com.fengsheng.protos.Role.skill_jing_meng_a_toc
import com.fengsheng.protos.Role.skill_jing_meng_b_toc
import com.fengsheng.skill.MiaoShou
import com.fengsheng.skill.MiaoShou.executeMiaoShou
import com.fengsheng.protos.Role.skill_miao_shou_a_toc
import com.fengsheng.protos.Role.skill_miao_shou_b_toc
import com.fengsheng.skill.QiangLing.executeQiangLing
import com.fengsheng.protos.Role.skill_wait_for_qiang_ling_toc
import com.fengsheng.skill.QiHuoKeJu.executeQiHuoKeJu
import com.fengsheng.skill.XinSiChao
import com.fengsheng.skill.JinShen
import com.fengsheng.skill.LianLuo
import com.fengsheng.skill.QiHuoKeJu
import com.fengsheng.skill.MianLiCangZhen
import com.fengsheng.skill.YiYaHuanYa
import com.fengsheng.skill.YiHuaJieMu
import com.fengsheng.skill.LianMin
import com.fengsheng.skill.RuGui
import com.fengsheng.skill.ChengZhi
import com.fengsheng.skill.WeiSheng
import com.fengsheng.skill.ChengFu
import com.fengsheng.skill.YiXin
import com.fengsheng.skill.JingMeng
import com.fengsheng.skill.JieDaoShaRen
import com.fengsheng.skill.ZhuanJiao
import com.fengsheng.skill.MiaoBiQiaoBian
import com.fengsheng.skill.JinKouYiKai
import com.fengsheng.skill.GuangFaBao
import com.fengsheng.skill.DuiZhengXiaYao
import com.fengsheng.skill.RoleCache
import com.fengsheng.skill.ZhuanJiao.executeZhuanJiao
import com.fengsheng.protos.Role.skill_wait_for_zhuan_jiao_toc
import com.fengsheng.skill.GuangFaBao.executeGuangFaBao
import com.fengsheng.protos.Role.skill_wait_for_guang_fa_bao_b_toc
import com.fengsheng.protos.Role.skill_guang_fa_bao_b_toc
import com.fengsheng.skill.YiYaHuanYa.executeYiYaHuanYa
import com.fengsheng.skill.JiangHuLing.executeJiangHuLingA
import com.fengsheng.protos.Role.skill_wait_for_jiang_hu_ling_a_toc
import com.fengsheng.skill.JiangHuLing.JiangHuLing2
import com.fengsheng.skill.JiangHuLing.executeJiangHuLingB
import com.fengsheng.protos.Role.skill_wait_for_jiang_hu_ling_b_toc
import com.fengsheng.skill.JinKouYiKai.executeJinKouYiKai
import com.fengsheng.protos.Role.skill_jin_kou_yi_kai_a_toc
import com.fengsheng.skill.JieDaoShaRen.executeJieDaoShaRen
import com.fengsheng.protos.Role.skill_jie_dao_sha_ren_a_toc
import com.fengsheng.skill.DuiZhengXiaYao.executeDuiZhengXiaYaoA
import com.fengsheng.protos.Role.skill_dui_zheng_xia_yao_a_toc
import com.fengsheng.protos.Errcode
import com.fengsheng.skill.DuiZhengXiaYao.executeDuiZhengXiaYaoB
import com.fengsheng.protos.Role.skill_dui_zheng_xia_yao_b_toc
import com.fengsheng.protos.Role.skill_dui_zheng_xia_yao_c_toc
import com.fengsheng.skill.MianLiCangZhen.executeMianLiCangZhen
import com.fengsheng.skill.MiaoBiQiaoBian.executeMiaoBiQiaoBian
import com.fengsheng.protos.Role.skill_miao_bi_qiao_bian_a_toc
import com.fengsheng.handler.ProtoHandler
import com.fengsheng.handler.AbstractProtoHandler
import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.network.WebSocketServerChannelHandler
import java.lang.InterruptedException
import com.fengsheng.Statistics.PlayerInfo
import com.fengsheng.protos.Fengsheng.get_room_info_toc
import com.fengsheng.protos.Errcode.error_code_toc
import com.fengsheng.protos.Fengsheng.leave_room_toc
import com.fengsheng.protos.Fengsheng.notify_die_give_card_toc
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import com.fengsheng.network.ProtoServerInitializer
import com.fengsheng.network.WebSocketServerInitializer
import com.fengsheng.network.HttpServerInitializer
import io.netty.handler.codec.http.HttpServerCodec
import com.fengsheng.network.HttpServerChannelHandler
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleState
import com.fengsheng.network.HeartBeatServerHandler
import io.netty.handler.timeout.IdleStateHandler
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import java.nio.ByteOrder
import com.fengsheng.network.ProtoServerChannelHandler
import io.netty.handler.codec.http.HttpObject
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpHeaderNames
import java.net.URISyntaxException
import java.lang.ClassNotFoundException
import java.lang.reflect.InvocationTargetException
import java.lang.InstantiationException
import java.lang.IllegalAccessException
import java.net.SocketException
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import com.fengsheng.protos.Fengsheng.join_room_toc
import com.fengsheng.protos.Common.secret_task
import com.fengsheng.Statistics.PlayerGameResult
import com.fengsheng.protos.Fengsheng.discard_card_toc
import com.fengsheng.protos.Fengsheng.notify_role_update_toc
import java.io.IOException
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import com.fengsheng.protos.Record.recorder_line
import java.time.ZoneId
import com.fengsheng.protos.Record.record_file
import java.io.FilenameFilter
import java.io.DataInputStream
import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicInteger
import com.fengsheng.protos.Record.player_order
import java.security.NoSuchAlgorithmException
import java.io.BufferedReader
import com.fengsheng.protos.Record.player_orders
import com.fengsheng.protos.Fengsheng.pb_order
import com.fengsheng.protos.Fengsheng.get_record_list_toc
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.security.MessageDigest
import com.fengsheng.protos.Fengsheng.init_toc
import com.fengsheng.protos.Fengsheng.add_card_toc
import com.fengsheng.protos.Fengsheng.send_message_card_toc
import com.fengsheng.protos.Fengsheng.notify_winner_toc
import com.fengsheng.protos.Fengsheng.wait_for_cheng_qing_toc
import com.fengsheng.protos.Fengsheng.wait_for_die_give_card_toc
import java.util.function.BiPredicate
import com.fengsheng.GameExecutor.GameAndCallback
import io.netty.util.HashedWheelTimer

class HumanPlayer(var channel: Channel) : AbstractPlayer() {
    var seq = 0
        private set
    private var timeout: Timeout? = null
    private var timeoutCount = 0
    private val recorder = Recorder()
    var device: String? = null
    private var autoPlay = false
    val limiter = Limiter(10, 100, TimeUnit.MILLISECONDS)

    /**
     * 向玩家客户端发送协议
     */
    fun send(message: GeneratedMessageV3) {
        val buf = message.toByteArray()
        val name = message.descriptorForType.name
        recorder.add(name, buf)
        if (isActive) send(name, buf, true)
        HumanPlayer.Companion.log.debug(
            "send@%s len: %d %s | %s".formatted(
                channel.id().asShortText(), buf.size, name,
                HumanPlayer.Companion.printer.printToString(message).replace("\n *".toRegex(), " ")
            )
        )
    }

    fun send(protoName: String, buf: ByteArray, flush: Boolean) {
        val protoNameBuf = protoName.toByteArray()
        val totalLen = 2 + protoNameBuf.size + buf.size
        val byteBuf = Unpooled.buffer(totalLen)
        byteBuf.writeShortLE(protoNameBuf.size)
        byteBuf.writeBytes(protoNameBuf)
        byteBuf.writeBytes(buf)
        val frame = BinaryWebSocketFrame(byteBuf)
        val f = if (flush) channel.writeAndFlush(frame) else channel.write(frame)
        f.addListener(ChannelFutureListener { future: ChannelFuture ->
            if (!future.isSuccess) HumanPlayer.Companion.log.error(
                "send@%s failed, proto name: %s, len: %d".formatted(
                    channel.id().asShortText(), protoName, buf.size
                )
            )
        })
    }

    fun saveRecord() {
        recorder.save(game, this, channel.isActive)
    }

    fun loadRecord(version: Int, recordId: String?) {
        recorder.load(version, recordId, this)
    }

    val isLoadingRecord: Boolean
        get() = recorder.loading()

    fun pauseRecord(pause: Boolean) {
        recorder.pause(pause)
    }

    fun reconnect() {
        recorder.reconnect(this)
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
                    HumanPlayer.Companion.log.error("time task exception", e)
                }
            }
        } else {
            if (timeout != null && timeout!!.cancel()) {
                var delay = 16
                if (game.fsm is MainPhaseIdle || game.fsm is WaitForDieGiveCard) delay =
                    21 else if (game.fsm is WaitForSelectRole) delay = 31
                timeout =
                    GameExecutor.Companion.TimeWheel.newTimeout(timeout!!.task(), delay.toLong(), TimeUnit.SECONDS)
            }
        }
        send("auto_play_toc", Fengsheng.auto_play_toc.newBuilder().setEnable(autoPlay).build().toByteArray(), true)
    }

    override fun init() {
        super.init()
        val builder =
            init_toc.newBuilder().setPlayerCount(game.players.size).setIdentity(identity).setSecretTask(secretTask)
        var l = location
        do {
            builder.addRoles(if (game.players[l].isRoleFaceUp || l == location) game.players[l].role else Common.role.unknown)
            builder.addNames(game.players[l].playerName)
            l = (l + 1) % game.players.size
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
        val player = (game.fsm as DrawPhase).player
        val playerId = getAlternativeLocation(player.location())
        val builder = notify_phase_toc.newBuilder()
        builder.setCurrentPlayerId(playerId).setCurrentPhase(Common.phase.Draw_Phase).waitingPlayerId = playerId
        send(builder.build())
    }

    override fun notifyMainPhase(waitSecond: Int) {
        val player = (game.fsm as MainPhaseIdle).player
        val playerId = getAlternativeLocation(player.location())
        val builder = notify_phase_toc.newBuilder()
        builder.setCurrentPlayerId(playerId).setCurrentPhase(Common.phase.Main_Phase).waitingPlayerId = playerId
        builder.waitingSecond = waitSecond
        if (this === player) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.Companion.post(game, Runnable {
                if (checkSeq(seq2)) {
                    incrSeq()
                    game.resolve(SendPhaseStart(player))
                }
            }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
        }
        send(builder.build())
    }

    override fun notifySendPhaseStart(waitSecond: Int) {
        val player = (game.fsm as SendPhaseStart).player
        val playerId = getAlternativeLocation(player.location())
        val builder = notify_phase_toc.newBuilder()
        builder.setCurrentPlayerId(playerId).currentPhase = Common.phase.Send_Start_Phase
        builder.setWaitingPlayerId(playerId).waitingSecond = waitSecond
        if (this === player) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.Companion.post(game, Runnable {
                if (checkSeq(seq2)) {
                    incrSeq()
                    RobotPlayer.Companion.autoSendMessageCard(this, false)
                }
            }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
        }
        send(builder.build())
    }

    override fun notifySendMessageCard(
        player: Player,
        targetPlayer: Player,
        lockedPlayers: Array<Player>,
        messageCard: Card,
        dir: direction?
    ) {
        val builder = send_message_card_toc.newBuilder()
        builder.playerId = getAlternativeLocation(player.location())
        builder.targetPlayerId = getAlternativeLocation(targetPlayer.location())
        builder.cardDir = dir
        if (player === this) builder.cardId = messageCard.id
        for (p in lockedPlayers) builder.addLockPlayerIds(getAlternativeLocation(p.location()))
        send(builder.build())
    }

    override fun notifySendPhase(waitSecond: Int) {
        val fsm = game.fsm as SendPhaseIdle
        val playerId = getAlternativeLocation(fsm.whoseTurn.location())
        val builder = notify_phase_toc.newBuilder()
        builder.setCurrentPlayerId(playerId).currentPhase = Common.phase.Send_Phase
        builder.messagePlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location())
        builder.waitingPlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location())
        builder.setMessageCardDir(fsm.dir).waitingSecond = waitSecond
        if (fsm.isMessageCardFaceUp) builder.messageCard = fsm.messageCard.toPbCard()
        if (this === fsm.inFrontOfWhom) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.Companion.post(game, Runnable {
                if (checkSeq(seq2)) {
                    incrSeq()
                    var isLocked = false
                    for (p in fsm.lockedPlayers) {
                        if (p === this) {
                            isLocked = true
                            break
                        }
                    }
                    if (isLocked || fsm.inFrontOfWhom === fsm.whoseTurn) game.resolve(
                        OnChooseReceiveCard(
                            fsm.whoseTurn,
                            fsm.messageCard,
                            fsm.inFrontOfWhom,
                            fsm.isMessageCardFaceUp
                        )
                    ) else game.resolve(MessageMoveNext(fsm))
                }
            }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
        }
        send(builder.build())
    }

    override fun notifyChooseReceiveCard(player: Player) {
        send(Fengsheng.choose_receive_toc.newBuilder().setPlayerId(getAlternativeLocation(player.location())).build())
    }

    override fun notifyFightPhase(waitSecond: Int) {
        val fsm = game.fsm as FightPhaseIdle
        val builder = notify_phase_toc.newBuilder()
        builder.currentPlayerId = getAlternativeLocation(fsm.whoseTurn.location())
        builder.messagePlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location())
        builder.waitingPlayerId = getAlternativeLocation(fsm.whoseFightTurn.location())
        builder.setCurrentPhase(Common.phase.Fight_Phase).waitingSecond = waitSecond
        if (fsm.isMessageCardFaceUp) builder.messageCard = fsm.messageCard.toPbCard()
        if (this === fsm.whoseFightTurn) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.Companion.post(game, Runnable {
                if (checkSeq(seq2)) {
                    incrSeq()
                    game.resolve(FightPhaseNext(fsm))
                }
            }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
        }
        send(builder.build())
    }

    override fun notifyReceivePhase() {
        val fsm = game.fsm as ReceivePhase
        val builder = notify_phase_toc.newBuilder()
        builder.currentPlayerId = getAlternativeLocation(fsm.whoseTurn.location())
        builder.messagePlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location())
        builder.waitingPlayerId = getAlternativeLocation(fsm.inFrontOfWhom.location())
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
        builder.currentPlayerId = getAlternativeLocation(whoseTurn.location())
        builder.messagePlayerId = getAlternativeLocation(inFrontOfWhom.location())
        builder.waitingPlayerId = getAlternativeLocation(waitingPlayer.location())
        builder.setCurrentPhase(Common.phase.Receive_Phase).setMessageCard(messageCard.toPbCard()).waitingSecond =
            waitSecond
        if (this === waitingPlayer) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.Companion.post(
                game,
                Runnable {
                    if (checkSeq(seq2)) game.tryContinueResolveProtocol(
                        this,
                        Fengsheng.end_receive_phase_tos.newBuilder().setSeq(seq2).build()
                    )
                },
                getWaitSeconds(waitSecond + 2).toLong(),
                TimeUnit.SECONDS
            )
        }
        send(builder.build())
    }

    override fun notifyDying(location: Int, loseGame: Boolean) {
        super.notifyDying(location, loseGame)
        send(
            Fengsheng.notify_dying_toc.newBuilder().setPlayerId(getAlternativeLocation(location)).setLoseGame(loseGame)
                .build()
        )
    }

    override fun notifyDie(location: Int) {
        super.notifyDie(location)
        send(Fengsheng.notify_die_toc.newBuilder().setPlayerId(getAlternativeLocation(location)).build())
    }

    override fun notifyWin(declareWinners: Array<Player>, winners: Array<Player>) {
        val builder = notify_winner_toc.newBuilder()
        val declareWinnerIds: MutableList<Int> = ArrayList()
        for (p in declareWinners) declareWinnerIds.add(getAlternativeLocation(p.location()))
        Collections.sort(declareWinnerIds)
        builder.addAllDeclarePlayerIds(declareWinnerIds)
        val winnerIds: MutableList<Int> = ArrayList()
        for (p in winners) winnerIds.add(getAlternativeLocation(p.location()))
        Collections.sort(winnerIds)
        builder.addAllWinnerIds(winnerIds)
        for (i in game.players.indices) {
            val p = game.players[(location + i) % game.players.size]
            builder.addIdentity(p.identity).addSecretTasks(p.secretTask)
        }
        send(builder.build())
    }

    override fun notifyAskForChengQing(whoDie: Player, askWhom: Player, waitSecond: Int) {
        val builder = wait_for_cheng_qing_toc.newBuilder()
        builder.diePlayerId = getAlternativeLocation(whoDie.location())
        builder.waitingPlayerId = getAlternativeLocation(askWhom.location())
        builder.waitingSecond = waitSecond
        if (askWhom === this) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.Companion.post(game, Runnable {
                if (checkSeq(seq2)) {
                    incrSeq()
                    game.resolve(WaitNextForChengQing(game.fsm as WaitForChengQing))
                }
            }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
        }
        send(builder.build())
    }

    override fun waitForDieGiveCard(whoDie: Player, waitSecond: Int) {
        val builder = wait_for_die_give_card_toc.newBuilder()
        builder.playerId = getAlternativeLocation(whoDie.location())
        builder.waitingSecond = waitSecond
        if (whoDie === this) {
            builder.seq = seq
            val seq2 = seq
            timeout = GameExecutor.Companion.post(game, Runnable {
                if (checkSeq(seq2)) {
                    incrSeq()
                    game.resolve(AfterDieGiveCard(game.fsm as WaitForDieGiveCard))
                }
            }, getWaitSeconds(waitSecond + 2).toLong(), TimeUnit.SECONDS)
        }
        send(builder.build())
    }

    /**
     * 把跟玩家有关的计时器绑定在玩家身上，例如操作超时等待。这样在玩家操作后就可以清掉这个计时器，以节约资源
     */
    fun setTimeout(timeout: Timeout?) {
        this.timeout = timeout
    }

    fun checkSeq(seq: Int): Boolean {
        return this.seq == seq
    }

    override fun incrSeq() {
        seq++
        if (timeout != null) {
            if (timeout!!.isExpired) {
                if (!autoPlay && ++timeoutCount >= 3) setAutoPlay(true)
            } else {
                timeout!!.cancel()
            }
            timeout = null
        }
    }

    fun clearTimeoutCount() {
        timeoutCount = 0
    }

    fun getWaitSeconds(seconds: Int): Int {
        if (!isActive) {
            return 5
        }
        return if (autoPlay) 1 else seconds
    }

    companion object {
        private val log = Logger.getLogger(HumanPlayer::class.java)
        private val printer = TextFormat.printer().escapingNonAscii(false)
    }
}