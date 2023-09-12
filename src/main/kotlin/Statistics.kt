package com.fengsheng

import com.fengsheng.ScoreFactory.addScore
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.get_record_list_toc
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.log4j.Logger
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.fixedRateTimer
import kotlin.math.ceil
import kotlin.random.Random

object Statistics {
    private val pool = Channel<() -> Unit>(Channel.UNLIMITED)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").apply {
        timeZone = TimeZone.getTimeZone("GMT+8:00")
    }
    private val playerInfoMap = ConcurrentHashMap<String, PlayerInfo>()
    private val totalWinCount = AtomicInteger()
    private val totalGameCount = AtomicInteger()
    private val trialStartTime = ConcurrentHashMap<String, Long>()
    val rankList = AtomicReference<String>()

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            while (true) {
                val f = pool.receive()
                withContext(Dispatchers.IO) { f() }
            }
        }

        fixedRateTimer(daemon = true, initialDelay = 12 * 3600 * 1000, period = 24 * 3600 * 1000) {
            val file = File("playerInfo.csv")
            if (file.exists()) file.copyTo(File("playerInfo.csv.bak"), true)
        }
    }

    fun add(records: List<Record>) {
        ScoreFactory.addWinCount(records)
        pool.trySend {
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
                writeFile("stat.csv", sb.toString().toByteArray(), true)
            } catch (e: Exception) {
                log.error("execute task failed", e)
            }
        }
    }

    fun addPlayerGameCount(playerGameResultList: List<PlayerGameResult>) {
        pool.trySend {
            try {
                var win = 0
                var game = 0
                var updateTrial = false
                for (count in playerGameResultList) {
                    if (count.isWin) {
                        win++
                        if (trialStartTime.remove(count.playerName) != null) updateTrial = true
                    }
                    game++
                    playerInfoMap.computeIfPresent(count.playerName) { _, v ->
                        val addWin = if (count.isWin) 1 else 0
                        v.copy(winCount = v.winCount + addWin, gameCount = v.gameCount + 1)
                    }
                }
                totalWinCount.addAndGet(win)
                totalGameCount.addAndGet(game)
                savePlayerInfo()
                if (updateTrial) saveTrials()
            } catch (e: Exception) {
                log.error("execute task failed", e)
            }
        }
    }

    fun register(name: String): Boolean {
        val result = playerInfoMap.putIfAbsent(name, PlayerInfo(name, 0, "", 0, 0, 0, "")) == null
        if (result) pool.trySend(::savePlayerInfo)
        return result
    }

    fun login(name: String, pwd: String?): PlayerInfo {
        val password = try {
            if (pwd.isNullOrEmpty()) "" else md5(name + pwd)
        } catch (e: NoSuchAlgorithmException) {
            log.error("md5加密失败", e)
            throw Exception("内部错误，登录失败")
        }
        var changed = false
        val playerInfo = playerInfoMap.computeIfPresent(name) { _, v ->
            if (v.password.isEmpty() && password.isNotEmpty()) {
                changed = true
                v.copy(password = password)
            } else v
        } ?: throw Exception("用户名或密码错误，你可以在群里输入“注册”")
        if (changed) pool.trySend(::savePlayerInfo)
        if (password != playerInfo.password) throw Exception("用户名或密码错误，你可以在群里输入“注册”")
        val forbidLeft = playerInfo.forbidUntil - System.currentTimeMillis()
        if (forbidLeft > 0) throw Exception("你已被禁止登录，剩余${ceil(forbidLeft / 3600000.0).toInt()}小时")
        return playerInfo
    }

    fun forbidPlayer(name: String, hours: Int): Boolean {
        val forbidUntil = System.currentTimeMillis() + hours * 3600000L
        var changed = false
        playerInfoMap.computeIfPresent(name) { _, v ->
            changed = true
            v.copy(forbidUntil = forbidUntil)
        }
        if (changed) pool.trySend(::savePlayerInfo)
        return changed
    }

    fun releasePlayer(name: String): Boolean {
        var changed = false
        playerInfoMap.computeIfPresent(name) { _, v ->
            changed = true
            v.copy(forbidUntil = 0)
        }
        if (changed) pool.trySend(::savePlayerInfo)
        return changed
    }

    fun getPlayerInfo(name: String) = playerInfoMap[name]
    fun getScore(name: String) = playerInfoMap[name]?.score

    fun updateTitle(name: String, title: String): Boolean {
        var succeed = false
        playerInfoMap.computeIfPresent(name) { _, v ->
            if (v.score < 360) return@computeIfPresent v
            succeed = true
            v.copy(title = title)
        }
        return succeed
    }

    /**
     * @return Pair(score的新值, score的变化量)
     */
    fun updateScore(name: String, score: Int, save: Boolean): Pair<Int, Int> {
        var newScore = 0
        var delta = 0
        playerInfoMap.computeIfPresent(name) { _, v ->
            newScore = v.score addScore score
            delta = newScore - v.score
            v.copy(score = newScore)
        }
        if (save) pool.trySend(::savePlayerInfo)
        return newScore to delta
    }

    fun calculateRankList() {
        val l1 = playerInfoMap.filter { (_, v) -> v.score > 0 }.map { (_, v) -> v }.sortedWith { a, b ->
            if (a.score > b.score) -1
            else if (a.score < b.score) 1
            else a.name.compareTo(b.name)
        }
        val l = if (l1.size > 10) l1.subList(0, 10) else l1
        var i = 0
        val s = l.joinToString(separator = "\n") {
            val name = it.name.replace("\"", "\\\"")
            val rank = ScoreFactory.getRankNameByScore(it.score)
            "第${++i}名：${name}·${rank}·${it.score}"
        }
        rankList.set(s)
    }

    fun resetPassword(name: String): Boolean {
        if (playerInfoMap.computeIfPresent(name) { _, v -> v.copy(password = "") } != null) {
            pool.trySend(::savePlayerInfo)
            return true
        }
        return false
    }

    fun getPlayerGameCount(name: String): PlayerGameCount {
        val playerInfo = playerInfoMap[name] ?: return PlayerGameCount(0, 0)
        return PlayerGameCount(playerInfo.winCount, playerInfo.gameCount)
    }

    val totalPlayerGameCount: PlayerGameCount
        get() = PlayerGameCount(totalWinCount.get(), totalGameCount.get())

    private fun savePlayerInfo() {
        val sb = StringBuilder()
        for ((_, info) in playerInfoMap) {
            sb.append(info.winCount).append(',')
            sb.append(info.gameCount).append(',')
            sb.append(info.name).append(',')
            sb.append(info.score).append(',')
            sb.append(info.password).append(',')
            sb.append(info.forbidUntil).append(',')
            sb.append(info.title).append('\n')
        }
        writeFile("playerInfo.csv", sb.toString().toByteArray())
    }

    private fun saveTrials() {
        val sb = StringBuilder()
        for ((key, value) in trialStartTime) {
            sb.append(value).append(',')
            sb.append(key).append('\n')
        }
        writeFile("trial.csv", sb.toString().toByteArray())
    }

    @Throws(IOException::class)
    fun load() {
        var winCount = 0
        var gameCount = 0
        try {
            BufferedReader(InputStreamReader(FileInputStream("playerInfo.csv"))).use { reader ->
                var line: String?
                while (true) {
                    line = reader.readLine()
                    if (line == null) break
                    val a = line.split(",".toRegex(), limit = 6).toTypedArray()
                    val password = a[4]
                    val score = if (a[3].length < 6) a[3].toInt() else 0 // 以前这个位置是deviceId
                    val name = a[2]
                    val win = a[0].toInt()
                    val game = a[1].toInt()
                    val forbid = a.getOrNull(5)?.toLong() ?: 0
                    val title = a.getOrNull(6) ?: ""
                    if (playerInfoMap.put(name, PlayerInfo(name, score, password, win, game, forbid, title)) != null)
                        throw RuntimeException("数据错误，有重复的玩家name")
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
                var line: String?
                while (true) {
                    line = reader.readLine()
                    if (line == null) break
                    val a = line!!.split(",".toRegex(), limit = 2).toTypedArray()
                    trialStartTime[a[1]] = a[0].toLong()
                }
            }
        } catch (ignored: FileNotFoundException) {
        }
        calculateRankList()
    }

    fun getTrialStartTime(playerName: String): Long {
        return trialStartTime.getOrDefault(playerName, 0L)
    }

    fun setTrialStartTime(playerName: String, time: Long) {
        pool.trySend {
            try {
                trialStartTime[playerName] = time
                saveTrials()
            } catch (e: Exception) {
                log.error("execute task failed", e)
            }
        }
    }

    fun displayRecordList(player: HumanPlayer) {
        pool.trySend {
            val builder = get_record_list_toc.newBuilder()
            val dir = File("records")
            val files = dir.list()
            if (files != null) {
                files.sort()
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

    class Record(
        val role: role,
        val isWinner: Boolean,
        val identity: color,
        val task: secret_task,
        val totalPlayerCount: Int
    )

    class PlayerGameResult(val playerName: String, val isWin: Boolean)

    data class PlayerGameCount(val winCount: Int, val gameCount: Int) {
        fun random(): PlayerGameCount {
            val i = Random.nextInt(20)
            return PlayerGameCount(winCount * i / 100, gameCount * i / 100)
        }

        fun inc(isWinner: Boolean) = PlayerGameCount(winCount + if (isWinner) 1 else 0, gameCount + 1)

        val rate get() = if (gameCount == 0) 0.0 else winCount * 100.0 / gameCount
    }

    data class PlayerInfo(
        val name: String,
        val score: Int,
        val password: String,
        val winCount: Int,
        val gameCount: Int,
        val forbidUntil: Long,
        val title: String,
    )

    private val log = Logger.getLogger(Statistics::class.java)

    private fun writeFile(fileName: String, buf: ByteArray, append: Boolean = false) {
        try {
            FileOutputStream(fileName, append).use { fileOutputStream -> fileOutputStream.write(buf) }
        } catch (e: IOException) {
            log.error("write file failed", e)
        }
    }

    private val hexDigests =
        charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

    @Throws(NoSuchAlgorithmException::class)
    private fun md5(s: String): String {
        try {
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
                str[k++] = hexDigests[b.toInt() ushr 4 and 0xf] // 高4位
                str[k++] = hexDigests[b.toInt() and 0xf] // 低4位
            }
            return String(str)
        } catch (e: NoSuchAlgorithmException) {
            log.warn("calculate md5 failed: ", e)
            return s
        }
    }
}