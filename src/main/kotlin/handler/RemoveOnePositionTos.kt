package com.fengsheng.handler

import com.fengsheng.*
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.leaveRoomToc
import com.fengsheng.protos.removeOnePositionToc
import org.apache.logging.log4j.kotlin.logger

class RemoveOnePositionTos : AbstractProtoHandler<Fengsheng.remove_one_position_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.remove_one_position_tos) {
        if (r.game!!.isStarted) {
            return
        }
        val oldPlayers = r.game!!.players
        val index = oldPlayers.indexOfFirst { p -> p == null }
        if (index < 0) {
            return
        }
        if (oldPlayers.size <= 2) {
            r.sendErrorMessage("至少2人局")
            return
        }
        if (!Config.IsGmEnable && oldPlayers.size <= 5) {
            r.sendErrorMessage("至少5人局")
            return
        }
        val players = oldPlayers.filterIndexed { i, _ -> i != index }
        r.game!!.players = players
        players.forEachIndexed { i, p ->
            p?.location = i
            p?.send(removeOnePositionToc { position = index })
        }
        if (players.any { it == null }) return
        if (!Config.IsGmEnable && players.count { it is HumanPlayer } <= 1 && Statistics.getEnergy(r.playerName) <= 0) {
            val robotPlayerIndex = players.indexOfLast { it is RobotPlayer }
            if (robotPlayerIndex >= 0) {
                val robotPlayer = players[robotPlayerIndex]!!
                r.game!!.players = r.game!!.players.toMutableList().apply { set(robotPlayerIndex, null) }
                logger.info("${robotPlayer.playerName}离开了房间")
                val reply = leaveRoomToc { position = robotPlayer.location }
                players.send { reply }
                return
            }
        }
        logger.info("已满${players.size}个人，游戏将在5秒内开始。。。")
        r.game!!.setStartTimer()
    }
}
