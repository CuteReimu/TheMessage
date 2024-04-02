package com.fengsheng

import akka.actor.ActorRef
import com.fengsheng.ScoreFactory.calScore
import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.Statistics.PlayerGameResult
import com.fengsheng.card.Card
import com.fengsheng.card.Deck
import com.fengsheng.network.Network
import com.fengsheng.phase.NextTurn
import com.fengsheng.phase.WaitForSelectRole
import com.fengsheng.protos.*
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.role.unknown
import com.fengsheng.protos.Common.secret_task
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.skill.*
import com.google.protobuf.GeneratedMessage
import com.google.protobuf.util.JsonFormat
import io.netty.util.Timeout
import org.apache.logging.log4j.kotlin.logger
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.random.nextInt

class Game(val id: Int, totalPlayerCount: Int, val actorRef: ActorRef) {
    var resolvingEvents = emptyList<Event>()
    private var unresolvedEvents = ArrayList<Event>()

    private var gameStartTimeout: Timeout? = null

    private var gameIdleTimeout: Timeout? = null

    @Volatile
    var isStarted = false

    @Volatile
    var isEnd = false
        private set
    var players: List<Player?> = List(totalPlayerCount) { null }
    var deck = Deck(this)
    var fsm: Fsm? = null
    var possibleSecretTasks: List<secret_task> = emptyList()
    var turn = 0
    val isEarly: Boolean
        get() = turn <= players.size - players.size / 2

    /**
     * 用于出牌阶段结束时提醒还未发动的技能
     */
    var mainPhaseAlreadyNotify = false

    fun setStartTimer() {
        val delay = if (Config.IsGmEnable || players.count { it is HumanPlayer } <= 1) 0L else 5L
        gameStartTimeout = GameExecutor.post(this, { start() }, delay, TimeUnit.SECONDS)
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
            players = players + null
            players.send { addOnePositionToc { } }
            index = players.size - 1
            cancelStartTimer()
        }
        players = players.toMutableList().apply { set(index, player) }
        player.location = index
        val unready = players.count { it == null }
        val msg = joinRoomToc {
            name = player.playerName
            position = player.location
            winCount = count.winCount
            gameCount = count.gameCount
            score = Statistics.getScore(name) ?: 0
            rank = if (player is HumanPlayer) ScoreFactory.getRankNameByScore(score) else ""
        }
        players.forEach { if (it !== player && it is HumanPlayer) it.send(msg) }
        if (unready == 0) {
            logger.info("${player.playerName}加入了。已加入${players.size}个人，游戏将在5秒内开始。。。")
            setStartTimer()
        } else {
            logger.info("${player.playerName}加入了。已加入${players.size - unready}个人，等待${unready}人加入。。。")
        }
        return true
    }

    private fun start() {
        players.all { it != null } || return
        !isStarted || return
        isStarted = true
        MiraiPusher.notifyStart()
        val identities = ArrayList<color>()
        when (players.size) {
            2 -> Random.nextInt(4).let {
                identities.add(color.forNumber(it and 1)!!)
                identities.add(color.forNumber(it and 2)!!)
            }

            3, 4 -> {
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
        val tasks = arrayListOf(Killer, Stealer, Collector, Pioneer)
        if (players.size >= 5) tasks.addAll(listOf(Mutator, Disturber, Sweeper))
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
        val possibleSecretTaskCount = when (players.size) {
            2, 3, 4 -> 3
            6 -> 4
            5, 7, 8 -> tasks.size
            else -> players.size - 4
        }
        possibleSecretTasks = tasks.take(possibleSecretTaskCount.coerceAtMost(tasks.size)).shuffled()
        val roleSkillsDataList = if (Config.IsGmEnable) RoleCache.getRandomRolesWithSpecific(
            players.size * 3,
            Config.DebugRoles
        ) else RoleCache.getRandomRoles(players.size * 3)
        resolve(WaitForSelectRole(this, players.indices.map {
            listOf(
                roleSkillsDataList[it],
                roleSkillsDataList[it + players.size],
                roleSkillsDataList[it + players.size * 2]
            ).filter { r -> r.role != unknown }
        }))
    }

    fun end(declaredWinners: List<Player>?, winners: List<Player>?, forceEnd: Boolean = false) {
        isEnd = true
        gameCache.remove(id)
        val humanPlayers = players.filterIsInstance<HumanPlayer>()
        val addScoreMap = HashMap<String, Int>()
        val newScoreMap = HashMap<String, Int>()
        if (declaredWinners != null && winners != null) {
            if (players.size >= 5) {
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
                        logger.info("$p(${p.originIdentity},${p.originSecretTask})得${score}分，新分数为：$newScore")
                        addScoreMap[p.playerName] = deltaScore
                        newScoreMap[p.playerName] = newScore
                    }
                }
                val playerGameResultList = ArrayList<PlayerGameResult>()
                if (players.size == humanPlayers.size) {
                    val records = ArrayList<Statistics.Record>(players.size)
                    for (p in players) {
                        records.add(
                            Statistics.Record(
                                p!!.originRole,
                                winners.any { it === p },
                                p.originIdentity,
                                p.originSecretTask,
                                players.size
                            )
                        )
                    }
                    Statistics.add(records)
                }
                for (p in humanPlayers) {
                    playerGameResultList.add(PlayerGameResult(p.playerName, winners.any { it === p }))
                }
                Statistics.addPlayerGameCount(playerGameResultList)
                Statistics.calculateRankList()
                if (humanPlayers.size > 1)
                    MiraiPusher.push(this, declaredWinners, winners, addScoreMap, newScoreMap)
            }
            this.players.forEach { it!!.notifyWin(declaredWinners, winners, addScoreMap, newScoreMap) }
        }
        humanPlayers.forEach { it.saveRecord() }
        humanPlayers.forEach { playerNameCache.remove(it.playerName) }
        players.forEach { it!!.reset() }
        if (forceEnd) humanPlayers.forEach { it.send(notifyKickedToc {}) }
        actorRef.tell(StopGameActor(), ActorRef.noSender())
    }

    /**
     * 玩家弃牌
     */
    fun playerDiscardCard(player: Player, card: Card) {
        playerDiscardCard(player, listOf(card))
    }

    /**
     * 玩家弃牌
     */
    fun playerDiscardCard(player: Player, cards: List<Card>) {
        if (cards.isEmpty()) return
        player.cards.removeAll(cards.toSet())
        logger.info("${player}弃掉了${cards.joinToString()}，剩余手牌${player.cards.size}张")
        deck.discard(cards)
        players.send {
            discardCardToc {
                playerId = it.getAlternativeLocation(player.location)
                cards.forEach { this.cards.add(it.toPbCard()) }
            }
        }
    }

    fun playerSetRoleFaceUp(player: Player?, faceUp: Boolean) {
        if (faceUp) {
            if (player!!.roleFaceUp) logger.error("${player}本来就是正面朝上的")
            else logger.info("${player}将角色翻至正面朝上")
            player.roleFaceUp = true
            player.hasEverFaceUp = true
        } else {
            if (!player!!.roleFaceUp) logger.error("${player}本来就是背面朝上的")
            else logger.info("${player}将角色翻至背面朝上")
            player.roleFaceUp = false
        }
        players.send {
            notifyRoleUpdateToc {
                playerId = it.getAlternativeLocation(player.location)
                role = if (player.roleFaceUp) player.role else unknown
            }
        }
    }

    fun allPlayerSetRoleFaceUp() {
        for (p in players) {
            if (!p!!.roleFaceUp) playerSetRoleFaceUp(p, true)
        }
    }

    /**
     * 将players按照从fromIndex开始逆时针顺序排序，不是这个游戏中的玩家会被排除
     */
    fun sortedFrom(players: Iterable<Player?>, fromIndex: Int): List<Player> {
        var i = fromIndex % this.players.size
        val newPlayers = ArrayList<Player>()
        do {
            val player = this.players[i]!!
            if (players.any { it === player }) newPlayers.add(player)
            i = (i + 1) % this.players.size
        } while (i != fromIndex % this.players.size)
        return newPlayers
    }

    inline fun <reified E : Event> findEvent(skill: Skill, predicate: (E) -> Boolean) =
        resolvingEvents.find { it is E && it.checkResolve(skill) && predicate(it) } as? E

    fun addEvent(event: Event) = unresolvedEvents.add(event)

    /**
     * 继续处理当前状态机
     */
    fun continueResolve() {
        gameIdleTimeout?.cancel()
        gameIdleTimeout = GameExecutor.post(this, {
            if (!isEnd) fsm?.let { resolve(NextTurn(it.whoseTurn)) }
        }, (Config.WaitSecond * 3).toLong(), TimeUnit.SECONDS)
        while (true) {
            val result = fsm!!.resolve() ?: break
            fsm = result.next
            if (!result.continueResolve) break
        }
    }

    private val printer = JsonFormat.printer().alwaysPrintFieldsWithNoPresence()

    /**
     * 对于[WaitingFsm]，当收到玩家协议时，继续处理当前状态机
     */
    fun tryContinueResolveProtocol(player: Player, pb: GeneratedMessage) {
        GameExecutor.post(this) {
            if (fsm !is WaitingFsm) {
                logger.error(
                    "时机错误，当前时点为：$fsm，收到: ${pb.javaClass.simpleName} | " +
                        printer.print(pb).replace("\n *".toRegex(), " ")
                )
                player.sendErrorMessage("时机错误")
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
    fun dealListeningSkill(beginLocation: Int): ResolveResult? {
        repeat(100) { // 写个100，防止死循环
            if (resolvingEvents.isEmpty()) {
                if (unresolvedEvents.isEmpty()) return null
                resolvingEvents = unresolvedEvents
                unresolvedEvents = ArrayList()
            }
            var i = beginLocation % players.size
            do {
                val player = players[i]!!
                player.skills.forEach { skill ->
                    if (player.alive || skill is BeforeDieSkill || !skill.isInitialSkill)
                        (skill as? TriggeredSkill)?.execute(this, player)?.let { return it }
                }
                i = (i + 1) % players.size
            } while (i != beginLocation % players.size)
            resolvingEvents = emptyList()
        }
        return null
    }

    /**
     * 判断是否仅剩的一个阵营存活
     */
    fun checkOnlyOneAliveIdentityPlayers(whoseTurn: Player): Boolean {
        var identity: color? = null
        val players = players.filterNotNull().filter { !it.lose }
        val alivePlayers = players.filter {
            if (!it.alive) return@filter false
            when (identity) {
                null -> identity = it.identity
                Black -> return false
                it.identity -> {}
                else -> {
                    // 四人局潜伏和军情会同时获胜
                    if (this.players.size != 4 || it.identity == Black)
                        return false
                }
            }
            true
        }
        val winner =
            if (identity == Red || identity == Blue) {
                // 四人局潜伏和军情会同时获胜
                if (this.players.size == 4)
                    players.filter { it.identity == Red || it.identity == Blue }.toMutableList()
                else
                    players.filter { identity == it.identity }.toMutableList()
            } else {
                alivePlayers.toMutableList()
            }
        val declaredWinners = ArrayList<Player>()
        changeGameResult(whoseTurn, declaredWinners, winner)
        logger.info("只剩下${alivePlayers.joinToString()}存活，胜利者有${winner.joinToString()}")
        this.players.send { unknownWaitingToc { } }
        GameExecutor.post(this, {
            allPlayerSetRoleFaceUp()
            end(declaredWinners, winner)
        }, 1, TimeUnit.SECONDS)
        return true
    }

    companion object {
        val playerCache = ConcurrentHashMap<String, HumanPlayer>()
        val gameCache = ConcurrentHashMap<Int, Game>()
        val playerNameCache = ConcurrentHashMap<String, HumanPlayer>()
        val increaseId = AtomicInteger(0)

        @Volatile
        var lastTotalPlayerCount = Config.TotalPlayerCount

        fun exchangePlayer(oldPlayer: HumanPlayer, newPlayer: HumanPlayer) {
            oldPlayer.channel = newPlayer.channel
            oldPlayer.needWaitLoad = newPlayer.needWaitLoad
            if (playerCache.put(newPlayer.channel.id().asLongText(), oldPlayer) == null) {
                logger.error("channel [id: ${newPlayer.channel.id().asLongText()}] not exists")
            }
        }

        val onlineCount: Int
            get() = gameCache.values.sumOf { it.players.count { p -> p != null } } +
                Random(System.currentTimeMillis() / 300000).run {
                    (0..nextInt(1..4)).sumOf { nextInt(5..9) }
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
