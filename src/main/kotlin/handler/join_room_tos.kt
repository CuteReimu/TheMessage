package com.fengsheng.handler

import com.fengsheng.*
import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.protos.Fengsheng
import com.google.protobuf.GeneratedMessageV3
import kotlinx.coroutines.runBlocking
import org.apache.log4j.Logger
import java.nio.charset.StandardCharsets

class join_room_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        if (player.game != null || player.isLoadingRecord) {
            log.error("player is already in a room")
            player.sendErrorMessage("你已经在房间里了")
            return
        }
        val pb = message as Fengsheng.join_room_tos
        // 客户端版本号不对，直接返回错误信息
        if (pb.version < Config.ClientVersion) {
            player.sendErrorMessage("客户端版本号过低，请更新客户端")
            return
        }
        val playerName = pb.name
        if (playerName.toByteArray(StandardCharsets.UTF_8).size > 24) {
            player.sendErrorMessage("名字太长了")
            return
        }
        if (playerName.isBlank() || playerName.contains(",") ||
            playerName.contains("\n") || playerName.contains("\r")
        ) {
            player.sendErrorMessage("用户名不合法")
            return
        }
        val playerInfo = Statistics.login(playerName, pb.device, pb.password)
        if (playerInfo == null) {
            player.sendErrorMessage("用户名或密码错误")
            return
        }
        val oldPlayer = Game.playerNameCache[playerName]
        val game = oldPlayer?.game
        if (game != null) {
            val continueLogin = runBlocking {
                GameExecutor.call(game) {
                    if (game.isStarted && !game.isEnd) { // 断线重连
                        oldPlayer.send(Fengsheng.notify_kicked_toc.getDefaultInstance())
                        Game.exchangePlayer(oldPlayer, player)
                        oldPlayer.setAutoPlay(false)
                        if (oldPlayer.needWaitLoad) {
                            oldPlayer.isReconnecting = true
                            player.send(Fengsheng.game_start_toc.getDefaultInstance())
                        } else {
                            oldPlayer.reconnect()
                        }
                        log.info("${oldPlayer}断线重连成功")
                        return@call false
                    }
                    return@call true
                }
            }
            if (!continueLogin) return
        }
        if (Game.GameCache.size > Config.MaxRoomCount) {
            player.sendErrorMessage("没有更多的房间了")
            return
        }
        val newGame = Game.newGame
        GameExecutor.post(newGame) {
            player.device = pb.device
            val oldPlayer2 = Game.playerNameCache.put(playerName, player)
            if (oldPlayer2 != null) {
                log.info("${oldPlayer2.playerName}离开了房间")
                newGame.players[oldPlayer2.location] = null
                oldPlayer2.game = null
                oldPlayer2.send(Fengsheng.notify_kicked_toc.getDefaultInstance())
                val reply = Fengsheng.leave_room_toc.newBuilder().setPosition(oldPlayer2.location).build()
                newGame.players.forEach { (it as? HumanPlayer)?.send(reply) }
            }
            if (!Config.IsGmEnable) {
                val humanCount = newGame.players.count { it is HumanPlayer }
                if (humanCount >= 2) newGame.removeAllRobot()
            }
            player.playerName = playerName
            player.game = newGame
            val count = PlayerGameCount(playerInfo.winCount, playerInfo.gameCount)
            player.game!!.onPlayerJoinRoom(player, count)
            val builder = Fengsheng.get_room_info_toc.newBuilder()
            builder.myPosition = player.location
            builder.onlineCount = Game.playerNameCache.size
            for (p in player.game!!.players) {
                builder.addNames(p?.playerName ?: "")
                val c = when (p) {
                    null -> PlayerGameCount(0, 0)
                    is HumanPlayer -> Statistics.getPlayerGameCount(p.playerName)
                    else -> Statistics.totalPlayerGameCount.random()
                }
                builder.addWinCounts(c.winCount)
                builder.addGameCounts(c.gameCount)
            }
            player.send(builder.build())
        }
    }

    companion object {
        private val log = Logger.getLogger(join_room_tos::class.java)

        private fun Game.removeAllRobot() {
            for ((index, robotPlayer) in players.withIndex()) {
                if (robotPlayer is RobotPlayer) {
                    players[index] = null
                    log.info("${robotPlayer.playerName}离开了房间")
                    val builder = Fengsheng.leave_room_toc.newBuilder()
                    builder.position = robotPlayer.location
                    val reply = builder.build()
                    for (p in players) {
                        if (p is HumanPlayer) {
                            p.send(reply)
                        }
                    }
                }
            }
        }
    }
}