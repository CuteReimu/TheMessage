package com.fengsheng.handler

import com.fengsheng.*
import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.handler.remove_robot_tos.Companion.removeOneRobot
import com.fengsheng.protos.Errcode.error_code.*
import com.fengsheng.protos.Errcode.error_code_toc
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
        // 客户端版本号不对，直接返回错误码
        if (pb.version < Config.ClientVersion) {
            val builder = error_code_toc.newBuilder()
            builder.code = client_version_not_match
            builder.addIntParams(Config.ClientVersion.toLong())
            player.send(builder.build())
            return
        }
        val playerName = pb.name
        if (playerName.toByteArray(StandardCharsets.UTF_8).size > 24) {
            player.send(error_code_toc.newBuilder().setCode(name_too_long).build())
            return
        }
        val playerInfo = Statistics.login(playerName, pb.device, pb.password)
        if (playerInfo == null) {
            player.send(error_code_toc.newBuilder().setCode(login_failed).build())
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
            player.send(error_code_toc.newBuilder().setCode(no_more_room).build())
            return
        }
        val newGame = Game.newGame
        GameExecutor.post(newGame) {
            player.device = pb.device
            if (playerName.isBlank() || playerName.contains(",") ||
                playerName.contains("\n") || playerName.contains("\r")
            ) {
                player.send(error_code_toc.newBuilder().setCode(login_failed).build())
                return@post
            }
            val oldPlayer2 = Game.playerNameCache.put(playerName, player)
            if (oldPlayer2 != null) {
                oldPlayer2.send(Fengsheng.notify_kicked_toc.getDefaultInstance())
                log.info("${player.playerName}离开了房间")
                newGame.players[player.location] = null
                val reply = Fengsheng.leave_room_toc.newBuilder().setPosition(player.location).build()
                newGame.players.forEach { (it as? HumanPlayer)?.send(reply) }
            }
            val emptyCount = newGame.players.count { it == null }
            if (emptyCount == 1) newGame.removeOneRobot()
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
    }
}