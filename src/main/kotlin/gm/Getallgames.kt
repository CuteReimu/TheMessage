package com.fengsheng.gm

import com.fengsheng.CountColors
import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import java.util.function.Function

class Getallgames : Function<Map<String, String>, Any> {
    override fun apply(t: Map<String, String>): Any {
        val games = Game.gameCache.values.mapNotNull { game ->
            val (turn, players) = GameExecutor.call(game) {
                if (game.isEnd) return@call -1 to emptyList<PlayerData>()
                game.realTurn to game.players.mapNotNull m1@{
                    if (it == null) return@m1 null
                    val name = (it as? HumanPlayer)?.playerName ?: "机器人"
                    if (game.realTurn == 0) return@m1 PlayerData(name = name)
                    val count = CountColors(it.messageCards)
                    val roleName = when {
                        it.role == Common.role.unknown -> ""
                        !it.roleFaceUp -> "未知角色"
                        else -> it.roleName
                    }
                    PlayerData(
                        name = (it as? HumanPlayer)?.playerName ?: name,
                        roleName = roleName,
                        alive = it.alive,
                        cards = it.cards.size,
                        messageCards = intArrayOf(count.black, count.red, count.blue),
                        isTurn = game.fsm?.whoseTurn === it,
                    )
                }
            }
            if (turn == -1) null else GameData(game.id, turn, players)
        }.sortedBy { it.id }
        return gson.toJson(games)
    }

    private class PlayerData(
        val name: String,
        val roleName: String = "",
        val alive: Boolean = true,
        val cards: Int = 0,
        val messageCards: IntArray = intArrayOf(),
        val isTurn: Boolean = false,
    )

    private class GameData(
        val id: Int,
        val turn: Int,
        val players: List<PlayerData>,
    )

    companion object {
        private val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
    }
}
