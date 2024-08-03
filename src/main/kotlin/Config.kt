package com.fengsheng

import com.fengsheng.protos.Common.role
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object Config {
    val FileServerPort: Int
    val ListenWebSocketPort: Int
    val TotalPlayerCount: Int
    val RecordMaxInterval: Int
        get() = field.coerceAtLeast(1)
    val HandCardCountBegin: Int
    val HandCardCountEachTurn: Int
    val IsGmEnable: Boolean
    val GmListenPort: Int
    val ClientVersion: AtomicInteger
    val MaxRoomCount: Int
    val DebugRoles: List<role>
    val RecordListSize: Int
    val Notice: AtomicReference<String>
    val EnablePush: Boolean
    val MiraiHttpUrl: String
    val MiraiVerifyKey: String
    val RobotQQ: Long
    val PushQQGroups: LongArray
    val WaitingSecond: AtomicInteger

    val WaitSecond: Int get() = WaitingSecond.get()

    init {
        val pps = Properties()
        try {
            FileInputStream("application.properties").use { `in` -> pps.load(`in`) }
        } catch (_: FileNotFoundException) {
            // Ignored
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
        pps.putIfAbsent("file_server_port", "9091")
        pps.putIfAbsent("listen_websocket_port", "12222")
        pps.putIfAbsent("player.total_count", "5")
        pps.putIfAbsent("rule.record_max_interval", "3")
        pps.putIfAbsent("rule.hand_card_count_begin", "3")
        pps.putIfAbsent("rule.hand_card_count_each_turn", "3")
        pps.putIfAbsent("gm.enable", "false")
        pps.putIfAbsent("gm.listen_port", "9092")
        pps.putIfAbsent("client_version", "1")
        pps.putIfAbsent("room_count", "200")
        pps.putIfAbsent("gm.debug_roles", "22,26")
        pps.putIfAbsent("record_list_size", "20")
        pps.putIfAbsent("notice", "")
        pps.putIfAbsent("push.enable_push", "false")
        pps.putIfAbsent("push.mirai_http_url", "http://127.0.0.1:8080")
        pps.putIfAbsent("push.mirai_verify_key", "")
        pps.putIfAbsent("push.robot_qq", "12345678")
        pps.putIfAbsent("push.push_qq_groups", "")
        pps.putIfAbsent("waiting_second", "15")
        FileServerPort = pps.getProperty("file_server_port").toInt()
        ListenWebSocketPort = pps.getProperty("listen_websocket_port").toInt()
        RecordMaxInterval = pps.getProperty("rule.record_max_interval").toInt()
        TotalPlayerCount = pps.getProperty("player.total_count").toInt()
        HandCardCountBegin = pps.getProperty("rule.hand_card_count_begin").toInt()
        HandCardCountEachTurn = pps.getProperty("rule.hand_card_count_each_turn").toInt()
        IsGmEnable = pps.getProperty("gm.enable").toBoolean()
        GmListenPort = pps.getProperty("gm.listen_port").toInt()
        ClientVersion = AtomicInteger(pps.getProperty("client_version").toInt())
        MaxRoomCount = pps.getProperty("room_count").toInt()
        val debugRoleStr = pps.getProperty("gm.debug_roles")
        DebugRoles = if (debugRoleStr.isBlank()) {
            listOf()
        } else {
            val debugRoles = debugRoleStr.split(",")
            List(debugRoles.size) { i -> role.forNumber(debugRoles[i].toInt()) ?: role.unknown }
        }
        RecordListSize = pps.getProperty("record_list_size").toInt()
        Notice = AtomicReference(pps.getProperty("notice"))
        EnablePush = pps.getProperty("push.enable_push").toBoolean()
        MiraiHttpUrl = pps.getProperty("push.mirai_http_url")
        MiraiVerifyKey = pps.getProperty("push.mirai_verify_key")
        RobotQQ = pps.getProperty("push.robot_qq").toLong()
        val pushQQGroupsStr = pps.getProperty("push.push_qq_groups")
        PushQQGroups =
            if (pushQQGroupsStr.isBlank()) longArrayOf()
            else pushQQGroupsStr.split(",").map { it.toLong() }.toLongArray()
        WaitingSecond = AtomicInteger(pps.getProperty("waiting_second").toInt())
        try {
            FileOutputStream("application.properties").use { out -> pps.store(out, "application.properties") }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun save() {
        synchronized(this) {
            val pps = Properties()
            pps["file_server_port"] = FileServerPort.toString()
            pps["listen_websocket_port"] = ListenWebSocketPort.toString()
            pps["player.total_count"] = TotalPlayerCount.toString()
            pps["rule.record_max_interval"] = RecordMaxInterval.toString()
            pps["rule.hand_card_count_begin"] = HandCardCountBegin.toString()
            pps["rule.hand_card_count_each_turn"] = HandCardCountEachTurn.toString()
            pps["gm.enable"] = IsGmEnable.toString()
            pps["gm.listen_port"] = GmListenPort.toString()
            pps["client_version"] = ClientVersion.get().toString()
            pps["room_count"] = MaxRoomCount.toString()
            pps["gm.debug_roles"] = DebugRoles.joinToString(separator = ",") { it.number.toString() }
            pps["record_list_size"] = RecordListSize.toString()
            pps["notice"] = Notice.get()
            pps["push.enable_push"] = EnablePush.toString()
            pps["push.mirai_http_url"] = MiraiHttpUrl
            pps["push.mirai_verify_key"] = MiraiVerifyKey
            pps["push.robot_qq"] = RobotQQ.toString()
            pps["push.push_qq_groups"] = PushQQGroups.joinToString(separator = ",")
            pps["waiting_second"] = WaitingSecond.get().toString()
            FileOutputStream("application.properties").use { out -> pps.store(out, "application.properties") }
        }
    }
}
