package com.fengsheng

import com.fengsheng.Game
import com.fengsheng.Player
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.*
import com.fengsheng.skill.RoleSkillsData
import com.fengsheng.skill.Skill
import com.fengsheng.skill.SkillId
import java.util.concurrent.ThreadLocalRandom

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
import com.fengsheng.GameExecutor.GameAndCallback
import io.netty.util.HashedWheelTimer

interface Player {
    fun setRoleSkillsData(roleSkillsData: RoleSkillsData?)
    fun init()

    /**
     * 玩家停止计时器，并且seq值加一
     */
    fun incrSeq()
    var game: Game?

    /**
     * 获取玩家的名字
     */
    var playerName: String?

    /**
     * 玩家在服务器上的座位号，也就是在数组中的index
     */
    fun location(): Int

    /**
     * 设置玩家在服务器上的座位号，也就是在数组中的index
     */
    fun setLocation(location: Int)

    /**
     * 根据玩家的相对座位号获取玩家在服务器上的座位号
     */
    fun getAbstractLocation(location: Int): Int

    /**
     * 根据玩家在服务器上的座位号获取玩家的相对座位号
     */
    fun getAlternativeLocation(location: Int): Int

    /**
     * 通知有玩家摸牌了
     *
     * @param location     摸牌的玩家在服务器上的座位号
     * @param unknownCount 该玩家摸到的看不到的牌的数量
     * @param cards        该玩家摸到的能看到的卡牌
     */
    fun notifyAddHandCard(location: Int, unknownCount: Int, vararg cards: Card)

    /**
     * 玩家摸牌
     */
    fun draw(n: Int)

    /**
     * 把卡加入玩家手牌
     */
    fun addCard(vararg cards: Card)

    /**
     * 获取玩家手牌
     */
    val cards: Map<Int, Card>

    /**
     * 从玩家手牌中查找一张牌
     */
    fun findCard(cardId: Int): Card?

    /**
     * 从玩家手牌中去掉一张牌
     */
    fun deleteCard(cardId: Int): Card?

    /**
     * 玩家删除所有手牌
     *
     * @return 被删除的所有手牌
     */
    fun deleteAllCards(): Array<Card>

    /**
     * 把卡加入玩家情报
     */
    fun addMessageCard(vararg cards: Card)

    /**
     * 获取玩家情报
     */
    val messageCards: Map<Int, Card>

    /**
     * 从玩家情报中查找一张牌
     */
    fun findMessageCard(cardId: Int): Card?

    /**
     * 从玩家情报中去掉一张牌
     */
    fun deleteMessageCard(cardId: Int): Card?

    /**
     * 玩家删除所有情报
     *
     * @return 被删除的所有情报
     */
    fun deleteAllMessageCards(): Array<Card>

    /**
     * 判断新增的情报是否会导致玩家有三张同色
     *
     * @param cards 将要增加的牌
     */
    fun checkThreeSameMessageCard(vararg cards: Card): Boolean

    /**
     * 通知进入了某名玩家的摸牌阶段
     */
    fun notifyDrawPhase()

    /**
     * 通知进入了某名玩家的出牌阶段
     *
     * @param waitSecond 超时时间
     */
    fun notifyMainPhase(waitSecond: Int)

    /**
     * 通知进入了某名玩家的情报传递阶段开始时
     *
     * @param waitSecond 超时时间
     */
    fun notifySendPhaseStart(waitSecond: Int)
    fun notifySendMessageCard(
        player: Player,
        targetPlayer: Player,
        lockedPlayers: Array<Player>,
        messageCard: Card,
        direction: direction?
    )

    /**
     * 通知进入了某名玩家的情报传递阶段
     *
     * @param waitSecond 超时时间
     */
    fun notifySendPhase(waitSecond: Int)

    /**
     * 通知某名玩家选择接收情报
     */
    fun notifyChooseReceiveCard(player: Player)

    /**
     * 通知进入了某名玩家的争夺阶段
     *
     * @param waitSecond 超时时间
     */
    fun notifyFightPhase(waitSecond: Int)

    /**
     * 通知进入了某名玩家的情报接收阶段，用于刚刚确定成功接收情报时
     */
    fun notifyReceivePhase()

    /**
     * 通知进入了某名玩家的情报接收阶段，用于询问情报接收阶段的技能
     *
     * @param whoseTurn     谁的回合
     * @param inFrontOfWhom 情报在谁面前
     * @param messageCard   情报牌
     * @param waitingPlayer 等待的那个玩家
     * @param waitSecond    超时时间
     */
    fun notifyReceivePhase(
        whoseTurn: Player,
        inFrontOfWhom: Player,
        messageCard: Card,
        waitingPlayer: Player,
        waitSecond: Int
    )

    /**
     * 获取是否是女性角色
     */
    val isFemale: Boolean

    /**
     * 通知某名玩家已确定死亡（用于通知客户端把头像置灰）
     *
     * @param location 死亡的玩家在服务器上的座位号
     * @param loseGame 是否因为没有手牌可以作为情报传递而输掉游戏导致的死亡
     */
    fun notifyDying(location: Int, loseGame: Boolean)

    /**
     * 通知某名玩家死亡了（用于通知客户端弃掉所有情报）
     *
     * @param location 死亡的玩家在服务器上的座位号
     */
    fun notifyDie(location: Int)

    /**
     * 通知胜利
     *
     * @param declareWinners 宣胜的玩家
     * @param winners        胜利的玩家，包含宣胜的玩家
     */
    fun notifyWin(declareWinners: Array<Player>, winners: Array<Player>)

    /**
     * 通知有人在濒死求澄清
     *
     * @param whoDie     濒死的玩家
     * @param askWhom    被询问是否使用澄清救人的玩家
     * @param waitSecond 超时时间
     */
    fun notifyAskForChengQing(whoDie: Player, askWhom: Player, waitSecond: Int)

    /**
     * 通知有人正在选择死亡给的三张牌
     *
     * @param whoDie     死亡的玩家
     * @param waitSecond 超时时间
     */
    fun waitForDieGiveCard(whoDie: Player, waitSecond: Int)
    var isAlive: Boolean
    var isLose: Boolean
    /**
     * 获得玩家的身份。
     */
    /**
     * 设置玩家的身份
     */
    var identity: color?
    /**
     * 获得玩家的初始身份（不受换身份的影响）
     */
    /**
     * 设置玩家的初始身份（不受换身份的影响）
     */
    var originIdentity: color?
    /**
     * 获得玩家的机密任务
     */
    /**
     * 设置玩家的机密任务
     */
    var secretTask: secret_task?
    /**
     * 获得玩家的初始机密任务（不受换身份的影响）
     */
    /**
     * 设置玩家的初始机密任务（不受换身份的影响）
     */
    var originSecretTask: secret_task?
    fun setSkills(skills: Array<Skill>)
    fun getSkills(): Array<Skill?>?
    fun <T : Skill?> findSkill(skillId: SkillId): T?
    val roleName: String?

    /**
     * 获得玩家的角色
     */
    val role: role?
    /**
     * 获得玩家的角色牌是否面朝上
     */
    /**
     * 设置玩家的角色牌是否面朝上
     */
    var isRoleFaceUp: Boolean

    /**
     * 增加每回合技能使用次数计数
     */
    fun addSkillUseCount(skillId: SkillId)

    /**
     * 增加每回合技能使用次数计数
     */
    fun addSkillUseCount(skillId: SkillId, count: Int)

    /**
     * 获取每回合技能使用次数计数
     */
    fun getSkillUseCount(skillId: SkillId): Int

    /**
     * 重置每回合技能使用次数计数
     */
    fun resetSkillUseCount()

    /**
     * 重置每回合技能使用次数计数
     */
    fun resetSkillUseCount(skillId: SkillId)

    /**
     * 获取左手边下一个存活的玩家
     */
    val nextLeftAlivePlayer: Player?

    /**
     * 获取右手边下一个存活的玩家
     */
    val nextRightAlivePlayer: Player?

    companion object {
        /**
         * （日志用）将颜色转为角色身份的字符串
         */
        fun identityColorToString(color: color): String? {
            return when (color) {
                color.Red -> "红方"
                color.Blue -> "蓝方"
                color.Black -> "神秘人"
                else -> throw RuntimeException("unknown color: $color")
            }
        }

        /**
         * （日志用）将颜色转为角色身份的字符串
         */
        fun identityColorToString(color: color, task: secret_task): String? {
            return when (color) {
                color.Red -> "红方"
                color.Blue -> "蓝方"
                color.Black -> when (task) {
                    secret_task.Killer -> "神秘人[镇压者]"
                    secret_task.Stealer -> "神秘人[簒夺者]"
                    secret_task.Collector -> "神秘人[双重间谍]"
                    secret_task.Mutator -> "神秘人[诱变者]"
                    secret_task.Pioneer -> "神秘人[先行者]"
                    else -> throw RuntimeException("unknown secret task: $task")
                }

                color.Has_No_Identity -> "无身份"
                else -> throw RuntimeException("unknown color: $color")
            }
        }

        fun randPlayerName(): String? {
            return Integer.toString(ThreadLocalRandom.current().nextInt(Int.MAX_VALUE))
        }
    }
}