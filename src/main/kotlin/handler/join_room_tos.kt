package com.fengsheng.handler

import com.fengsheng.*
import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.network.WebSocketServerChannelHandler
import com.fengsheng.protos.Errcode
import com.fengsheng.protos.Errcode.error_code_toc
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.get_room_info_toc
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch

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
            builder.code = Errcode.error_code.client_version_not_match
            builder.addIntParams(Config.ClientVersion.toLong())
            player.send(builder.build())
            player.channel.close()
            return
        }
        val device = pb.device
        val oldPlayer = Game.deviceCache[device]
        if (oldPlayer?.game != null && oldPlayer.game!!.isStarted && !oldPlayer.game!!.isEnd) { // 断线重连
            if (oldPlayer.isActive) {
                player.send(error_code_toc.newBuilder().setCode(Errcode.error_code.already_online).build())
                player.channel.close()
                return
            }
            val cd = CountDownLatch(1)
            GameExecutor.post(oldPlayer.game!!) {
                WebSocketServerChannelHandler.exchangePlayer(oldPlayer, player)
                cd.countDown()
                oldPlayer.setAutoPlay(false)
                oldPlayer.reconnect()
                log.info("${oldPlayer}断线重连成功")
            }
            try {
                cd.await()
            } catch (e: InterruptedException) {
                log.error("thread ${Thread.currentThread().id} interrupted")
                Thread.currentThread().interrupt()
            }
            return
        }
        if (pb.name.toByteArray(StandardCharsets.UTF_8).size > 24) {
            player.send(error_code_toc.newBuilder().setCode(Errcode.error_code.name_too_long).build())
            return
        }
        val reply: GeneratedMessageV3
        synchronized(Game::class.java) {
            if (Game.GameCache.size > Config.MaxRoomCount) {
                reply = error_code_toc.newBuilder().setCode(Errcode.error_code.no_more_room).build()
            } else {
                player.device = pb.device
                val oldPlayer2 = Game.deviceCache.putIfAbsent(pb.device, player)
                val playerName = pb.name
                if (oldPlayer2 != null && oldPlayer2.game === Game.newGame && playerName == oldPlayer2.playerName) {
                    log.warn("怀疑连续发送了两次连接请求。为了游戏体验，拒绝本次连接。想要单设备双开请修改不同的用户名。")
                    reply = error_code_toc.newBuilder().setCode(Errcode.error_code.join_room_too_fast).build()
                } else if (playerName.isBlank() || playerName.contains(",") ||
                    playerName.contains("\n") || playerName.contains("\r")
                ) {
                    reply = error_code_toc.newBuilder().setCode(Errcode.error_code.login_failed).build()
                } else {
                    val playerInfo = Statistics.login(playerName, pb.device, pb.password)
                    if (playerInfo == null) {
                        reply = error_code_toc.newBuilder().setCode(Errcode.error_code.login_failed).build()
                    } else {
                        player.playerName = playerName
                        player.game = Game.newGame
                        val count = PlayerGameCount(playerInfo.winCount, playerInfo.gameCount)
                        player.game!!.onPlayerJoinRoom(player, count)
                        val builder = get_room_info_toc.newBuilder().setMyPosition(player.location)
                            .setOnlineCount(Game.deviceCache.size)
                        for (p in player.game!!.players) {
                            builder.addNames(if (p != null) p.playerName else "")
                            var count1: PlayerGameCount? = null
                            if (p is HumanPlayer) count1 = Statistics.getPlayerGameCount(p.playerName!!)
                            else if (p is RobotPlayer) count1 = Statistics.totalPlayerGameCount
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