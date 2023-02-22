package com.fengsheng.handler

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class remove_one_position_tos : AbstractProtoHandler<Fengsheng.remove_one_position_tos?>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.remove_one_position_tos?) {
        synchronized(Game::class.java) {
            if (r.game!!.isStarted) {
                log.error("game already started")
                return
            }
            val oldPlayers = r.game!!.players
            if (oldPlayers.size <= 2) return
            var i = oldPlayers.size - 1
            while (i >= 0) {
                if (oldPlayers[i] == null) break
                i--
            }
            val players = arrayOfNulls<Player>(oldPlayers.size - 1)
            System.arraycopy(oldPlayers, 0, players, 0, i)
            System.arraycopy(oldPlayers, i + 1, players, i, oldPlayers.size - i - 1)
            i = 0
            while (i < players.size) {
                if (players[i] != null) players[i]!!.location = i
                i++
            }
            r.game!!.players = players
            for (p in players) {
                if (p is HumanPlayer) p.send(Fengsheng.remove_one_position_toc.newBuilder().setPosition(i).build())
            }
            for (p in players) if (p == null) return
            log.info("已满${players.size}个人，游戏开始。。。")
            r.game!!.isStarted = true
            GameExecutor.post(r.game!!) { r.game!!.start() }
            Game.newGame = Game.newInstance()
        }
    }

    companion object {
        private val log = Logger.getLogger(remove_one_position_tos::class.java)
    }
}