package com.fengsheng

import com.fengsheng.protos.Common.role
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.*

object Config {
    val ListenPort: Int
    val ListenWebSocketPort: Int
    val TotalPlayerCount: Int
    val HandCardCountBegin: Int
    val HandCardCountEachTurn: Int
    val IsGmEnable: Boolean
    val GmListenPort: Int
    val ClientVersion: Int
    val MaxRoomCount: Int
    val DebugRoles: List<role>
    val RecordListSize: Int

    init {
        val pps = Properties()
        try {
            FileInputStream("application.properties").use { `in` -> pps.load(`in`) }
        } catch (_: FileNotFoundException) {
            // Ignored
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
        pps.putIfAbsent("listen_port", "9091")
        pps.putIfAbsent("listen_websocket_port", "12222")
        pps.putIfAbsent("player.total_count", "5")
        pps.putIfAbsent("rule.hand_card_count_begin", "3")
        pps.putIfAbsent("rule.hand_card_count_each_turn", "3")
        pps.putIfAbsent("gm.enable", "false")
        pps.putIfAbsent("gm.listen_port", "9092")
        pps.putIfAbsent("client_version", "1")
        pps.putIfAbsent("room_count", "200")
        pps.putIfAbsent("gm.debug_roles", "22,26")
        pps.putIfAbsent("record_list_size", "20")
        ListenPort = pps.getProperty("listen_port").toInt()
        ListenWebSocketPort = pps.getProperty("listen_websocket_port").toInt()
        TotalPlayerCount = pps.getProperty("player.total_count").toInt()
        HandCardCountBegin = pps.getProperty("rule.hand_card_count_begin").toInt()
        HandCardCountEachTurn = pps.getProperty("rule.hand_card_count_each_turn").toInt()
        IsGmEnable = pps.getProperty("gm.enable").toBoolean()
        GmListenPort = pps.getProperty("gm.listen_port").toInt()
        ClientVersion = pps.getProperty("client_version").toInt()
        MaxRoomCount = pps.getProperty("room_count").toInt()
        val debugRoleStr = pps.getProperty("gm.debug_roles")
        DebugRoles = if (debugRoleStr.isBlank()) {
            listOf()
        } else {
            val debugRoles = debugRoleStr.split(",")
            List(debugRoles.size) { i -> role.forNumber(debugRoles[i].toInt()) ?: role.unknown }
        }
        RecordListSize = pps.getProperty("record_list_size").toInt()
        try {
            FileOutputStream("application.properties").use { out -> pps.store(out, "application.properties") }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}