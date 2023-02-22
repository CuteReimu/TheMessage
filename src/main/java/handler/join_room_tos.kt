package com.fengsheng.handler

import com.fengsheng.*
import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.Statistics.PlayerInfo
import com.fengsheng.network.WebSocketServerChannelHandler
import com.fengsheng.protos.Errcode
import com.fengsheng.protos.Errcode.error_code_toc
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.get_room_info_toc
import java.util.concurrent.CountDownLatch

com.google.protobuf.GeneratedMessageV3

org.apache.log4j.Loggerimport java.nio.charset.StandardCharsets
class join_room_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        if (player.game != null || player.isLoadingRecord) {
            log.error("player is already in a room")
            return
        }
        val pb = message as Fengsheng.join_room_tos
        // 客户端版本号不对，直接返回错误码
        if (pb.version < Config.ClientVersion) {
            player.send(
                error_code_toc.newBuilder()
                    .setCode(Errcode.error_code.client_version_not_match)
                    .addIntParams(Config.ClientVersion.toLong()).build()
            )
            player.channel.close()
            return
        }
        val device = pb.device
        val oldPlayer: HumanPlayer = Game.Companion.deviceCache.get(device)
        if (oldPlayer != null && oldPlayer.game != null && oldPlayer.game.isStarted && !oldPlayer.game.isEnd) { // 断线重连
            if (oldPlayer.isActive) {
                player.send(error_code_toc.newBuilder().setCode(Errcode.error_code.already_online).build())
                player.channel.close()
                return
            }
            val cd = CountDownLatch(1)
            GameExecutor.Companion.post(oldPlayer.game, Runnable {
                WebSocketServerChannelHandler.Companion.exchangePlayer(oldPlayer, player)
                cd.countDown()
                oldPlayer.setAutoPlay(false)
                oldPlayer.reconnect()
                log.info(oldPlayer.toString() + "断线重连成功")
            })
            try {
                cd.await()
            } catch (e: InterruptedException) {
                log.error("thread " + Thread.currentThread().id + " interrupted")
                Thread.currentThread().interrupt()
            }
            return
        }
        if (pb.name.toByteArray(StandardCharsets.UTF_8).size > 24) {
            player.send(
                error_code_toc.newBuilder()
                    .setCode(Errcode.error_code.name_too_long).build()
            )
            return
        }
        var reply: GeneratedMessageV3?
        synchronized(Game::class.java) {
            if (Game.Companion.GameCache.size > Config.MaxRoomCount) {
                reply = error_code_toc.newBuilder().setCode(Errcode.error_code.no_more_room).build()
            } else {
                player.device = pb.device
                val oldPlayer2: Player = Game.Companion.deviceCache.putIfAbsent(pb.device, player)
                val playerName = pb.name
                if (oldPlayer2 != null && oldPlayer2.game == Game.Companion.getInstance() && playerName == oldPlayer2.playerName) {
                    log.warn("怀疑连续发送了两次连接请求。为了游戏体验，拒绝本次连接。想要单设备双开请修改不同的用户名。")
                    reply = error_code_toc.newBuilder().setCode(Errcode.error_code.join_room_too_fast).build()
                } else if (playerName.isBlank() || playerName.contains(",") || playerName.contains("\n") || playerName.contains(
                        "\r"
                    )
                ) {
                    reply = error_code_toc.newBuilder().setCode(Errcode.error_code.login_failed).build()
                } else {
                    val playerInfo: PlayerInfo =
                        Statistics.Companion.getInstance().login(playerName, pb.device, pb.password)
                    if (playerInfo == null) {
                        reply = error_code_toc.newBuilder().setCode(Errcode.error_code.login_failed).build()
                    } else {
                        player.playerName = playerName
                        player.game = Game.Companion.getInstance()
                        val count = PlayerGameCount(playerInfo.winCount, playerInfo.gameCount)
                        player.game.onPlayerJoinRoom(player, count)
                        val builder = get_room_info_toc.newBuilder().setMyPosition(player.location())
                            .setOnlineCount(Game.Companion.deviceCache.size)
                        for (p in player.game.players) {
                            builder.addNames(if (p != null) p.playerName else "")
                            var count1: PlayerGameCount? = null
                            if (p is HumanPlayer) count1 = Statistics.Companion.getInstance()
                                .getPlayerGameCount(p.getPlayerName()) else if (p is RobotPlayer) count1 =
                                Statistics.Companion.getInstance().getTotalPlayerGameCount()
                            builder.addWinCounts(count1?.winCount ?: 0)
                        }
                        reply = builder.build()
                    }
                }
            }
        }
        player.send(reply)
        if (reply is error_code_toc) player.channel.close()
    }

    companion object {
        private val log = Logger.getLogger(join_room_tos::class.java)
    }
}