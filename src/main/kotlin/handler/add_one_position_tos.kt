package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng
import org.apache.logging.log4j.kotlin.logger

class add_one_position_tos : AbstractProtoHandler<Fengsheng.add_one_position_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.add_one_position_tos) {
        if (r.game!!.isStarted) {
            logger.error("the game has already started")
            r.sendErrorMessage("游戏已经开始了")
            return
        }
        val players = r.game!!.players
        if (players.size >= 9) {
            r.sendErrorMessage("最多9人局")
            return
        }
        val newPlayers = arrayOf(*players, null)
        r.game!!.players = newPlayers
        for (p in players)
            (p as? HumanPlayer)?.send(Fengsheng.add_one_position_toc.getDefaultInstance())
        r.game!!.cancelStartTimer()
    }
}