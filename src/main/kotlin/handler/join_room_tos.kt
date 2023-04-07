package com.fengsheng.handler

import com.fengsheng.*
import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.protos.Errcode.error_code
import com.fengsheng.protos.Errcode.error_code_toc
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.get_room_info_toc
import com.google.protobuf.GeneratedMessageV3
import kotlinx.coroutines.runBlocking
import org.apache.log4j.Logger
import java.nio.charset.StandardCharsets

class join_room_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        if (player.game != null || player.isLoadingRecord) {
            log.error("player is already in a room")
            return
        }
        val pb = message as Fengsheng.join_room_tos
        // 客户端版本号不对，直接返回错误码
        if (pb.version < Config.ClientVersion) {
            val builder = error_code_toc.newBuilder()
            builder.code = error_code.client_version_not_match
            builder.addIntParams(Config.ClientVersion.toLong())
            player.send(builder.build())
            player.channel.close()
            return
        }
        val device = pb.device
        val oldPlayer = Game.deviceCache[device]
        val game = oldPlayer?.game
        if (game != null) {
            val continueLogin = runBlocking {
                GameExecutor.call(game) {
                    if (game.isStarted && !game.isEnd) { // 断线重连
                        if (oldPlayer.isActive) {
                            player.send(error_code_toc.newBuilder().setCode(error_code.already_online).build())
                            player.channel.close()
                            return@call false
                        }
                        Game.exchangePlayer(oldPlayer, player)
                        oldPlayer.setAutoPlay(false)
                        oldPlayer.reconnect()
                        log.info("${oldPlayer}断线重连成功")
                        return@call false
                    }
                    return@call true
                }
            }
            if (!continueLogin) return
        }
        if (pb.name.toByteArray(StandardCharsets.UTF_8).size > 24) {
            player.send(error_code_toc.newBuilder().setCode(error_code.name_too_long).build())
            return
        }
        if (Game.GameCache.size > Config.MaxRoomCount) {
            player.send(error_code_toc.newBuilder().setCode(error_code.no_more_room).build())
            player.channel.close()
            return
        }
        val newGame = Game.newGame
        GameExecutor.post(newGame) {
            player.device = pb.device
            val oldPlayer2 = Game.deviceCache.putIfAbsent(pb.device, player)
            val playerName = pb.name
            if (oldPlayer2 != null && oldPlayer2.game === newGame && playerName == oldPlayer2.playerName) {
                log.warn("怀疑连续发送了两次连接请求。为了游戏体验，拒绝本次连接。想要单设备双开请修改不同的用户名。")
                player.send(error_code_toc.newBuilder().setCode(error_code.join_room_too_fast).build())
                player.channel.close()
            } else if (playerName.isBlank() || playerName.contains(",") ||
                playerName.contains("\n") || playerName.contains("\r")
            ) {
                player.send(error_code_toc.newBuilder().setCode(error_code.login_failed).build())
                player.channel.close()
            } else {
                val playerInfo = Statistics.login(playerName, pb.device, pb.password)
                if (playerInfo == null) {
                    player.send(error_code_toc.newBuilder().setCode(error_code.login_failed).build())
                } else {
                    player.playerName = playerName
                    player.game = newGame
                    val count = PlayerGameCount(playerInfo.winCount, playerInfo.gameCount)
                    player.game!!.onPlayerJoinRoom(player, count)
                    val builder = get_room_info_toc.newBuilder()
                    builder.myPosition = player.location
                    builder.onlineCount = Game.deviceCache.size
                    for (p in player.game!!.players) {
                        builder.addNames(p?.playerName ?: "")
                        val count1 =
                            if (p is HumanPlayer) Statistics.getPlayerGameCount(p.playerName)
                            else Statistics.totalPlayerGameCount.random()
                        builder.addWinCounts(count1.winCount)
                    }
                    player.send(builder.build())
                }
            }
        }
    }

    companion object {
        private val log = Logger.getLogger(join_room_tos::class.java)
    }
}