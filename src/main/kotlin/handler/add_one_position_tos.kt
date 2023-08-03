package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class add_one_position_tos : AbstractProtoHandler<Fengsheng.add_one_position_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.add_one_position_tos) {
        if (r.game!!.isStarted) {
            log.error("the game has already started")
            r.sendErrorMessage("游戏已经开始了")
            return
        }
        val players = r.game!!.players
        if (players.size >= 9) return
        val newPlayers = arrayOf(*players, null)
        r.game!!.players = newPlayers
        for (p in players) {
            if (p is HumanPlayer) p.send(Fengsheng.add_one_position_toc.getDefaultInstance())
        }
    }

    companion object {
        private val log = Logger.getLogger(add_one_position_tos::class.java)
    }
}