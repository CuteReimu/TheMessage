package com.fengsheng

import com.fengsheng.card.count
import com.fengsheng.phase.StartWaitForChengQing
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.skill.changeDrawCardCount
import com.fengsheng.skill.checkWin
import org.apache.logging.log4j.kotlin.logger
import java.util.*

/**
 * 游戏流程相关的状态机，进入这个状态时，先会处理所有事件，再判断输赢，最后再调用execute0函数
 */
abstract class ProcessFsm : Fsm {
    abstract val whoseTurn: Player

    private var justSwitch = true

    protected open val needCheckWinAndDying = true

    /** 刚切到这个状态时执行的操作 */
    protected open fun onSwitch() {}

    override fun resolve(): ResolveResult? {
        if (justSwitch) {
            justSwitch = false
            onSwitch()
        }
        val result = whoseTurn.game!!.dealListeningSkill(whoseTurn.location)
        if (result != null) return result
        if (needCheckWinAndDying) {
            val winResult = checkWin()
            if (winResult != null) return winResult
            if (whoseTurn.game!!.checkOnlyOneAliveIdentityPlayers(whoseTurn))
                return ResolveResult(null, false)
            val dyingResult = checkDying()
            if (dyingResult != null) return dyingResult
            whoseTurn.game!!.players.forEach { it!!.dieJustNow = false }
        }
        return resolve0()
    }

    private fun checkWin(): ResolveResult? {
        val game = whoseTurn.game!!
        val players = game.players.filterNotNull().filter { !it.lose }
        val stealer =
            players.find { it.identity == Black && it.secretTask == Stealer } // 簒夺者
        val mutator = // 诱变者
            players.find { (it.alive || it.dieJustNow) && it.identity == Black && it.secretTask == Mutator }
        var declareWinner = HashMap<Int, Player>()
        var winner = HashMap<Int, Player>()
        players.forEach { player ->
            val red = player.messageCards.count(Red)
            val blue = player.messageCards.count(Blue)
            if (red >= 3) {
                if (player.identity == Red) {
                    declareWinner.addPlayer(player)
                    winner.addAllPlayers(players.filter { it.identity == Red })
                    if (game.players.size == 4) // 四人局潜伏和军情会同时获胜
                        winner.addAllPlayers(players.filter { it.identity == Blue })
                } else if (player.identity == Black && player.secretTask == Collector) {
                    declareWinner.addPlayer(player)
                    winner.addPlayer(player)
                }
            }
            if (blue >= 3) {
                if (player.identity == Blue) {
                    declareWinner.addPlayer(player)
                    winner.addAllPlayers(players.filter { it.identity == Blue })
                    if (game.players.size == 4) // 四人局潜伏和军情会同时获胜
                        winner.addAllPlayers(players.filter { it.identity == Red })
                } else if (player.identity == Black && player.secretTask == Collector) {
                    declareWinner.addPlayer(player)
                    winner.addPlayer(player)
                }
            }
            if ((red >= 3 || blue >= 3) && !declareWinner.containsPlayer(player)) {
                mutator?.let {
                    declareWinner.addPlayer(it)
                    winner.addPlayer(it)
                }
            }
        }
        whoseTurn.game!!.checkWin(whoseTurn, declareWinner, winner)
        if (declareWinner.isNotEmpty() && stealer != null && stealer === whoseTurn) {
            declareWinner = hashMapOf(stealer.location to stealer)
            winner = hashMapOf(stealer.location to stealer)
        }
        val declareWinners = declareWinner.values.toMutableList()
        val winners = winner.values.toMutableList()
        whoseTurn.game!!.changeDrawCardCount(whoseTurn, declareWinners, winners)
        if (declareWinner.isNotEmpty()) {
            logger.info("${declareWinners.joinToString()}宣告胜利，胜利者有${winners.joinToString()}")
            game.allPlayerSetRoleFaceUp()
            whoseTurn.game!!.end(declareWinners, winners)
            return ResolveResult(null, false)
        }
        return null
    }

    private fun checkDying(): ResolveResult? {
        val g = whoseTurn.game!!
        val dyingPlayers = g.players.filterNotNull().filter { it.alive && it.messageCards.count(Black) >= 3 }
        if (dyingPlayers.isEmpty()) return null
        val orderedDyingPlayers = g.sortedFrom(dyingPlayers, whoseTurn.location)
        return ResolveResult(
            StartWaitForChengQing(whoseTurn, LinkedList(orderedDyingPlayers), this),
            true
        )
    }

    abstract fun resolve0(): ResolveResult?
}

fun MutableMap<Int, Player>.addPlayer(player: Player) = put(player.location, player)
fun MutableMap<Int, Player>.addAllPlayers(players: Iterable<Player>) = players.forEach { addPlayer(it) }
fun MutableMap<Int, Player>.containsPlayer(player: Player) = containsKey(player.location)