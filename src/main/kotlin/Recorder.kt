package com.fengsheng

import com.fengsheng.phase.StartGame
import com.fengsheng.phase.WaitForSelectRole
import com.fengsheng.protos.Errcode.error_code.*
import com.fengsheng.protos.Errcode.error_code_toc
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.protos.Record.record_file
import com.fengsheng.protos.Record.recorder_line
import com.google.protobuf.ByteString
import org.apache.log4j.Logger
import java.io.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Recorder {
    private var list: MutableList<recorder_line> = ArrayList()
    private var currentIndex = 0

    @Volatile
    private var loading = false

    @Volatile
    private var pausing = false
    fun add(protoName: String, messageBuf: ByteArray?) {
        if ("reconnect_toc" == protoName) return
        if (!loading && ("wait_for_select_role_toc" == protoName || list.isNotEmpty())) list.add(
            recorder_line.newBuilder().setNanoTime(System.nanoTime()).setProtoName(protoName)
                .setMessageBuf(ByteString.copyFrom(messageBuf)).build()
        )
    }

    fun save(g: Game, p: HumanPlayer, notify: Boolean) {
        if (list.isEmpty() || g.fsm is StartGame || g.fsm is WaitForSelectRole) return
        val now = Date()
        val localDateTime = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
        val timeStr = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
        val sb = StringBuilder()
        for (player in g.players) {
            if (sb.isNotEmpty()) sb.append("-")
            sb.append(player!!.roleName)
        }
        val recordId = ((now.time / 1000 * 1000 + g.id % 100 * 10 + p.location) %
                (36L * 36 * 36 * 36 * 36 * 36)).toString(Character.MAX_RADIX).addLeadingZero(6)
        val fileName = timeStr + "-" + sb + "-" + p.location + "-" + recordId
        val builder = record_file.newBuilder()
        builder.clientVersion = Config.ClientVersion
        builder.addAllLines(list)
        val recordFile = builder.build()
        saveLoadPool.submit {
            val file = File("records/")
            if (!file.exists() && !file.isDirectory && !file.mkdir()) log.error("make dir failed: ${file.name}")
            try {
                DataOutputStream(FileOutputStream("records/$fileName")).use { os ->
                    os.write(recordFile.toByteArray())
                    if (notify) p.send(save_record_success_toc.newBuilder().setRecordId(recordId).build())
                    log.info("save record success: $recordId")
                }
            } catch (e: IOException) {
                log.error("save record failed", e)
            }
        }
    }

    fun load(version: Int, recordId: String, player: HumanPlayer) {
        val file = File("records/")
        if (!file.exists() || !file.isDirectory) {
            player.send(
                error_code_toc.newBuilder()
                    .setCode(record_not_exists).build()
            )
            return
        }
        val files = if (recordId.length == 6) file.listFiles { _, name -> name.endsWith("-$recordId") } else null
        if (files.isNullOrEmpty()) {
            player.send(
                error_code_toc.newBuilder()
                    .setCode(record_not_exists).build()
            )
            return
        }
        val recordFile = files[0]
        loading = true
        saveLoadPool.submit {
            try {
                DataInputStream(FileInputStream(recordFile)).use { `is` ->
                    val pb = record_file.parseFrom(`is`.readAllBytes())
                    val recordVersion = pb.clientVersion
                    if (version < recordVersion) {
                        player.send(
                            error_code_toc.newBuilder()
                                .setCode(record_version_not_match)
                                .addIntParams(recordVersion.toLong()).build()
                        )
                        loading = false
                        return@submit
                    }
                    list = pb.linesList
                    currentIndex = 0
                    log.info("load record success: $recordId")
                    displayNext(player)
                }
            } catch (e: IOException) {
                log.error("load record failed", e)
                player.send(
                    error_code_toc.newBuilder()
                        .setCode(load_record_failed).build()
                )
                loading = false
            }
        }
    }

    fun pause(pause: Boolean) {
        pausing = pause
    }

    private fun displayNext(player: HumanPlayer) {
        while (true) {
            if (!player.isActive) {
                loading = false
                return
            }
            if (pausing) {
                GameExecutor.TimeWheel.newTimeout(
                    { displayNext(player) },
                    2,
                    TimeUnit.SECONDS
                )
                return
            }
            if (currentIndex >= list.size) {
                player.send(display_record_end_toc.getDefaultInstance())
                list = ArrayList()
                loading = false
                break
            }
            val line = list[currentIndex]
            player.send(line.protoName, line.messageBuf.toByteArray(), true)
            if (++currentIndex >= list.size) {
                player.send(display_record_end_toc.getDefaultInstance())
                list = ArrayList()
                loading = false
                break
            }
            val diffNanoTime = list[currentIndex].nanoTime - line.nanoTime
            if (diffNanoTime > 100000000) {
                GameExecutor.TimeWheel.newTimeout(
                    { displayNext(player) },
                    diffNanoTime.coerceAtMost(2000000000),
                    TimeUnit.NANOSECONDS
                )
                break
            }
        }
    }

    fun reconnect(player: HumanPlayer) {
        player.send(reconnect_toc.newBuilder().setIsEnd(false).build())
        for (i in list.indices) {
            val line = list[i]
            player.send(line.protoName, line.messageBuf.toByteArray(), i == list.size - 1)
        }
        player.send(reconnect_toc.newBuilder().setIsEnd(true).build())
    }

    fun loading(): Boolean {
        return loading
    }

    companion object {
        private val log = Logger.getLogger(Recorder::class.java)
        private val saveLoadPool = Executors.newSingleThreadExecutor()
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