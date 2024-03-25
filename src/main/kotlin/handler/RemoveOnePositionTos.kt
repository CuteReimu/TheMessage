package com.fengsheng.handler

import com.fengsheng.Config
import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.removeOnePositionToc
import org.apache.logging.log4j.kotlin.logger

class RemoveOnePositionTos : AbstractProtoHandler<Fengsheng.remove_one_position_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.remove_one_position_tos) {
        if (r.game!!.isStarted) {
            logger.error("game already started")
            r.sendErrorMessage("游戏已经开始了")
            return
        }
        val oldPlayers = r.game!!.players
        val index = oldPlayers.indexOfFirst { p -> p == null }
        if (index < 0) {
            r.sendErrorMessage("已经没有空位了")
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
        logger.info("已满${players.size}个人，游戏将在5秒内开始。。。")
        r.game!!.setStartTimer()
    }
}
