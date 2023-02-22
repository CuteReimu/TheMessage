package com.fengsheng

import com.fengsheng.*
import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.Statistics.PlayerGameResult
import com.fengsheng.card.*
import com.fengsheng.network.*
import com.fengsheng.phase.WaitForSelectRole
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.skill.RoleCache
import com.fengsheng.skill.TriggeredSkill
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.io.IOException
import java.util.*
import java.util.concurrent.*

class Game private constructor(totalPlayerCount: Int) {

    val id: Int

    @Volatile
    var isStarted = false

    @Volatile
    var isEnd = false
        private set
    var players: Array<Player?>
    var deck: Deck? = null
    var fsm: Fsm? = null
        private set
    private val listeningSkills: MutableList<TriggeredSkill> = ArrayList()

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
    val qiangLingTypes: Set<card_type> = EnumSet.noneOf<card_type>(card_type::class.java)

    init {
        // 调用构造函数时加锁了，所以increaseId无需加锁
        id = ++Game.Companion.increaseId
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
                player.setLocation(index)
            }
        }
        val builder = join_room_toc.newBuilder().setName(player.playerName).setPosition(player.location())
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
            GameExecutor.post(this, Runnable { start() })
            newInstance()
        } else {
            log.info(player.playerName + "加入了。已加入" + (players.size - unready) + "个人，等待" + unready + "人加入。。。")
        }
    }

    fun start() {
        val random: Random = ThreadLocalRandom.current()
        var identities: MutableList<color?> = ArrayList()
        when (players.size) {
            2 -> identities = when (random.nextInt(4)) {
                0 -> listOf(color.Red, color.Blue)
                1 -> listOf(color.Red, color.Black)
                2 -> listOf(color.Blue, color.Black)
                else -> listOf(color.Black, color.Black)
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
        Collections.shuffle(identities, random)
        val tasks = Arrays.asList(
            secret_task.Killer,
            secret_task.Stealer,
            secret_task.Collector,
            secret_task.Mutator,
            secret_task.Pioneer
        )
        Collections.shuffle(tasks, random)
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
        Game.Companion.GameCache.remove(id)
        var isHumanGame = true
        for (p in players) {
            if (p is HumanPlayer) {
                p.saveRecord()
                Game.Companion.deviceCache.remove(p.device)
            } else {
                isHumanGame = false
            }
        }
        if (winners != null && isHumanGame && players.size >= 5) {
            val records: MutableList<Statistics.Record> = ArrayList(players.size)
            val playerGameResultList: MutableList<PlayerGameResult> = ArrayList()
            for (p in players) {
                val win = winners.contains(p)
                records.add(Statistics.Record(p!!.role, win, p.originIdentity, p.originSecretTask, players.size))
                if (p is HumanPlayer) playerGameResultList.add(PlayerGameResult(p, win))
            }
            Statistics.Companion.getInstance().add(records)
            Statistics.Companion.getInstance().addPlayerGameCount(playerGameResultList)
        }
    }

    /**
     * 玩家弃牌
     */
    fun playerDiscardCard(player: Player, vararg cards: Card) {
        if (cards.size == 0) return
        for (card in cards) {
            player.deleteCard(card.id)
        }
        Game.Companion.log.info(player.toString() + "弃掉了" + Arrays.toString(cards) + "，剩余手牌" + player.cards.size + "张")
        deck!!.discard(*cards)
        for (p in players) {
            if (p is HumanPlayer) {
                val builder = discard_card_toc.newBuilder().setPlayerId(p.getAlternativeLocation(player.location()))
                for (card in cards) {
                    builder.addCards(card.toPbCard())
                }
                p.send(builder.build())
            }
        }
    }

    fun playerSetRoleFaceUp(player: Player?, faceUp: Boolean) {
        if (faceUp) {
            if (player!!.isRoleFaceUp) Game.Companion.log.error(
                player.toString() + "本来就是正面朝上的",
                RuntimeException()
            ) else Game.Companion.log.info(player.toString() + "将角色翻至正面朝上")
            player.isRoleFaceUp = true
        } else {
            if (!player!!.isRoleFaceUp) Game.Companion.log.error(
                player.toString() + "本来就是背面朝上的",
                RuntimeException()
            ) else Game.Companion.log.info(player.toString() + "将角色翻至背面朝上")
            player.isRoleFaceUp = false
        }
        for (p in players) {
            if (p is HumanPlayer) {
                val builder = notify_role_update_toc.newBuilder().setPlayerId(
                    p.getAlternativeLocation(
                        player.location()
                    )
                )
                builder.role = if (player.isRoleFaceUp) player.role else role.unknown
                p.send(builder.build())
            }
        }
    }

    fun allPlayerSetRoleFaceUp() {
        for (p in players) {
            if (!p!!.isRoleFaceUp) playerSetRoleFaceUp(p, true)
        }
    }

    /**
     * 继续处理当前状态机
     */
    fun continueResolve() {
        GameExecutor.Companion.post(this, Runnable {
            val result = fsm!!.resolve()
            if (result != null) {
                fsm = result.next
                if (result.continueResolve) {
                    continueResolve()
                }
            }
        })
    }

    /**
     * 对于[WaitingFsm]，当收到玩家协议时，继续处理当前状态机
     */
    fun tryContinueResolveProtocol(player: Player?, pb: GeneratedMessageV3?) {
        GameExecutor.Companion.post(this, Runnable {
            if (fsm !is WaitingFsm) {
                Game.Companion.log.error("时机错误，当前时点为：$fsm")
                return@post
            }
            val result = (fsm as WaitingFsm).resolveProtocol(player, pb)
            if (result != null) {
                fsm = result.next
                if (result.continueResolve) {
                    continueResolve()
                }
            }
        })
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
     * 获取监听技能的列表
     */
    fun getListeningSkills(): List<TriggeredSkill> {
        return listeningSkills
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
        private const val increaseId = 0
        private val newGame: Game? = null

        /**
         * 不是线程安全的
         */
        fun newInstance() {
            Game.Companion.newGame = Game(
                Math.max(
                    if (Game.Companion.newGame != null) Game.Companion.newGame.getPlayers().size else 0,
                    Config.TotalPlayerCount
                )
            )
        }

        val instance: Game
            /**
             * 不是线程安全的
             */
            get() = Game.Companion.newGame

        @Throws(IOException::class, ClassNotFoundException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            Class.forName("com.fengsheng.skill.RoleCache")
            Statistics.Companion.getInstance().load()
            synchronized(Game::class.java) { Game.Companion.newInstance() }
            Network.init()
        }
    }
}