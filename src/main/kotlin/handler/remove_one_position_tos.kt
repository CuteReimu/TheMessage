package com.fengsheng.handler

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng.remove_one_position_toc
import com.fengsheng.protos.Fengsheng.remove_one_position_tos
import org.apache.log4j.Logger

class remove_one_position_tos : AbstractProtoHandler<remove_one_position_tos>() {
    override fun handle0(r: HumanPlayer, pb: remove_one_position_tos) {
        if (r.game!!.isStarted) {
            log.error("game already started")
            return
        }
        val oldPlayers = r.game!!.players
        if (oldPlayers.size <= 2) return
        val index = oldPlayers.indexOfFirst { p -> p == null }
        val players = oldPlayers.filterIndexed { i, _ -> i != index }.toTypedArray()
        r.game!!.players = players
        players.forEachIndexed { i, p ->
            p?.location = i
            if (p is HumanPlayer) p.send(remove_one_position_toc.newBuilder().setPosition(index).build())
        }
        for (p in players)
            if (p == null) return
        log.info("已满${players.size}个人，游戏开始。。。")
        r.game!!.isStarted = true
        GameExecutor.post(r.game!!) { r.game!!.start() }
        Game.newGame = Game.newInstance()
    }

    companion object {
        private val log = Logger.getLogger(remove_one_position_tos::class.java)
    }
}