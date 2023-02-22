package com.fengsheng.networkimport

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.handler.ProtoHandler
import com.fengsheng.network.WebSocketServerChannelHandler
import com.fengsheng.protos.Common.card
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.leave_room_toc
import com.fengsheng.protos.Role
import com.google.protobuf.Descriptors
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.Parser
import com.google.protobuf.TextFormat
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import org.apache.log4j.Logger
import java.lang.reflect.InvocationTargetException
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

com.fengsheng.protos.Common.card_type
import com.fengsheng.card.Deck
import com.fengsheng.Game
import com.fengsheng.GameExecutor
import java.lang.Runnable
import com.fengsheng.card.ChengQing
import com.fengsheng.card.ShiTan
import com.fengsheng.card.WeiBi
import com.fengsheng.card.LiYou
import com.fengsheng.card.PingHeng
import com.fengsheng.card.PoYi
import com.fengsheng.card.JieHuo
import com.fengsheng.card.DiaoBao
import com.fengsheng.card.WuDao
import com.fengsheng.card.FengYunBianHuan
import java.lang.IllegalStateException
import com.fengsheng.Player
import com.fengsheng.gm.addcard
import java.lang.NumberFormatException
import java.lang.NullPointerException
import com.fengsheng.RobotPlayer
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.card
import java.lang.StringBuilder
import java.lang.RuntimeException
import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.Fsm
import com.fengsheng.card.PoYi.executePoYi
import com.fengsheng.phase.OnUseCard
import com.fengsheng.WaitingFsm
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
import com.fengsheng.card.PlayerAndCard
import java.util.Deque
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
import java.util.LinkedList
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
import java.util.EnumMap
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
import java.util.EnumSet
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
import java.util.concurrent.CountDownLatch
import com.fengsheng.network.WebSocketServerChannelHandler
import java.lang.InterruptedException
import com.fengsheng.Statistics.PlayerInfo
import com.fengsheng.protos.Fengsheng.get_room_info_toc
import com.fengsheng.protos.Errcode.error_code_toc
import com.fengsheng.protos.Fengsheng.leave_room_toc
import com.fengsheng.protos.Fengsheng.notify_die_give_card_toc
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import com.fengsheng.network.ProtoServerInitializer
import io.netty.channel.ChannelFuture
import com.fengsheng.network.WebSocketServerInitializer
import com.fengsheng.network.HttpServerInitializer
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.HttpServerCodec
import com.fengsheng.network.HttpServerChannelHandler
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleState
import com.fengsheng.network.HeartBeatServerHandler
import io.netty.handler.timeout.IdleStateHandler
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import java.nio.ByteOrder
import com.fengsheng.network.ProtoServerChannelHandler
import io.netty.channel.SimpleChannelInboundHandler
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
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ConcurrentHashMap
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicInteger
import com.fengsheng.protos.Record.player_order
import java.security.NoSuchAlgorithmException
import java.io.BufferedReader
import com.fengsheng.protos.Record.player_orders
import com.fengsheng.protos.Fengsheng.pb_order
import com.fengsheng.protos.Fengsheng.get_record_list_toc
import java.util.TimeZone
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.security.MessageDigest
import com.fengsheng.AbstractPlayer
import com.fengsheng.Limiter
import io.netty.channel.ChannelFutureListener
import com.fengsheng.protos.Fengsheng.init_toc
import com.fengsheng.protos.Fengsheng.add_card_toc
import com.fengsheng.protos.Fengsheng.send_message_card_toc
import com.fengsheng.protos.Fengsheng.notify_winner_toc
import com.fengsheng.protos.Fengsheng.wait_for_cheng_qing_toc
import com.fengsheng.protos.Fengsheng.wait_for_die_give_card_toc
import java.util.function.BiPredicate
import java.util.concurrent.BlockingQueue
import com.fengsheng.GameExecutor.GameAndCallback
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimer

class WebSocketServerChannelHandler : SimpleChannelInboundHandler<WebSocketFrame>() {
    @Throws(Exception::class)
    public override fun channelRead0(ctx: ChannelHandlerContext, webSocketFrame: WebSocketFrame) {
        WebSocketServerChannelHandler.Companion.log.debug("收到消息$webSocketFrame")
        if (webSocketFrame !is BinaryWebSocketFrame) {
            WebSocketServerChannelHandler.Companion.log.debug("仅支持二进制消息，不支持文本消息")
            throw UnsupportedOperationException(webSocketFrame.javaClass.name + " frame types not supported")
        }
        val msg = webSocketFrame.content()
        val protoNameLen = msg.readShortLE()
        if (msg.readableBytes() < protoNameLen) {
            WebSocketServerChannelHandler.Companion.log.error("incorrect proto name length: $protoNameLen")
            ctx.close()
            return
        }
        val protoNameBuf = ByteArray(protoNameLen.toInt())
        msg.readBytes(protoNameBuf)
        val protoName = kotlin.String(protoNameBuf)
        val protoInfo: WebSocketServerChannelHandler.ProtoInfo =
            WebSocketServerChannelHandler.Companion.ProtoInfoMap.get(protoName)
        if (protoInfo == null) {
            WebSocketServerChannelHandler.Companion.log.error("incorrect msg, proto name: $protoName")
            ctx.close()
            return
        }
        val buf = ByteArray(msg.readableBytes())
        msg.readBytes(buf)
        val message = protoInfo.parser.parseFrom(buf) as GeneratedMessageV3
        if ("heart_tos" != protoName && "auto_play_tos" != protoName) {
            WebSocketServerChannelHandler.Companion.log.debug(
                "recv@%s len: %d %s | %s".formatted(
                    ctx.channel().id().asShortText(), buf.size, protoName,
                    WebSocketServerChannelHandler.Companion.printer.printToString(message)
                        .replace("\n *".toRegex(), " ")
                )
            )
        }
        val player: HumanPlayer =
            WebSocketServerChannelHandler.Companion.playerCache.get(ctx.channel().id().asLongText())
        if (!player.limiter.allow()) {
            WebSocketServerChannelHandler.Companion.log.error("recv msg too fast: " + ctx.channel().id().asShortText())
            ctx.close()
            return
        }
        val handler = protoInfo.handler
        if (handler != null) {
            handler.handle(player, message)
        } else {
            WebSocketServerChannelHandler.Companion.log.warn("message: " + protoInfo.name + " doesn't have a handler")
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        WebSocketServerChannelHandler.Companion.log.info(
            "session connected: " + channel.id().asShortText() + " " + channel.remoteAddress()
        )
        val player = HumanPlayer(channel)
        if (WebSocketServerChannelHandler.Companion.playerCache.putIfAbsent(
                channel.id().asLongText(),
                player
            ) != null
        ) {
            WebSocketServerChannelHandler.Companion.log.error(
                "already assigned channel id: " + channel.id().asLongText()
            )
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        WebSocketServerChannelHandler.Companion.log.info(
            "session closed: " + channel.id().asShortText() + " " + channel.remoteAddress()
        )
        val player: HumanPlayer = WebSocketServerChannelHandler.Companion.playerCache.remove(channel.id().asLongText())
        if (player == null) {
            WebSocketServerChannelHandler.Companion.log.error(
                "already unassigned channel id: " + channel.id().asLongText()
            )
            return
        }
        val game = player.game ?: return
        var reply: GeneratedMessageV3? = null
        synchronized(Game::class.java) {
            if (game.isStarted) {
                GameExecutor.Companion.post(game, Runnable {
                    for (p in game.players) {
                        if (p is HumanPlayer && p.isActive) return@post
                    }
                    game.end(null)
                })
            } else {
                WebSocketServerChannelHandler.Companion.log.info(player.playerName + "离开了房间")
                game.players[player.location()] = null
                Game.Companion.deviceCache.remove(player.device, player)
                reply = leave_room_toc.newBuilder().setPosition(player.location()).build()
            }
        }
        if (reply != null) {
            for (p in game.players) {
                if (p is HumanPlayer) {
                    p.send(reply)
                }
            }
        }
    }

    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause is SocketException && "Connection reset" == cause.message) return
        super.exceptionCaught(ctx, cause)
    }

    private class ProtoInfo(name: String, parser: Parser<*>, handler: ProtoHandler) {
        val name: String
        val parser: Parser<*>
        val handler: ProtoHandler

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
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.inFrontOfWhom = inFrontOfWhom
            this.player = player
            this.whoseTurn = whoseTurn
            this.diedQueue = diedQueue
            this.afterDieResolve = afterDieResolve
            this.fightPhase = fightPhase
            this.player = player
            this.sendPhase = sendPhase
            this.dieGiveCard = dieGiveCard
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.inFrontOfWhom = inFrontOfWhom
            this.isMessageCardFaceUp = isMessageCardFaceUp
            this.waitForChengQing = waitForChengQing
            this.waitForChengQing = waitForChengQing
            this.whoseTurn = whoseTurn
            this.dyingQueue = dyingQueue
            this.diedQueue = diedQueue
            this.afterDieResolve = afterDieResolve
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.receiveOrder = receiveOrder
            this.inFrontOfWhom = inFrontOfWhom
            this.r = r
            this.fsm = fsm
            this.r = r
            this.playerAndCards = playerAndCards
            this.fsm = fsm
            this.selection = selection
            this.fromPlayer = fromPlayer
            this.waitingPlayer = waitingPlayer
            this.card = card
            this.r = r
            this.r = r
            this.target = target
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.target = target
            this.fsm = fsm
            this.fsm = fsm
            this.target = target
            this.needReturnCount = needReturnCount
            this.fsm = fsm
            this.fsm = fsm
            this.cards = cards
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.target = target
            this.fsm = fsm
            this.r = r
            this.target = target
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.color = color
            this.fsm = fsm
            this.color = color
            this.fsm = fsm
            this.r = r
            this.cards = cards
            this.fsm = fsm
            this.r = r
            this.target = target
            this.card = card
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.r = r
            this.cards = cards
            this.colors = colors
            this.defaultSelection = defaultSelection
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.target1 = target1
            this.card1 = card1
            this.name = name
            this.parser = parser
            this.handler = handler
            this.name = name
            this.parser = parser
            this.handler = handler
        }
    }

    companion object {
        private val log = Logger.getLogger(WebSocketServerChannelHandler::class.java)
        private val printer = TextFormat.printer().escapingNonAscii(false)
        private val ProtoInfoMap: Map<String, WebSocketServerChannelHandler.ProtoInfo> = HashMap()
        private val playerCache: ConcurrentMap<String, HumanPlayer> = ConcurrentHashMap()

        init {
            try {
                WebSocketServerChannelHandler.Companion.initProtocols(Fengsheng::class.java)
                WebSocketServerChannelHandler.Companion.initProtocols(Role::class.java)
            } catch (e: InvocationTargetException) {
                throw RuntimeException(e)
            } catch (e: NoSuchMethodException) {
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            } catch (e: ClassNotFoundException) {
                throw RuntimeException(e)
            } catch (e: InstantiationException) {
                throw RuntimeException(e)
            }
        }

        @Throws(
            NoSuchMethodException::class,
            InvocationTargetException::class,
            IllegalAccessException::class,
            ClassNotFoundException::class,
            InstantiationException::class
        )
        private fun initProtocols(protoCls: Class<*>) {
            val descriptor = protoCls.getDeclaredMethod("getDescriptor").invoke(null) as Descriptors.FileDescriptor
            for (d in descriptor.messageTypes) {
                val name = d.name
                if (!name.endsWith("_tos")) continue
                val id: Short = WebSocketServerChannelHandler.Companion.stringHash(name)
                if (id.toInt() == 0) {
                    throw RuntimeException("message meta require 'ID' field: $name")
                }
                val className = protoCls.name + "$" + name
                val cls = protoCls.classLoader.loadClass(className)
                val parser = cls.getDeclaredMethod("parser").invoke(null) as Parser<*>
                var handler: ProtoHandler? = null
                try {
                    val handlerClass = protoCls.classLoader.loadClass("com.fengsheng.handler.$name")
                    handler = handlerClass.getDeclaredConstructor().newInstance() as ProtoHandler
                } catch (ignored: ClassNotFoundException) {
                }
                if (WebSocketServerChannelHandler.Companion.ProtoInfoMap.putIfAbsent(
                        name,
                        WebSocketServerChannelHandler.ProtoInfo(name, parser, handler)
                    ) != null
                ) {
                    throw RuntimeException("Duplicate message meta register by id: $id")
                }
            }
        }

        fun exchangePlayer(oldPlayer: HumanPlayer, newPlayer: HumanPlayer) {
            oldPlayer.channel = newPlayer.channel
            if (WebSocketServerChannelHandler.Companion.playerCache.put(
                    newPlayer.channel.id().asLongText(),
                    oldPlayer
                ) == null
            ) {
                WebSocketServerChannelHandler.Companion.log.error(
                    "channel [id: " + newPlayer.channel.id().asLongText() + "] not exists"
                )
            }
        }

        fun stringHash(s: String): Short {
            var hash = 0
            for (c in s.toByteArray()) {
                val i = if (c >= 0) c.toInt() else 256 + c
                hash = (hash + (hash shl 5) + i + (i shl 7)).toShort().toInt()
            }
            return hash.toShort()
        }
    }
}