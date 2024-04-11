package com.fengsheng.handler

import com.fengsheng.*
import com.fengsheng.Statistics.PlayerGameCount
import com.fengsheng.protos.*
import com.google.protobuf.GeneratedMessage
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.logger
import java.nio.charset.StandardCharsets

class JoinRoomTos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessage) {
        if (player.game != null || player.isLoadingRecord) {
            logger.error("player is already in a room")
            player.sendErrorMessage("你已经在房间里了")
            return
        }
        val pb = message as Fengsheng.join_room_tos
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
        val playerInfo =
            try {
                Statistics.login(playerName, pb.password)
            } catch (e: Exception) {
                e.message?.let { player.sendErrorMessage(it) }
                return
            }
        val oldPlayer = Game.playerNameCache[playerName]
        val game = oldPlayer?.game
        if (game != null) {
            val continueLogin = runBlocking {
                GameExecutor.call(game) {
                    if (game !== oldPlayer.game) {
                        logger.info("${oldPlayer}登录异常")
                        oldPlayer.sendErrorMessage("登录异常，请稍后重试")
                        false
                    } else if (game.isStarted && !game.isEnd) { // 断线重连
                        oldPlayer.send(notifyKickedToc { })
                        Game.exchangePlayer(oldPlayer, player)
                        oldPlayer.setAutoPlay(false)
                        if (oldPlayer.needWaitLoad) {
                            oldPlayer.isReconnecting = true
                            player.send(gameStartToc { })
                        } else {
                            oldPlayer.reconnect()
                        }
                        logger.info("${oldPlayer}断线重连成功")
                        false
                    } else true
                }
            }
            if (!continueLogin) return
        }
        // 客户端版本号不对，直接返回错误信息
        if (pb.version < Config.ClientVersion.get()) {
            player.sendErrorMessage("客户端版本号过低，请重新下载最新客户端")
            return
        }
        if (Game.gameCache.size > Config.MaxRoomCount) {
            player.sendErrorMessage("房间已满，请稍后再试")
            return
        }
        val newGame = GameExecutor.getGame(pb.roomId, pb.playerCount)
        GameExecutor.post(newGame) {
            if (player.game !== null) {
                logger.warn("${player}登录异常")
                player.sendErrorMessage("登录异常，请稍后重试")
                return@post
            }
            var failed = false
            var oldPlayer2: HumanPlayer? = null
            Game.playerNameCache.compute(playerName) { _, v ->
                if (v == null) return@compute player // 抢注成功
                if (v.game !== newGame) { // 已经被其它房间注册了
                    failed = true
                    v
                } else {
                    oldPlayer2 = v // 被这个房间注册了，直接顶号
                    player
                }
            }
            if (failed) {
                logger.warn("${player}登录异常")
                player.sendErrorMessage("登录异常，请稍后重试")
                return@post
            }
            oldPlayer2?.apply { // 顶号
                logger.info("${playerName}离开了房间")
                newGame.players = newGame.players.toMutableList().apply { set(location, null) }
                newGame.cancelStartTimer()
                this.game = null
                send(notifyKickedToc { })
                val reply = leaveRoomToc { position = location }
                newGame.players.send { reply }
            }
            if (!Config.IsGmEnable) {
                val humanCount = newGame.players.count { it is HumanPlayer }
                if (humanCount >= 1) newGame.removeAllRobot()
            }
            player.playerName = playerName
            player.playerTitle = playerInfo.title
            val count = PlayerGameCount(playerInfo.winCount, playerInfo.gameCount)
            if (!newGame.onPlayerJoinRoom(player, count)) {
                Game.playerNameCache.remove(playerName) // 登录失败的话，要把注册清掉
                player.sendErrorMessage("房间已满，请稍后再试")
                return@post
            }
            player.game = newGame
            player.send(getRoomInfoToc {
                myPosition = player.location
                onlineCount = Game.onlineCount
                for (p in player.game!!.players) {
                    if (p == null) {
                        names.add("")
                        winCounts.add(0)
                        gameCounts.add(0)
                        ranks.add("")
                        scores.add(0)
                        continue
                    }
                    val name = p.playerName
                    val score = Statistics.getScore(name) ?: 0
                    val rank = if (p is HumanPlayer) ScoreFactory.getRankNameByScore(score) else ""
                    names.add(name)
                    val c =
                        if (p is HumanPlayer) Statistics.getPlayerGameCount(p.playerName)
                        else Statistics.totalPlayerGameCount.random()
                    winCounts.add(c.winCount)
                    gameCounts.add(c.gameCount)
                    ranks.add(rank)
                    scores.add(score)
                }
                notice = "${Config.Notice.get()}\n\n${Statistics.rankList25.get()}"
                roomId = newGame.id
            })
        }
    }

    companion object {
        private fun Game.removeAllRobot() {
            for ((index, robotPlayer) in players.withIndex()) {
                if (robotPlayer is RobotPlayer) {
                    players = players.toMutableList().apply { set(index, null) }
                    logger.info("${robotPlayer.playerName}离开了房间")
                    val reply = leaveRoomToc { position = robotPlayer.location }
                    players.send { reply }
                }
            }
        }
    }
}
