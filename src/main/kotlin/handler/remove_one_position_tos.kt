package com.fengsheng.handler

import com.fengsheng.Config
import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class remove_one_position_tos : AbstractProtoHandler<Fengsheng.remove_one_position_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.remove_one_position_tos) {
        if (r.game!!.isStarted) {
            log.error("game already started")
            r.sendErrorMessage("游戏已经开始了")
            return
        }
        val oldPlayers = r.game!!.players
        if (oldPlayers.size <= 2) {
            r.sendErrorMessage("至少2人局")
            return
        }
        if (!Config.IsGmEnable) {
            val humanCount = oldPlayers.count { it is HumanPlayer }
            if (humanCount >= 3 && oldPlayers.size <= 5) {
                r.sendErrorMessage("至少5人局")
                return
            }
        }
        val index = oldPlayers.indexOfFirst { p -> p == null }
        val players = oldPlayers.filterIndexed { i, _ -> i != index }.toTypedArray()
        r.game!!.players = players
        players.forEachIndexed { i, p ->
            p?.location = i
            if (p is HumanPlayer) p.send(Fengsheng.remove_one_position_toc.newBuilder().setPosition(index).build())
        }
        for (p in players)
            if (p == null) return
        log.info("已满${players.size}个人，游戏开始。。。")
        r.game!!.setStartTimer()
    }

    companion object {
        private val log = Logger.getLogger(remove_one_position_tos::class.java)
    }
}