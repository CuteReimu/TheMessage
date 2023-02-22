package com.fengsheng

import com.fengsheng.*
import com.fengsheng.phase.WaitForSelectRole
import com.fengsheng.protos.Common.card
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.role
import com.fengsheng.protos.Common.secret_task
import com.fengsheng.protos.Fengsheng.get_record_list_toc
import com.fengsheng.protos.Fengsheng.pb_order
import com.fengsheng.protos.Record.player_order
import com.fengsheng.protos.Record.player_orders
import com.fengsheng.skill.RoleCache
import org.apache.log4j.Logger
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Predicate

com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.card
import java.lang.StringBuilder
import java.lang.RuntimeException
import com.fengsheng.protos.Common.role

class Statistics private constructor() {
    private val pool = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private val playerGameCount: MutableMap<String, PlayerGameCount> = ConcurrentHashMap()
    private val playerInfoMap: MutableMap<String, PlayerInfo?> = ConcurrentHashMap()
    private val totalWinCount = AtomicInteger()
    private val totalGameCount = AtomicInteger()
    private val trialStartTime: MutableMap<String, Long> = ConcurrentHashMap()
    private val orderMap: MutableMap<Int, player_order> = HashMap()
    private val deviceOrderMap: MutableMap<String, MutableList<player_order>> = ConcurrentHashMap()
    private var orderId = 0
    fun add(records: List<Statistics.Record>) {
        pool.submit {
            try {
                val time = dateFormat.format(Date())
                val sb = StringBuilder()
                for (r in records) {
                    sb.append(r.role).append(',')
                    sb.append(r.isWinner).append(',')
                    sb.append(r.identity).append(',')
                    sb.append(if (r.identity == color.Black) r.task.toString() else "").append(',')
                    sb.append(r.totalPlayerCount).append(',')
                    sb.append(time).append('\n')
                }
                Statistics.Companion.writeFile("stat.csv", sb.toString().toByteArray(), true)
            } catch (e: Exception) {
                Statistics.Companion.log.error("execute task failed", e)
            }
        }
    }

    fun addPlayerGameCount(playerGameResultList: List<PlayerGameResult>) {
        pool.submit {
            try {
                var win = 0
                var game = 0
                var updateTrial = false
                for (count in playerGameResultList) {
                    if (count.isWin) {
                        win++
                        if (trialStartTime.remove(count.player.device) != null) updateTrial = true
                    }
                    game++
                    playerInfoMap.computeIfPresent(count.player.playerName) { k: String?, v: PlayerInfo? ->
                        val addWin = if (count.isWin) 1 else 0
                        PlayerInfo(v!!.name, v.deviceId, v.password, v.winCount + addWin, v.gameCount + 1)
                    }
                }
                totalWinCount.addAndGet(win)
                totalGameCount.addAndGet(game)
                var sb = StringBuilder()
                for ((_, info) in playerInfoMap) {
                    sb.append(info!!.winCount).append(',')
                    sb.append(info.gameCount).append(',')
                    sb.append(info.name).append(',')
                    sb.append(info.deviceId).append(',')
                    sb.append(info.password).append('\n')
                }
                writeFile("playerInfo.csv", sb.toString().toByteArray())
                if (updateTrial) {
                    sb = StringBuilder()
                    for ((key, value) in trialStartTime) {
                        sb.append(value).append(',')
                        sb.append(key).append('\n')
                    }
                    writeFile("trial.csv", sb.toString().toByteArray())
                }
            } catch (e: Exception) {
                Statistics.Companion.log.error("execute task failed", e)
            }
        }
    }

    fun login(name: String, deviceId: String, pwd: String?): PlayerInfo? {
        val password: String = try {
            if (pwd.isNullOrEmpty()) "" else md5(name + pwd)
        } catch (e: NoSuchAlgorithmException) {
            log.error("md5加密失败", e)
            return null
        }
        var playerInfo = playerInfoMap[name]
        if (playerInfo == null) {
            playerInfo = PlayerInfo(name, deviceId, password, 0, 0)
            val playerInfo2 = playerInfoMap.putIfAbsent(name, playerInfo)
            if (playerInfo2 != null) playerInfo = playerInfo2
        }
        if (playerInfo.password == null || playerInfo.password!!.isEmpty()) playerInfo =
            PlayerInfo(playerInfo.name, playerInfo.deviceId, password, playerInfo.winCount, playerInfo.gameCount)
        if (password != playerInfo.password) return null
        // 对旧数据进行兼容
        val finalPlayerInfo = arrayOf(playerInfo)
        playerGameCount.computeIfPresent(deviceId) { k: String?, v: PlayerGameCount ->
            val gameCount = finalPlayerInfo[0].gameCount + v.gameCount
            val winCount = finalPlayerInfo[0].winCount + v.winCount
            finalPlayerInfo[0] = PlayerInfo(name, deviceId, password, gameCount, winCount)
            null
        }
        if (finalPlayerInfo[0] !== playerInfo) {
            playerInfoMap[name] = finalPlayerInfo[0]
            pool.submit {
                var sb = StringBuilder()
                for ((_, info) in playerInfoMap) {
                    sb.append(info!!.winCount).append(',')
                    sb.append(info.gameCount).append(',')
                    sb.append(info.name).append(',')
                    sb.append(info.deviceId).append(',')
                    sb.append(info.password).append('\n')
                }
                writeFile("playerInfo.csv", sb.toString().toByteArray())
                sb = StringBuilder()
                for ((key, count) in playerGameCount) {
                    sb.append(count.winCount).append(',')
                    sb.append(count.gameCount).append(',')
                    sb.append(key).append('\n')
                }
                writeFile("player.csv", sb.toString().toByteArray())
            }
        }
        return playerInfo
    }

    fun getPlayerGameCount(name: String): PlayerGameCount? {
        val playerInfo = playerInfoMap[name] ?: return null
        return PlayerGameCount(playerInfo.winCount, playerInfo.gameCount)
    }

    val totalPlayerGameCount: PlayerGameCount
        get() = PlayerGameCount(totalWinCount.get(), totalGameCount.get())

    @Throws(IOException::class)
    fun load() {
        try {
            BufferedReader(InputStreamReader(FileInputStream("player.csv"))).use { reader ->
                var line: String
                while (reader.readLine().also { line = it } != null) {
                    val a = line.split(",".toRegex(), limit = 3).toTypedArray()
                    val deviceId = a[2]
                    val win = a[0].toInt()
                    val game = a[1].toInt()
                    playerGameCount[deviceId] = PlayerGameCount(win, game)
                }
            }
        } catch (ignored: FileNotFoundException) {
        }
        var winCount = 0
        var gameCount = 0
        try {
            BufferedReader(InputStreamReader(FileInputStream("playerInfo.csv"))).use { reader ->
                var line: String
                while (reader.readLine().also { line = it } != null) {
                    val a = line.split(",".toRegex(), limit = 5).toTypedArray()
                    val password = a[4]
                    val deviceId = a[3]
                    val name = a[2]
                    val win = a[0].toInt()
                    val game = a[1].toInt()
                    if (playerInfoMap.put(
                            name,
                            PlayerInfo(name, deviceId, password, win, game)
                        ) != null
                    ) throw RuntimeException("数据错误，有重复的玩家name")
                    winCount += win
                    gameCount += game
                }
            }
        } catch (ignored: FileNotFoundException) {
        }
        totalWinCount.set(winCount)
        totalGameCount.set(gameCount)
        try {
            BufferedReader(InputStreamReader(FileInputStream("trial.csv"))).use { reader ->
                var line: String
                while (reader.readLine().also { line = it } != null) {
                    val a = line.split(",".toRegex(), limit = 2).toTypedArray()
                    trialStartTime[a[1]] = a[0].toLong()
                }
            }
        } catch (ignored: FileNotFoundException) {
        }
        try {
            FileInputStream("order.dat").use { `is` ->
                val playerOrders = player_orders.parseFrom(`is`.readAllBytes())
                orderMap.putAll(playerOrders.ordersMap)
                orderId = playerOrders.orderId
                for (order in playerOrders.ordersMap.values) deviceOrderMap.computeIfAbsent(order.device) { k: String? -> ArrayList() }
                    .add(order)
            }
        } catch (ignored: FileNotFoundException) {
        }
    }

    fun getTrialStartTime(deviceId: String): Long {
        return trialStartTime.getOrDefault(deviceId, 0L)
    }

    fun setTrialStartTime(device: String, time: Long) {
        pool.submit {
            try {
                trialStartTime[device] = time
                val sb = StringBuilder()
                for ((key, value) in trialStartTime) {
                    sb.append(value).append(',')
                    sb.append(key).append('\n')
                }
                writeFile("trial.csv", sb.toString().toByteArray())
            } catch (e: Exception) {
                Statistics.Companion.log.error("execute task failed", e)
            }
        }
    }

    fun getOrders(deviceId: String): List<pb_order> {
        val now = System.currentTimeMillis() / 1000 + 8 * 3600
        val set: MutableSet<player_order> = TreeSet(Comparator { o1: player_order, o2: player_order ->
            if (o1.time == o2.time) return@Comparator Integer.compare(o1.id, o2.id)
            if (o1.time < o2.time) -1 else 1
        })
        val myOrders: List<player_order>? = deviceOrderMap[deviceId]
        myOrders?.forEach(Consumer { o: player_order -> if (o.time > now - 1800) set.add(o) })
        orderMap.forEach { (k: Int?, o: player_order) -> if (o.time > now - 1800) set.add(o) }
        val list: MutableList<pb_order> = ArrayList()
        for (o in set) {
            list.add(Statistics.Companion.playerOrderToPbOrder(deviceId, o))
            if (list.size >= 20) break
        }
        return list
    }

    fun addOrder(device: String, name: String?, time: Long) {
        val now = System.currentTimeMillis() / 1000 + 8 * 3600
        if (time <= now - 1800) return
        pool.submit {
            try {
                var orders1 = deviceOrderMap[device]
                orders1 = if (orders1 == null) ArrayList() else ArrayList(orders1)
                val order =
                    player_order.newBuilder().setId(++orderId).setDevice(device).setName(name).setTime(time).build()
                orders1.add(order)
                orders1.removeIf(Predicate { o: player_order ->
                    if (o.time <= now - 1800) {
                        orderMap.remove(o.id)
                         true
                    } else false
                })
                if (orders1.size > 3) orders1.subList(0, orders1.size - 3).clear()
                deviceOrderMap[device] = orders1
                orderMap[order.id] = order
                val removeList: MutableList<Int> = ArrayList()
                for ((key, o) in orderMap) {
                    if (o.time <= now - 1800) {
                        removeList.add(key)
                        val orders2: MutableList<player_order> = ArrayList()
                        for (o2 in deviceOrderMap[o.device]!!) {
                            if (o2.id != o.id) orders2.add(o2)
                        }
                        deviceOrderMap[o.device] = orders2
                    }
                }
                removeList.forEach(Consumer { key: Int -> orderMap.remove(key) })
                val buf = player_orders.newBuilder().setOrderId(orderId).putAllOrders(orderMap).build().toByteArray()
                writeFile("order.dat", buf)
            } catch (e: Exception) {
                Statistics.Companion.log.error("execute task failed", e)
            }
        }
    }

    fun displayRecordList(player: HumanPlayer) {
        pool.submit {
            val builder = get_record_list_toc.newBuilder()
            val dir = File("records")
            val files = dir.list()
            if (files != null) {
                Arrays.sort(files)
                var lastPrefix: String? = null
                var j = 0
                for (i in files.indices.reversed()) {
                    if (files[i].length < 19) continue
                    if (lastPrefix == null || !files[i].startsWith(lastPrefix)) {
                        if (++j > Config.RecordListSize) break
                        lastPrefix = files[i].substring(0, 19)
                    }
                    builder.addRecords(files[i])
                }
            }
            player.send(builder.build())
        }
    }

    class Record(role: role, isWinner: Boolean, identity: color, task: secret_task, totalPlayerCount: Int) {
        val role: role
        val isWinner: Boolean
        val identity: color
        val task: secret_task
        val totalPlayerCount: Int

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
            this.role = role
            this.isWinner = isWinner
            this.identity = identity
            this.task = task
            this.totalPlayerCount = totalPlayerCount
        }
    }

    class PlayerGameResult(player: HumanPlayer, isWin: Boolean) {
        val player: HumanPlayer
        val isWin: Boolean

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
            this.role = role
            this.isWinner = isWinner
            this.identity = identity
            this.task = task
            this.totalPlayerCount = totalPlayerCount
            this.player = player
            this.isWin = isWin
        }
    }

    class PlayerGameCount(winCount: Int, gameCount: Int) {
        val winCount: Int
        val gameCount: Int

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
            this.role = role
            this.isWinner = isWinner
            this.identity = identity
            this.task = task
            this.totalPlayerCount = totalPlayerCount
            this.player = player
            this.isWin = isWin
            this.winCount = winCount
            this.gameCount = gameCount
        }
    }

    class PlayerInfo(name: String, deviceId: String, password: String?, winCount: Int, gameCount: Int) {
        val name: String
        val deviceId: String
        val password: String?
        val winCount: Int
        val gameCount: Int

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
            this.role = role
            this.isWinner = isWinner
            this.identity = identity
            this.task = task
            this.totalPlayerCount = totalPlayerCount
            this.player = player
            this.isWin = isWin
            this.winCount = winCount
            this.gameCount = gameCount
            this.name = name
            this.deviceId = deviceId
            this.password = password
            this.winCount = winCount
            this.gameCount = gameCount
        }
    }

    init {
        dateFormat.timeZone = TimeZone.getTimeZone("GMT+8:00")
    }

    companion object {
        val instance: Statistics
            get() = Statistics.Companion.instance
        private val log = Logger.getLogger(Statistics::class.java)
        private fun playerOrderToPbOrder(deviceId: String, order: player_order): pb_order {
            return pb_order.newBuilder().setId(order.id).setName(order.name).setTime(order.time).setIsMine(
                deviceId == order.device
            ).build()
        }

        private fun writeFile(fileName: String, buf: ByteArray, append: Boolean = false) {
            try {
                FileOutputStream(fileName, append).use { fileOutputStream -> fileOutputStream.write(buf) }
            } catch (e: IOException) {
                Statistics.Companion.log.error("write file failed", e)
            }
        }

        @Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val appearCount = EnumMap<role, Int>(role::class.java)
            val blackAppearCount = EnumMap<role, Int>(role::class.java)
            val winCount = EnumMap<role, Int>(role::class.java)
            val blackWinCount = EnumMap<role, Int>(role::class.java)
            val timeSet: MutableSet<String> = HashSet()
            BufferedReader(InputStreamReader(FileInputStream("stat.csv"))).use { reader ->
                var line: String
                while (reader.readLine().also { line = it } != null) {
                    val a = line.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val role = role.valueOf(a[0])
                    appearCount.compute(role) { k: role?, v: Int? -> if (v == null) 1 else v + 1 }
                    if (java.lang.Boolean.parseBoolean(a[1])) winCount.compute(role) { k: role?, v: Int? -> if (v == null) 1 else v + 1 }
                    if ("Black" == a[2]) {
                        blackAppearCount.compute(role) { k: role?, v: Int? -> if (v == null) 1 else v + 1 }
                        if (java.lang.Boolean.parseBoolean(a[1])) blackWinCount.compute(role) { k: role?, v: Int? -> if (v == null) 1 else v + 1 }
                    }
                    timeSet.add(a[5])
                }
            }
            if (timeSet.isEmpty()) return
            BufferedWriter(OutputStreamWriter(FileOutputStream("stat0.csv"))).use { writer ->
                writer.write("角色,场次,胜率,军潜胜率,神秘人胜率")
                writer.newLine()
                for ((key, value) in appearCount) {
                    var roleName = RoleCache.getRoleName(key)
                    roleName = roleName ?: WaitForSelectRole.Companion.getRoleName(key)
                    writer.write(Objects.requireNonNullElse(roleName, ""))
                    writer.write(','.code)
                    writer.write(Integer.toString(value))
                    writer.write(','.code)
                    writer.write("%.2f%%".formatted(winCount.getOrDefault(key, 0) * 100.0 / value))
                    writer.write(','.code)
                    val blackAppear = blackAppearCount.getOrDefault(key, 0)
                    val nonBlackAppear = value - blackAppear
                    if (nonBlackAppear > 0) writer.write(
                        "%.2f%%".formatted(
                            (winCount.getOrDefault(
                                key,
                                0
                            ) - blackWinCount.getOrDefault(key, 0)) * 100.0 / nonBlackAppear
                        )
                    ) else writer.write("0.00%")
                    writer.write(','.code)
                    if (blackAppear > 0) writer.write(
                        "%.2f%%".formatted(
                            blackWinCount.getOrDefault(
                                key,
                                0
                            ) * 100.0 / blackAppearCount[key]!!
                        )
                    ) else writer.write("0.00%")
                    writer.newLine()
                }
            }
        }

        private val hexDigests =
            charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

        @Throws(NoSuchAlgorithmException::class)
        private fun md5(s: String): String {
            val `in` = s.toByteArray(StandardCharsets.UTF_8)
            val messageDigest = MessageDigest.getInstance("md5")
            messageDigest.update(`in`)
            // 获得密文
            val md = messageDigest.digest()
            // 将密文转换成16进制字符串形式
            val j = md.size
            val str = CharArray(j * 2)
            var k = 0
            for (b in md) {
                str[k++] = Statistics.Companion.hexDigests.get(b.toInt() ushr 4 and 0xf) // 高4位
                str[k++] = Statistics.Companion.hexDigests.get(b.toInt() and 0xf) // 低4位
            }
            return kotlin.String(str)
        }
    }
}