package com.fengsheng

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.*
import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.notify_die_give_card_toc
import com.fengsheng.skill.*
import io.netty.util.Timeout
import io.netty.util.TimerTask
import org.apache.log4j.Logger
import java.util.*
import java.util.concurrent.*
import java.util.function.BiPredicate
import java.util.function.Predicate

com.fengsheng.protos.Common.card_type
import com.fengsheng.Game
import com.fengsheng.GameExecutor
import java.lang.Runnable
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
import com.fengsheng.GameExecutor.GameAndCallback
import io.netty.util.HashedWheelTimer

class RobotPlayer : AbstractPlayer() {
    override fun notifyAddHandCard(location: Int, unknownCount: Int, vararg cards: Card) {
        // Do nothing
    }

    override fun notifyDrawPhase() {
        // Do nothing
    }

    override fun notifyMainPhase(waitSecond: Int) {
        val fsm = game.fsm as MainPhaseIdle
        if (this !== fsm.player) return
        for (skill in skills) {
            val ai: BiPredicate<MainPhaseIdle, ActiveSkill> = RobotPlayer.Companion.aiSkillMainPhase.get(skill.skillId)
            if (ai != null && ai.test(fsm, skill as ActiveSkill)) return
        }
        if (cards.size > 1 && findSkill<Skill?>(SkillId.JI_SONG) == null && (findSkill<Skill?>(SkillId.GUANG_FA_BAO) == null || isRoleFaceUp)) {
            for (card in cards.values) {
                val ai: BiPredicate<MainPhaseIdle, Card> = RobotPlayer.Companion.aiMainPhase.get(card.type)
                if (ai != null && ai.test(fsm, card)) return
            }
        }
        GameExecutor.Companion.post(game, Runnable { game.resolve(SendPhaseStart(this)) }, 2, TimeUnit.SECONDS)
    }

    override fun notifySendPhaseStart(waitSecond: Int) {
        val fsm = game.fsm as SendPhaseStart
        if (this !== fsm.player) return
        GameExecutor.Companion.post(
            game,
            Runnable { RobotPlayer.Companion.autoSendMessageCard(this, true) },
            2,
            TimeUnit.SECONDS
        )
    }

    override fun notifySendMessageCard(
        player: Player,
        targetPlayer: Player,
        lockedPlayers: Array<Player>,
        messageCard: Card,
        direction: direction?
    ) {
        // Do nothing
    }

    override fun notifySendPhase(waitSecond: Int) {
        val fsm = game.fsm as SendPhaseIdle
        if (this !== fsm.inFrontOfWhom) return
        if (this !== game.jinBiPlayer) {
            for (card in cards.values) {
                val ai: BiPredicate<SendPhaseIdle, Card> = RobotPlayer.Companion.aiSendPhase.get(card.type)
                if (ai != null && ai.test(fsm, card)) return
            }
        }
        GameExecutor.Companion.post(game, Runnable {
            val colors = fsm.messageCard.colors
            val certainlyReceive = fsm.isMessageCardFaceUp && colors.size == 1 && colors[0] != color.Black
            val certainlyReject = fsm.isMessageCardFaceUp && colors.size == 1 && colors[0] == color.Black
            if (certainlyReceive || Arrays.asList(*fsm.lockedPlayers)
                    .contains(this) || fsm.whoseTurn === this || !certainlyReject && ThreadLocalRandom.current()
                    .nextInt((game.players.size - 1) * 2) == 0
            ) game.resolve(
                OnChooseReceiveCard(
                    fsm.whoseTurn,
                    fsm.messageCard,
                    fsm.inFrontOfWhom,
                    fsm.isMessageCardFaceUp
                )
            ) else game.resolve(MessageMoveNext(fsm))
        }, 2, TimeUnit.SECONDS)
    }

    override fun notifyChooseReceiveCard(player: Player) {
        // Do nothing
    }

    override fun notifyFightPhase(waitSecond: Int) {
        val fsm = game.fsm as FightPhaseIdle
        if (this !== fsm.whoseFightTurn) return
        for (skill in skills) {
            val ai: BiPredicate<FightPhaseIdle, ActiveSkill> =
                RobotPlayer.Companion.aiSkillFightPhase.get(skill.skillId)
            if (ai != null && ai.test(fsm, skill as ActiveSkill)) return
        }
        for (card in cards.values) {
            var cardType: card_type? = card.type
            if (findSkill<Skill?>(SkillId.YING_BIAN) != null && cardType == Common.card_type.Jie_Huo) cardType =
                Common.card_type.Wu_Dao
            val ai: BiPredicate<FightPhaseIdle, Card> = RobotPlayer.Companion.aiFightPhase.get(cardType)
            if (ai != null && ai.test(fsm, card)) return
        }
        GameExecutor.Companion.post(game, Runnable { game.resolve(FightPhaseNext(fsm)) }, 2, TimeUnit.SECONDS)
    }

    override fun notifyReceivePhase() {
        // Do nothing
    }

    override fun notifyReceivePhase(
        whoseTurn: Player,
        inFrontOfWhom: Player,
        messageCard: Card,
        waitingPlayer: Player,
        waitSecond: Int
    ) {
        if (waitingPlayer !== this) return
        for (skill in skills) {
            val ai: Predicate<Fsm> = RobotPlayer.Companion.aiSkillReceivePhase.get(skill.skillId)
            if (ai != null && ai.test(game.fsm)) return
        }
        GameExecutor.Companion.TimeWheel.newTimeout(TimerTask { timeout: Timeout? ->
            game.tryContinueResolveProtocol(
                this,
                Fengsheng.end_receive_phase_tos.getDefaultInstance()
            )
        }, 2, TimeUnit.SECONDS)
    }

    override fun notifyWin(declareWinners: Array<Player>, winners: Array<Player>) {
        // Do nothing
    }

    override fun notifyAskForChengQing(whoDie: Player, askWhom: Player, waitSecond: Int) {
        val fsm = game.fsm as WaitForChengQing
        if (askWhom !== this) return
        GameExecutor.Companion.post(game, Runnable { game.resolve(WaitNextForChengQing(fsm)) }, 2, TimeUnit.SECONDS)
    }

    override fun waitForDieGiveCard(whoDie: Player, waitSecond: Int) {
        val fsm = game.fsm as WaitForDieGiveCard
        if (whoDie !== this) return
        GameExecutor.Companion.post(game, Runnable {
            if (identity != color.Black) {
                for (target in game.players) {
                    if (target !== this && target.identity == identity) {
                        val giveCards: MutableList<Card> = ArrayList()
                        for (card in cards.values) {
                            giveCards.add(card)
                            if (giveCards.size >= 3) break
                        }
                        if (!giveCards.isEmpty()) {
                            val cards = giveCards.toTypedArray()
                            for (card in cards) deleteCard(card.id)
                            target.addCard(*cards)
                            RobotPlayer.Companion.log.info(this.toString() + "给了" + target + Arrays.toString(cards))
                            for (p in game.players) {
                                if (p is HumanPlayer) {
                                    val builder = notify_die_give_card_toc.newBuilder()
                                    builder.playerId = p.getAlternativeLocation(location)
                                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                                    if (p === target) {
                                        for (card in cards) builder.addCard(card.toPbCard())
                                    } else {
                                        builder.unknownCardCount = cards.size
                                    }
                                    p.send(builder.build())
                                }
                            }
                        }
                        break
                    }
                }
            }
            game.resolve(AfterDieGiveCard(fsm))
        }, 2, TimeUnit.SECONDS)
    }

    companion object {
        private val log = Logger.getLogger(RobotPlayer::class.java)

        /**
         * 随机选择一张牌作为情报传出
         *
         * @param lock 是否考虑锁定
         */
        fun autoSendMessageCard(r: Player, lock: Boolean) {
            var card: Card? = null
            for (c in r.cards.values) {
                card = c
                break
            }
            assert(card != null)
            val random: Random = ThreadLocalRandom.current()
            val fsm = r.game.fsm as SendPhaseStart
            var dir = card.getDirection()
            if (r.findSkill<Skill?>(SkillId.LIAN_LUO) != null) {
                dir = direction.forNumber(random.nextInt(3))
                assert(dir != null)
            }
            var targetLocation = 0
            val availableLocations: MutableList<Int> = ArrayList()
            var lockedPlayer: Player? = null
            for (p in r.game.players) {
                if (p !== r && p.isAlive) availableLocations.add(p.location())
            }
            if (dir != direction.Up && lock && card!!.canLock() && random.nextInt(3) != 0) {
                val player = r.game.players[availableLocations[random.nextInt(availableLocations.size)]]
                if (player.isAlive) lockedPlayer = player
            }
            when (dir) {
                direction.Up -> {
                    targetLocation = availableLocations[random.nextInt(availableLocations.size)]
                    if (lock && card!!.canLock() && random.nextBoolean()) lockedPlayer = r.game.players[targetLocation]
                }

                direction.Left -> targetLocation = r.nextLeftAlivePlayer.location()
                direction.Right -> targetLocation = r.nextRightAlivePlayer.location()
            }
            r.game.resolve(
                OnSendCard(
                    fsm.player, card, dir, r.game.players[targetLocation],
                    if (lockedPlayer == null) arrayOfNulls(0) else arrayOf(lockedPlayer)
                )
            )
        }

        private val aiSkillMainPhase = EnumMap<SkillId, BiPredicate<MainPhaseIdle, ActiveSkill>>(
            SkillId::class.java
        )
        private val aiMainPhase: EnumMap<card_type, BiPredicate<MainPhaseIdle, Card>> =
            EnumMap<card_type, BiPredicate<MainPhaseIdle, Card>>(
                card_type::class.java
            )
        private val aiSendPhase: EnumMap<card_type, BiPredicate<SendPhaseIdle, Card>> =
            EnumMap<card_type, BiPredicate<SendPhaseIdle, Card>>(
                card_type::class.java
            )
        private val aiSkillFightPhase = EnumMap<SkillId, BiPredicate<FightPhaseIdle, ActiveSkill>>(
            SkillId::class.java
        )
        private val aiFightPhase: EnumMap<card_type, BiPredicate<FightPhaseIdle, Card>> =
            EnumMap<card_type, BiPredicate<FightPhaseIdle, Card>>(
                card_type::class.java
            )
        private val aiSkillReceivePhase = EnumMap<SkillId, Predicate<Fsm>>(
            SkillId::class.java
        )

        init {
            RobotPlayer.Companion.aiSkillMainPhase.put(
                SkillId.XIN_SI_CHAO,
                BiPredicate { e: MainPhaseIdle, skill: ActiveSkill -> XinSiChao.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillMainPhase.put(
                SkillId.GUI_ZHA,
                BiPredicate { e: MainPhaseIdle, skill: ActiveSkill -> GuiZha.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillMainPhase.put(
                SkillId.JIAO_JI,
                BiPredicate { e: MainPhaseIdle, skill: ActiveSkill -> JiaoJi.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillMainPhase.put(
                SkillId.JIN_BI,
                BiPredicate { e: MainPhaseIdle, skill: ActiveSkill -> JinBi.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillMainPhase.put(
                SkillId.JI_BAN,
                BiPredicate { e: MainPhaseIdle, skill: ActiveSkill -> JiBan.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillMainPhase.put(
                SkillId.BO_AI,
                BiPredicate { e: MainPhaseIdle, skill: ActiveSkill -> BoAi.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.TOU_TIAN,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> TouTian.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.JI_ZHI,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> JiZhi.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.YI_HUA_JIE_MU,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> YiHuaJieMu.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.JIE_DAO_SHA_REN,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> JieDaoShaRen.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.GUANG_FA_BAO,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> GuangFaBao.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.JI_SONG,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> JiSong.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.MIAO_BI_QIAO_BIAN,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> MiaoBiQiaoBian.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.JIN_KOU_YI_KAI,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> JinKouYiKai.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.MIAO_SHOU,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> MiaoShou.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.SOU_JI,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> SouJi.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.DUI_ZHENG_XIA_YAO,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> DuiZhengXiaYao.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.DU_JI,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> DuJi.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.JIN_SHEN,
                Predicate { fsm0: Fsm -> JinShen.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.LIAN_MIN,
                Predicate { fsm0: Fsm -> LianMin.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.MIAN_LI_CANG_ZHEN,
                Predicate { fsm0: Fsm -> MianLiCangZhen.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.QI_HUO_KE_JU,
                Predicate { fsm0: Fsm -> QiHuoKeJu.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.YI_YA_HUAN_YA,
                Predicate { fsm0: Fsm -> YiYaHuanYa.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.JING_MENG,
                Predicate { fsm0: Fsm -> JingMeng.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.JIAN_REN,
                Predicate { fsm0: Fsm -> JianRen.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiMainPhase.put(
                Common.card_type.Cheng_Qing,
                BiPredicate { e: MainPhaseIdle, card: Card -> ChengQing.Companion.ai(e, card) })
            RobotPlayer.Companion.aiMainPhase.put(
                Common.card_type.Li_You,
                BiPredicate { e: MainPhaseIdle, card: Card -> LiYou.Companion.ai(e, card) })
            RobotPlayer.Companion.aiMainPhase.put(
                Common.card_type.Ping_Heng,
                BiPredicate { e: MainPhaseIdle, card: Card -> PingHeng.Companion.ai(e, card) })
            RobotPlayer.Companion.aiMainPhase.put(
                Common.card_type.Shi_Tan,
                BiPredicate { e: MainPhaseIdle, card: Card -> ShiTan.Companion.ai(e, card) })
            RobotPlayer.Companion.aiMainPhase.put(
                Common.card_type.Wei_Bi,
                BiPredicate { e: MainPhaseIdle, card: Card -> WeiBi.Companion.ai(e, card) })
            RobotPlayer.Companion.aiSendPhase.put(
                Common.card_type.Po_Yi,
                BiPredicate { e: SendPhaseIdle, card: Card -> PoYi.Companion.ai(e, card) })
            RobotPlayer.Companion.aiMainPhase.put(
                Common.card_type.Feng_Yun_Bian_Huan,
                BiPredicate { e: MainPhaseIdle, card: Card -> FengYunBianHuan.Companion.ai(e, card) })
            RobotPlayer.Companion.aiFightPhase.put(
                Common.card_type.Diao_Bao,
                BiPredicate { e: FightPhaseIdle, card: Card -> DiaoBao.Companion.ai(e, card) })
            RobotPlayer.Companion.aiFightPhase.put(
                Common.card_type.Jie_Huo,
                BiPredicate { e: FightPhaseIdle, card: Card -> JieHuo.Companion.ai(e, card) })
            RobotPlayer.Companion.aiFightPhase.put(
                Common.card_type.Wu_Dao,
                BiPredicate { e: FightPhaseIdle, card: Card -> WuDao.Companion.ai(e, card) })
        }
    }
}