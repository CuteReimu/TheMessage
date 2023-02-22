package com.fengsheng

import com.fengsheng.AbstractPlayer
import com.fengsheng.card.*
import com.fengsheng.protos.Common.*
import com.fengsheng.skill.RoleSkillsData
import com.fengsheng.skill.Skill
import com.fengsheng.skill.SkillId
import com.fengsheng.skill.TriggeredSkill
import org.apache.log4j.Logger
import java.util.*

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

abstract class AbstractPlayer protected constructor() : Player {
    protected var game: Game? = null
    protected var playerName: String? = null
    protected var location = 0
    protected val cards: MutableMap<Int, Card>
    protected val messageCards: MutableMap<Int, Card>
    protected var identity: color? = null
    protected var secretTask: secret_task? = null
    private var originIdentity: color? = null
    private var originSecretTask: secret_task? = null
    var alive = true
    var lose = false
    protected var roleSkillsData: RoleSkillsData
    protected val skillUseCount: EnumMap<SkillId, Int>

    init {
        cards = HashMap()
        messageCards = HashMap()
        roleSkillsData = RoleSkillsData()
        skillUseCount = EnumMap(SkillId::class.java)
    }

    override fun setRoleSkillsData(roleSkillsData: RoleSkillsData?) {
        this.roleSkillsData = roleSkillsData?.let { RoleSkillsData(it) } ?: RoleSkillsData()
    }

    override fun init() {
        AbstractPlayer.Companion.log.info(
            this.toString() + "的身份是" + Player.Companion.identityColorToString(
                identity,
                secretTask
            )
        )
        for (skill in roleSkillsData.skills) {
            (skill as? TriggeredSkill)?.init(game)
        }
    }

    override fun incrSeq() {}
    override fun getGame(): Game? {
        return game
    }

    override fun setGame(game: Game?) {
        this.game = game
    }

    override fun getPlayerName(): String? {
        return playerName
    }

    override fun setPlayerName(name: String?) {
        playerName = name
    }

    override fun location(): Int {
        return location
    }

    override fun setLocation(location: Int) {
        this.location = location
    }

    override fun getAbstractLocation(location: Int): Int {
        return (location + this.location) % game!!.players.size
    }

    override fun getAlternativeLocation(location: Int): Int {
        var location = location
        location -= this.location
        if (location < 0) {
            location += game!!.players.size
        }
        return location
    }

    override fun draw(n: Int) {
        val cards = game!!.deck.draw(n)
        addCard(*cards)
        AbstractPlayer.Companion.log.info(this.toString() + "摸了" + Arrays.toString(cards) + "，现在有" + this.cards.size + "张手牌")
        for (player in game!!.players) {
            if (player === this) player.notifyAddHandCard(location, 0, *cards) else player.notifyAddHandCard(
                location,
                cards.size
            )
        }
    }

    override fun addCard(vararg cards: Card) {
        for (card in cards) {
            this.cards[card.id] = card
        }
    }

    override fun getCards(): Map<Int, Card> {
        return cards
    }

    override fun findCard(cardId: Int): Card? {
        return cards[cardId]
    }

    override fun deleteCard(cardId: Int): Card? {
        return cards.remove(cardId)
    }

    override fun deleteAllCards(): Array<Card> {
        val cards = cards.values.toTypedArray()
        this.cards.clear()
        return cards
    }

    override fun addMessageCard(vararg cards: Card) {
        for (card in cards) {
            messageCards[card.id] = card
        }
    }

    override fun getMessageCards(): Map<Int, Card> {
        return messageCards
    }

    override fun findMessageCard(cardId: Int): Card? {
        return messageCards[cardId]
    }

    override fun deleteMessageCard(cardId: Int): Card? {
        return messageCards.remove(cardId)
    }

    override fun deleteAllMessageCards(): Array<Card> {
        val cards = messageCards.values.toTypedArray()
        messageCards.clear()
        return cards
    }

    override fun checkThreeSameMessageCard(vararg cards: Card): Boolean {
        var red = 0
        var blue = 0
        var black = 0
        for (card in cards) {
            for (c in card.colors) {
                when (c) {
                    color.Red -> red++
                    color.Blue -> blue++
                    color.Black -> black++
                }
            }
        }
        val hasRed = red > 0
        val hasBlue = blue > 0
        val hasBlack = black > 0
        for (card in messageCards.values) {
            for (c in card.colors) {
                when (c) {
                    color.Red -> red++
                    color.Blue -> blue++
                    color.Black -> black++
                }
            }
        }
        return hasRed && red >= 3 || hasBlue && blue >= 3 || hasBlack && black >= 3
    }

    override fun notifyDying(location: Int, loseGame: Boolean) {}
    override fun notifyDie(location: Int) {
        if (this.location == location) {
            game!!.playerDiscardCard(this, *cards.values.toTypedArray())
            game!!.deck.discard(*messageCards.values.toTypedArray())
        }
    }

    override fun setAlive(alive: Boolean) {
        this.alive = alive
    }

    override fun isAlive(): Boolean {
        return alive
    }

    override fun setLose(lose: Boolean) {
        this.lose = lose
    }

    override fun isLose(): Boolean {
        return lose
    }

    override fun getIdentity(): color? {
        return identity
    }

    override fun setIdentity(identity: color?) {
        this.identity = identity
    }

    override fun getOriginIdentity(): color? {
        return originIdentity
    }

    override fun setOriginIdentity(identity: color?) {
        originIdentity = identity
    }

    override fun getSecretTask(): secret_task? {
        return secretTask
    }

    override fun setSecretTask(secretTask: secret_task?) {
        this.secretTask = secretTask
    }

    override fun getOriginSecretTask(): secret_task? {
        return originSecretTask
    }

    override fun setOriginSecretTask(secretTask: secret_task?) {
        originSecretTask = secretTask
    }

    override fun setSkills(skills: Array<Skill>) {
        roleSkillsData.skills = skills
    }

    override fun getSkills(): Array<Skill?>? {
        return roleSkillsData.skills
    }

    override fun <T : Skill?> findSkill(skillId: SkillId): T? {
        for (skill in skills!!) {
            if (skill!!.skillId == skillId) {
                return skill as T?
            }
        }
        return null
    }

    override fun getRoleName(): String? {
        return roleSkillsData.name
    }

    override fun getRole(): role? {
        return roleSkillsData.role
    }

    override fun isRoleFaceUp(): Boolean {
        return roleSkillsData.isFaceUp
    }

    override fun setRoleFaceUp(faceUp: Boolean) {
        roleSkillsData.isFaceUp = faceUp
    }

    override fun isFemale(): Boolean {
        return roleSkillsData.isFemale
    }

    override fun addSkillUseCount(skillId: SkillId) {
        addSkillUseCount(skillId, 1)
    }

    override fun addSkillUseCount(skillId: SkillId, count: Int) {
        skillUseCount.compute(skillId) { k: SkillId?, v: Int? -> if (v == null) count else v + count }
    }

    override fun getSkillUseCount(skillId: SkillId): Int {
        return Objects.requireNonNullElse(skillUseCount[skillId], 0)
    }

    override fun resetSkillUseCount() {
        skillUseCount.clear()
    }

    /**
     * 重置每回合技能使用次数计数
     */
    override fun resetSkillUseCount(skillId: SkillId) {
        skillUseCount.remove(skillId)
    }

    override fun getNextLeftAlivePlayer(): Player? {
        var left: Int
        left = location - 1
        while (left != location) {
            if (left < 0) left += game!!.players.size
            if (game!!.players[left].isAlive) break
            left--
        }
        return game!!.players[left]
    }

    override fun getNextRightAlivePlayer(): Player? {
        var right: Int
        right = location + 1
        while (right != location) {
            if (right >= game!!.players.size) right -= game!!.players.size
            if (game!!.players[right].isAlive) break
            right++
        }
        return game!!.players[right]
    }

    override fun toString(): String {
        return location.toString() + "号[" + roleName + (if (isRoleFaceUp) "" else "(隐)") + "]"
    }

    companion object {
        private val log = Logger.getLogger(AbstractPlayer::class.java)
    }
}