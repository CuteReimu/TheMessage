package com.fengsheng

import com.fengsheng.phase.StartGame
import com.fengsheng.phase.WaitForSelectRole
import com.fengsheng.protos.*
import com.fengsheng.protos.Record.record_file
import com.fengsheng.protos.Record.recorder_line
import com.fengsheng.skill.RoleCache
import com.google.protobuf.ByteString
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.logging.log4j.kotlin.logger
import java.io.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer

class Recorder {
    private var list: MutableList<recorder_line> = ArrayList()
    private var currentIndex = 0
    private var skipCount = 0

    @Volatile
    var loading = false
        private set

    @Volatile
    private var pausing = false
    fun add(protoName: String, messageBuf: ByteArray?) {
        if (protoName in ignoredProtoNames) return
        if (!loading && ("wait_for_select_role_toc" == protoName || "init_toc" == protoName || list.isNotEmpty())) {
            list.add(recorderLine {
                nanoTime = System.nanoTime()
                this.protoName = protoName
                this.messageBuf = ByteString.copyFrom(messageBuf)
            })
        }
    }

    fun save(g: Game, p: HumanPlayer, notify: Boolean) {
        if (list.size < 10 || g.fsm is StartGame || g.fsm is WaitForSelectRole) return
        val now = Date()
        val localDateTime = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
        val timeStr = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
        val sb = StringBuilder()
        for (player in g.players) {
            if (sb.isNotEmpty()) sb.append("-")
            sb.append(RoleCache.getRoleName(player!!.originRole))
        }
        val recordId = ((now.time / 1000 * 1000 + g.id % 100 * 10 + p.location) %
            (36L * 36 * 36 * 36 * 36 * 36)).toString(Character.MAX_RADIX).addLeadingZero(6)
        val fileName = timeStr + "-" + sb + "-" + p.location + "-" + recordId
        val recordFile = recordFile {
            clientVersion = Config.ClientVersion.get()
            lines.addAll(list)
        }
        saveLoadPool.trySend {
            val file = File("records/")
            if (!file.exists() && !file.isDirectory && !file.mkdir()) logger.error("make dir failed: ${file.name}")
            try {
                DataOutputStream(FileOutputStream("records/$fileName")).use { os ->
                    os.write(recordFile.toByteArray())
                    if (notify) p.send(saveRecordSuccessToc { this.recordId = recordId })
                    logger.info("save record success: $recordId")
                }
            } catch (e: IOException) {
                logger.error("save record failed", e)
            }
        }
    }

    fun load(version: Int, recordId: String, skipCount: Int, player: HumanPlayer) {
        val file = File("records/")
        if (!file.exists() || !file.isDirectory) {
            player.sendErrorMessage("录像不存在")
            return
        }
        val files = if (recordId.length == 6) file.listFiles { _, name -> name.endsWith("-$recordId") } else null
        if (files.isNullOrEmpty()) {
            player.sendErrorMessage("录像不存在")
            return
        }
        val recordFile = files[0]
        this.skipCount = skipCount
        loading = true
        saveLoadPool.trySend {
            try {
                DataInputStream(FileInputStream(recordFile)).use { `is` ->
                    val pb = record_file.parseFrom(`is`.readAllBytes())
                    val recordVersion = pb.clientVersion
                    if (version < recordVersion)
                        logger.warn("客户端版本号过低，录像可能有问题。录像$recordVersion，当前$version")
                    list = pb.linesList
                    currentIndex = 0
                    logger.info("load record success: $recordId")
                    if (player.needWaitLoad)
                        player.send(gameStartToc {})
                    else
                        displayNext(player)
                }
            } catch (e: IOException) {
                logger.error("load record failed", e)
                player.sendErrorMessage("播放录像失败")
                loading = false
            }
        }
    }

    fun pause(pause: Boolean) {
        pausing = pause
    }

    fun displayNext(player: HumanPlayer) {
        if (skipCount > 0) {
            player.send(reconnectToc { isEnd = false })
            while (currentIndex <= skipCount && currentIndex < list.size) {
                val line = list[currentIndex]
                if ("wait_for_select_role_toc" != line.protoName && "select_role_toc" != line.protoName)
                    player.send(line.protoName, line.messageBuf.toByteArray(), true)
                currentIndex++
            }
            player.send(reconnectToc { isEnd = true })
        }
        while (true) {
            if (!player.isActive) {
                loading = false
                return
            }
            if (pausing) {
                GameExecutor.TimeWheel.newTimeout({ displayNext(player) }, 2, TimeUnit.SECONDS)
                return
            }
            if (currentIndex >= list.size) {
                player.send(displayRecordEndToc { })
                list = ArrayList()
                loading = false
                break
            }
            val line = list[currentIndex]
            if ("wait_for_select_role_toc" == line.protoName || "select_role_toc" == line.protoName) {
                currentIndex++
                continue
            }
            player.send(line.protoName, line.messageBuf.toByteArray(), true)
            if (++currentIndex >= list.size) {
                player.send(displayRecordEndToc { })
                list = ArrayList()
                loading = false
                break
            }
            val diffNanoTime = list[currentIndex].nanoTime - line.nanoTime
            if (diffNanoTime > 100000000) {
                var maxInterval = 1000000000L * Config.RecordMaxInterval
                if (list[currentIndex].protoName in fastDisplayProtoNames && line.protoName in fastDisplayProtoNames)
                    maxInterval /= 2
                GameExecutor.TimeWheel.newTimeout(
                    { displayNext(player) },
                    diffNanoTime.coerceAtMost(maxInterval),
                    TimeUnit.NANOSECONDS
                )
                break
            }
        }
    }

    fun reconnect(player: HumanPlayer) {
        player.send(reconnectToc { isEnd = false })
        for (i in 0..(list.size - 2)) {
            val line = list[i]
            player.send(line.protoName, line.messageBuf.toByteArray(), false)
        }
        player.send(reconnectToc { isEnd = true })
        list.lastOrNull()?.let { line ->
            player.send(line.protoName, line.messageBuf.toByteArray(), true)
        }
    }

    companion object {
        private val saveLoadPool = Channel<() -> Unit>(Channel.UNLIMITED)
        private val ignoredProtoNames = listOf(
            "reconnect_toc",
            "heart_toc",
            "game_init_finish_tos",
            "notify_kicked_toc",
            "error_message_toc",
            "notify_player_update_toc"
        )
        private val fastDisplayProtoNames = listOf("notify_phase_toc", "wait_for_cheng_qing_toc")

        init {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch {
                while (true) {
                    val f = saveLoadPool.receive()
                    withContext(Dispatchers.IO) { f() }
                }
            }

            fixedRateTimer(daemon = true, period = 600000) {
                val now = Date()
                now.time -= 14 * 24 * 3600 * 1000
                val localDateTime = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                val timeStr = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
                val file = File("records/")
                if (!file.exists() || !file.isDirectory) {
                    return@fixedRateTimer
                }
                val l = "yyyy-MM-dd-HH-mm-ss".length
                file.listFiles()?.forEach {
                    val fileName = it.name
                    if (fileName.length > l && fileName.substring(0, l) <= timeStr)
                        it.delete()
                }
            }
        }
    }

    private fun String.addLeadingZero(totalLen: Int): String {
        val zeroCount = totalLen - length
        if (zeroCount <= 0) return this
        val sb = StringBuilder()
        repeat(zeroCount) { sb.append('0') }
        sb.append(this)
        return sb.toString()
    }
}
