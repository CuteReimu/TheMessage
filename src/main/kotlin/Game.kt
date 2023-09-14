package com.fengsheng

import com.fengsheng.ScoreFactory.calScore
import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.Statistics.PlayerGameResult
import com.fengsheng.card.Card
import com.fengsheng.card.Deck
import com.fengsheng.network.Network
import com.fengsheng.phase.WaitForSelectRole
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.skill.RoleCache
import com.fengsheng.skill.SkillId.WEI_SHENG
import com.fengsheng.skill.TriggeredSkill
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.TextFormat
import io.netty.util.Timeout
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import org.apache.log4j.Logger
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class Game private constructor(totalPlayerCount: Int) {
    val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    val id: Int = ++increaseId

    private var gameStartTimeout: Timeout? = null

    @Volatile
    var isStarted = false

    @Volatile
    var isEnd = false
        private set
    var players: Array<Player?>
    var deck = Deck(this)
    var fsm: Fsm? = null
    var possibleSecretTasks: List<secret_task> = emptyList()

    /**
     * 用于王田香技能禁闭
     */
    var jinBiPlayer: Player? = null

    /**
     * 用于张一挺技能强令
     */
    val qiangLingTypes = HashSet<card_type>()

    /** 用于调虎离山禁用出牌 */
    val diaoHuLiShanPlayers = HashSet<Int>()

    init {
        // 调用构造函数时加锁了，所以increaseId无需加锁
        players = arrayOfNulls(totalPlayerCount)
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            while (!isEnd) try {
                val callBack = queue.receive()
                callBack()
            } catch (_: ClosedReceiveChannelException) {
                break
            } catch (e: Exception) {
                log.error("catch throwable", e)
            }
        }
    }

    fun setStartTimer() {
        gameStartTimeout = GameExecutor.post(this, { start() }, 5, TimeUnit.SECONDS)
    }

    fun cancelStartTimer() {
        gameStartTimeout?.cancel()
        gameStartTimeout = null
    }

    /**
     * 玩家进入房间时调用
     */
    fun onPlayerJoinRoom(player: Player, count: PlayerGameCount): Boolean {
        var index = players.indexOfFirst { it == null }
        if (index < 0) {
            if (players.size >= 9 || player is RobotPlayer) return false
            players = arrayOf(*players, null)
            for (p in players)
                (p as? HumanPlayer)?.send(add_one_position_toc.getDefaultInstance())
            index = players.size - 1
            cancelStartTimer()
        }
        players[index] = player
        player.location = index
        val unready = players.count { it == null }
        val builder = join_room_toc.newBuilder()
        val name = player.playerName
        val score = Statistics.getScore(name) ?: 0
        val rank = if (player is HumanPlayer) ScoreFactory.getRankNameByScore(score) else ""
        builder.name = name
        builder.position = player.location
        builder.winCount = count.winCount
        builder.gameCount = count.gameCount
        builder.rank = rank
        builder.score = score
        val msg = builder.build()
        players.forEach { if (it !== player && it is HumanPlayer) it.send(msg) }
        if (unready == 0) {
            log.info("${player.playerName}加入了。已加入${players.size}个人，游戏将在5秒内开始。。。")
            setStartTimer()
        } else {
            log.info("${player.playerName}加入了。已加入${players.size - unready}个人，等待${unready}人加入。。。")
        }
        return true
    }

    private fun start() {
        players.all { it != null } || return
        !isStarted || return
        isStarted = true
        newInstance()
        MiraiPusher.notifyStart()
        val identities = ArrayList<color>()
        when (players.size) {
            2 -> Random.nextInt(4).let {
                identities.add(color.forNumber(it and 1)!!)
                identities.add(color.forNumber(it and 2)!!)
            }

            3 -> {
                identities.add(Red)
                identities.add(Blue)
                identities.add(Black)
            }

            4 -> {
                identities.add(Red)
                identities.add(Blue)
                identities.add(Black)
                identities.add(Black)
            }

            5, 6, 7, 8 -> {
                var i = 0
                while (i < (players.size - 1) / 2) {
                    identities.add(Red)
                    identities.add(Blue)
                    i++
                }
                identities.add(Black)
                if (players.size % 2 == 0) identities.add(Black)
            }

            else -> {
                identities.add(Red)
                identities.add(Red)
                identities.add(Red)
                identities.add(Blue)
                identities.add(Blue)
                identities.add(Blue)
                repeat(players.size - identities.size) { identities.add(Black) }
            }
        }
        identities.shuffle()
        val tasks = arrayListOf(Killer, Stealer, Collector, Mutator, Pioneer)
        if (players.size >= 5) tasks.addAll(listOf(Disturber, Sweeper))
        tasks.shuffle()
        var secretIndex = 0
        for (i in players.indices) {
            val identity = identities[i]
            val task = if (identity == Black) tasks[secretIndex++] else secret_task.forNumber(0)
            players[i]!!.identity = identity
            players[i]!!.secretTask = task
            players[i]!!.originIdentity = identity
            players[i]!!.originSecretTask = task
        }
        val possibleSecretTaskCount = when {
            players.size <= 5 -> 3
            players.size <= 8 -> 4
            else -> minOf(players.size - 4, tasks.size)
        }
        possibleSecretTasks = tasks.subList(0, possibleSecretTaskCount).shuffled()
        val roleSkillsDataArray = if (Config.IsGmEnable) RoleCache.getRandomRolesWithSpecific(
            players.size * 3,
            Config.DebugRoles
        ) else RoleCache.getRandomRoles(players.size * 3)
        resolve(WaitForSelectRole(this, players.indices.map {
            arrayOf(
                roleSkillsDataArray[it],
                roleSkillsDataArray[it + players.size],
                roleSkillsDataArray[it + players.size * 2]
            ).filter { r -> r.role != role.unknown }
        }))
    }

    fun end(declaredWinners: List<Player>?, winners: List<Player>?, forceEnd: Boolean = false) {
        isEnd = true
        GameCache.remove(id)
        val humanPlayers = players.filterIsInstance<HumanPlayer>()
        val addScoreMap = HashMap<String, Int>()
        val newScoreMap = HashMap<String, Int>()
        if (declaredWinners != null && winners != null) {
            if (players.size == humanPlayers.size && players.size >= 5) {
                if (winners.isNotEmpty() && winners.size < players.size) {
                    val totalWinners = winners.sumOf { (Statistics.getScore(it.playerName) ?: 0).coerceIn(180..1900) }
                    val totalPlayers = players.sumOf { (Statistics.getScore(it!!.playerName) ?: 0).coerceIn(180..1900) }
                    val totalLoser = totalPlayers - totalWinners
                    val delta = totalLoser / (players.size - winners.size) - totalWinners / winners.size
                    for ((i, p) in humanPlayers.withIndex()) {
                        val score = p.calScore(players.filterNotNull(), winners, delta / 10)
                        val (newScore, deltaScore) = Statistics.updateScore(
                            p.playerName,
                            score,
                            i == humanPlayers.size - 1
                        )
                        log.info("${p}(${p.originIdentity},${p.originSecretTask})得${score}分，新分数为：${newScore}")
                        addScoreMap[p.playerName] = deltaScore
                        newScoreMap[p.playerName] = newScore
                    }
                    Statistics.calculateRankList()
                }
                val records = ArrayList<Statistics.Record>(players.size)
                val playerGameResultList = ArrayList<PlayerGameResult>()
                for (p in players) {
                    val win = p!! in winners
                    records.add(
                        Statistics.Record(
                            p.originRole,
                            win,
                            p.originIdentity,
                            p.originSecretTask,
                            players.size
                        )
                    )
                    if (p is HumanPlayer) playerGameResultList.add(PlayerGameResult(p.playerName, win))
                }
                Statistics.add(records)
                Statistics.addPlayerGameCount(playerGameResultList)
                MiraiPusher.push(this, declaredWinners, winners, addScoreMap, newScoreMap)
            }
            this.players.forEach { it!!.notifyWin(declaredWinners, winners, addScoreMap, newScoreMap) }
        }
        humanPlayers.forEach { it.saveRecord() }
        humanPlayers.forEach { playerNameCache.remove(it.playerName) }
        players.forEach { it!!.reset() }
        if (forceEnd) humanPlayers.forEach { it.send(notify_kicked_toc.getDefaultInstance()) }
        queue.close()
    }

    /**
     * 玩家弃牌
     */
    fun playerDiscardCard(player: Player, vararg cards: Card) {
        if (cards.isEmpty()) return
        player.cards.removeAll(cards.toSet())
        log.info("${player}弃掉了${cards.contentToString()}，剩余手牌${player.cards.size}张")
        deck.discard(*cards)
        for (p in players) {
            if (p is HumanPlayer) {
                val builder = discard_card_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(player.location)
                cards.forEach { builder.addCards(it.toPbCard()) }
                p.send(builder.build())
            }
        }
    }

    fun playerSetRoleFaceUp(player: Player?, faceUp: Boolean) {
        if (faceUp) {
            if (player!!.roleFaceUp) log.error("${player}本来就是正面朝上的")
            else log.info("${player}将角色翻至正面朝上")
            player.roleFaceUp = true
        } else {
            if (!player!!.roleFaceUp) log.error("${player}本来就是背面朝上的")
            else log.info("${player}将角色翻至背面朝上")
            player.roleFaceUp = false
        }
        for (p in players) {
            if (p is HumanPlayer) {
                val builder = notify_role_update_toc.newBuilder().setPlayerId(
                    p.getAlternativeLocation(player.location)
                )
                builder.role = if (player.roleFaceUp) player.role else role.unknown
                p.send(builder.build())
            }
        }
    }

    fun allPlayerSetRoleFaceUp() {
        for (p in players) {
            if (!p!!.roleFaceUp) playerSetRoleFaceUp(p, true)
        }
    }

    /**
     * 继续处理当前状态机
     */
    fun continueResolve() {
        while (true) {
            val result = fsm!!.resolve() ?: break
            fsm = result.next
            if (!result.continueResolve) break
        }
    }

    private val printer = TextFormat.printer().escapingNonAscii(false)

    /**
     * 对于[WaitingFsm]，当收到玩家协议时，继续处理当前状态机
     */
    fun tryContinueResolveProtocol(player: Player, pb: GeneratedMessageV3) {
        GameExecutor.post(this) {
            if (fsm !is WaitingFsm) {
                log.error(
                    "时机错误，当前时点为：$fsm，收到: ${pb.javaClass.simpleName} | " +
                            printer.printToString(pb).replace("\n *".toRegex(), " ")
                )
                (player as? HumanPlayer)?.sendErrorMessage("时机错误")
                return@post
            }
            val result = (fsm as WaitingFsm).resolveProtocol(player, pb)
            if (result != null) {
                fsm = result.next
                if (result.continueResolve) {
                    continueResolve()
                }
            }
        }
    }

    /**
     * 更新一个新的状态机并结算，只能由游戏所在线程调用
     */
    fun resolve(fsm: Fsm?) {
        this.fsm = fsm
        continueResolve()
    }

    /**
     * 遍历监听列表，结算技能
     */
    fun dealListeningSkill(): ResolveResult? {
        players.forEach { player ->
            player!!.skills.forEach { skill ->
                (skill as? TriggeredSkill)?.execute(this)?.let { return it }
            }
        }
        return null
    }

    /**
     * 判断是否仅剩的一个阵营存活
     */
    fun checkOnlyOneAliveIdentityPlayers(): Boolean {
        var identity: color? = null
        val players = players.filterNotNull().filter { !it.lose }
        val alivePlayers = players.filter {
            if (!it.alive) return@filter false
            when (identity) {
                null -> identity = it.identity
                Black -> return false
                it.identity -> {}
                else -> {
                    if (this.players.size != 4 || it.identity == Black) // 四人局潜伏和军情会同时获胜
                        return false
                }
            }
            true
        }
        val winner =
            if (identity == Red || identity == Blue) {
                if (this.players.size == 4) // 四人局潜伏和军情会同时获胜
                    players.filter { it.identity == Red || it.identity == Blue }.toMutableList()
                else
                    players.filter { identity == it.identity }.toMutableList()
            } else {
                alivePlayers.toMutableList()
            }
        if (winner.any { it.findSkill(WEI_SHENG) != null && it.roleFaceUp })
            winner.addAll(players.filter { it.identity == Has_No_Identity })
        val winners = winner.toTypedArray()
        log.info("只剩下${alivePlayers.toTypedArray().contentToString()}存活，胜利者有${winners.contentToString()}")
        allPlayerSetRoleFaceUp()
        end(emptyList(), winner)
        return true
    }

    companion object {
        private val log = Logger.getLogger(Game::class.java)
        val playerCache = ConcurrentHashMap<String, HumanPlayer>()
        val GameCache = ConcurrentHashMap<Int, Game>()
        val playerNameCache = ConcurrentHashMap<String, HumanPlayer>()
        private var increaseId = 0
        private var lastTotalPlayerCount = Config.TotalPlayerCount
        var newGame = Game(lastTotalPlayerCount)

        fun exchangePlayer(oldPlayer: HumanPlayer, newPlayer: HumanPlayer) {
            oldPlayer.channel = newPlayer.channel
            oldPlayer.needWaitLoad = newPlayer.needWaitLoad
            if (playerCache.put(newPlayer.channel.id().asLongText(), oldPlayer) == null) {
                log.error("channel [id: ${newPlayer.channel.id().asLongText()}] not exists")
            }
        }

        /**
         * 不是线程安全的
         */
        fun newInstance() {
            if (newGame.players.all { it is HumanPlayer } && newGame.players.size >= 5)
                lastTotalPlayerCount = newGame.players.size + 1
            newGame = Game(lastTotalPlayerCount.coerceIn(minOf(5, Config.TotalPlayerCount)..8))
        }

        @Throws(IOException::class, ClassNotFoundException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            Class.forName("com.fengsheng.skill.RoleCache")
            ScoreFactory.load()
            Statistics.load()
            Network.init()
        }
    }
}