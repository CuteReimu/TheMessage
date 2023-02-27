package com.fengsheng

import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.Statistics.PlayerGameResult
import com.fengsheng.card.Card
import com.fengsheng.card.Deck
import com.fengsheng.network.Network
import com.fengsheng.phase.WaitForSelectRole
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.skill.RoleCache
import com.fengsheng.skill.TriggeredSkill
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.math.max
import kotlin.random.Random

class Game private constructor(totalPlayerCount: Int) {

    val id: Int = ++increaseId

    @Volatile
    var isStarted = false

    @Volatile
    var isEnd = false
        private set
    var players: Array<Player?>
    var deck: Deck = Deck(this)
    var fsm: Fsm? = null
        private set
    val listeningSkills = ArrayList<TriggeredSkill>()

    /**
     * 用于王田香技能禁闭
     */
    var jinBiPlayer: Player? = null
    /**
     * 用于张一挺技能强令
     */
    /**
     * 用于张一挺技能强令
     */
    val qiangLingTypes = HashSet<card_type>()

    init {
        // 调用构造函数时加锁了，所以increaseId无需加锁
        players = arrayOfNulls(totalPlayerCount)
    }

    /**
     * 玩家进入房间时调用
     */
    fun onPlayerJoinRoom(player: Player, count: PlayerGameCount?) {
        var unready = -1
        for (index in players.indices) {
            if (players[index] == null && ++unready == 0) {
                players[index] = player
                player.location = index
            }
        }
        val builder = join_room_toc.newBuilder().setName(player.playerName).setPosition(player.location)
        if (count != null) {
            builder.winCount = count.winCount
            builder.gameCount = count.gameCount
        }
        val msg = builder.build()
        for (p in players) {
            if (p !== player && p is HumanPlayer) {
                p.send(msg)
            }
        }
        if (unready == 0) {
            log.info(player.playerName + "加入了。已加入" + players.size + "个人，游戏开始。。。")
            isStarted = true
            GameExecutor.post(this) { start() }
            newGame = newInstance()
        } else {
            log.info(player.playerName + "加入了。已加入" + (players.size - unready) + "个人，等待" + unready + "人加入。。。")
        }
    }

    fun start() {
        var identities = ArrayList<color>()
        when (players.size) {
            2 -> identities = when (Random.nextInt(4)) {
                0 -> arrayListOf(color.Red, color.Blue)
                1 -> arrayListOf(color.Red, color.Black)
                2 -> arrayListOf(color.Blue, color.Black)
                else -> arrayListOf(color.Black, color.Black)
            }

            9 -> {
                identities.add(color.Red)
                identities.add(color.Red)
                identities.add(color.Blue)
                identities.add(color.Blue)
                identities.add(color.Black)
                identities.add(color.Red)
                identities.add(color.Blue)
                identities.add(color.Black)
                identities.add(color.Black)
            }

            4 -> {
                identities.add(color.Red)
                identities.add(color.Blue)
                identities.add(color.Black)
                identities.add(color.Black)
            }

            3 -> {
                identities.add(color.Red)
                identities.add(color.Blue)
                identities.add(color.Black)
            }

            else -> {
                var i = 0
                while (i < (players.size - 1) / 2) {
                    identities.add(color.Red)
                    identities.add(color.Blue)
                    i++
                }
                identities.add(color.Black)
                if (players.size % 2 == 0) identities.add(color.Black)
            }
        }
        identities.shuffle()
        val tasks = listOf(
            secret_task.Killer,
            secret_task.Stealer,
            secret_task.Collector,
            secret_task.Mutator,
            secret_task.Pioneer
        ).shuffled()
        var secretIndex = 0
        for (i in players.indices) {
            val identity = identities[i]
            val task = if (identity == color.Black) tasks[secretIndex++] else secret_task.forNumber(0)
            players[i]!!.identity = identity
            players[i]!!.secretTask = task
            players[i]!!.originIdentity = identity
            players[i]!!.originSecretTask = task
        }
        val roleSkillsDataArray = if (Config.IsGmEnable) RoleCache.getRandomRolesWithSpecific(
            players.size * 2,
            Config.DebugRoles
        ) else RoleCache.getRandomRoles(players.size * 2)
        resolve(WaitForSelectRole(this, roleSkillsDataArray))
    }

    fun end(winners: List<Player?>?) {
        isEnd = true
        GameCache.remove(id)
        var isHumanGame = true
        for (p in players) {
            if (p is HumanPlayer) {
                p.saveRecord()
                deviceCache.remove(p.device)
            } else {
                isHumanGame = false
            }
        }
        if (winners != null && isHumanGame && players.size >= 5) {
            val records = ArrayList<Statistics.Record>(players.size)
            val playerGameResultList = ArrayList<PlayerGameResult>()
            for (p in players) {
                val win = winners.contains(p!!)
                records.add(Statistics.Record(p.role, win, p.originIdentity, p.originSecretTask, players.size))
                if (p is HumanPlayer) playerGameResultList.add(PlayerGameResult(p, win))
            }
            Statistics.add(records)
            Statistics.addPlayerGameCount(playerGameResultList)
        }
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
                val builder = discard_card_toc.newBuilder().setPlayerId(p.getAlternativeLocation(player.location))
                for (card in cards) {
                    builder.addCards(card.toPbCard())
                }
                p.send(builder.build())
            }
        }
    }

    fun playerSetRoleFaceUp(player: Player?, faceUp: Boolean) {
        if (faceUp) {
            log.error(if (player!!.roleFaceUp) "${player}本来就是正面朝上的" else "${player}将角色翻至正面朝上")
            player.roleFaceUp = true
        } else {
            log.error(if (player!!.roleFaceUp) "${player}本来就是背面朝上的" else "${player}将角色翻至背面朝上")
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
        GameExecutor.post(this) {
            val result = fsm!!.resolve()
            if (result != null) {
                fsm = result.next
                if (result.continueResolve) {
                    continueResolve()
                }
            }
        }
    }

    /**
     * 对于[WaitingFsm]，当收到玩家协议时，继续处理当前状态机
     */
    fun tryContinueResolveProtocol(player: Player, pb: GeneratedMessageV3) {
        GameExecutor.post(this) {
            if (fsm !is WaitingFsm) {
                log.error("时机错误，当前时点为：$fsm")
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
     * 增加一个新的需要监听的技能。仅用于接收情报时、使用卡牌时、死亡时的技能
     */
    fun addListeningSkill(skill: TriggeredSkill) {
        listeningSkills.add(skill)
    }

    /**
     * 遍历监听列表，结算技能
     */
    fun dealListeningSkill(): ResolveResult? {
        for (skill in listeningSkills) {
            val result = skill.execute(this)
            if (result != null) return result
        }
        return null
    }

    companion object {
        private val log = Logger.getLogger(Game::class.java)
        val GameCache: ConcurrentMap<Int, Game> = ConcurrentHashMap()
        val deviceCache: ConcurrentMap<String, HumanPlayer> = ConcurrentHashMap()
        private var increaseId = 0
        var newGame = newInstance()

        /**
         * 不是线程安全的
         */
        fun newInstance(): Game {
            return Game(max(newGame.players.size, Config.TotalPlayerCount))
        }

        @Throws(IOException::class, ClassNotFoundException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            Class.forName("com.fengsheng.skill.RoleCache")
            Statistics.load()
            Network.init()
        }
    }
}