package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.count
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.skill.SkillId.BI_YI_SHUANG_FEI
import com.fengsheng.skill.SkillId.WEI_SHENG
import org.apache.log4j.Logger

/**
 * 判断是否有人胜利
 *  * 只有接收阶段正常接收情报才会进入 [ReceivePhaseSenderSkill]
 *  * 其它情况均为置入情报区，一律进入这里。
 */
data class CheckWin(
    /**
     * 谁的回合
     */
    val whoseTurn: Player,
    /**
     * 接收第三张黑色情报的顺序
     */
    override val receiveOrder: ReceiveOrder,
    /**
     * 濒死结算后的下一个动作
     */
    val afterDieResolve: Fsm
) : Fsm, HasReceiveOrder {
    constructor(whoseTurn: Player, afterDieResolve: Fsm) : this(whoseTurn, ReceiveOrder(), afterDieResolve)

    override fun resolve(): ResolveResult {
        val game = whoseTurn.game!!
        val players = game.players.filterNotNull().filter { !it.lose }
        val stealer = players.find { it.identity == Black && it.secretTask == Stealer } // 簒夺者
        val mutator = // 诱变者
            players.find { (it.alive || it.dieJustNow) && it.identity == Black && it.secretTask == Mutator }
        var declareWinner = HashMap<Int, Player>()
        var winner = HashMap<Int, Player>()
        fun HashMap<Int, Player>.addPlayer(player: Player) = put(player.location, player)
        fun HashMap<Int, Player>.addAllPlayers(players: Iterable<Player>) = players.forEach { addPlayer(it) }
        fun HashMap<Int, Player>.containsPlayer(player: Player) = containsKey(player.location)
        fun HashMap<Int, Player>.removePlayer(player: Player) = remove(player.location)
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
        if (declareWinner.isNotEmpty() && stealer != null && stealer === whoseTurn) {
            declareWinner = hashMapOf(stealer.location to stealer)
            winner = hashMapOf(stealer.location to stealer)
        }
        if (declareWinner.isNotEmpty()) {
            if (winner.any { (_, v) -> v.findSkill(WEI_SHENG) != null && v.roleFaceUp })
                winner.addAllPlayers(players.filter { it.identity == Has_No_Identity })
            if (whoseTurn.findSkill(BI_YI_SHUANG_FEI) != null && whoseTurn.roleFaceUp && whoseTurn.alive
                && !winner.containsPlayer(whoseTurn) // 自己本来就是赢家，则不发动
            ) {
                val target = declareWinner.values.filter { it.isMale }.randomOrNull()
                if (target != null) {
                    if (target.identity == Red || target.identity == Blue) {
                        winner.values.filter { it !== target && it.identity == target.identity }
                            .forEach { winner.removePlayer(it) }
                    }
                    winner.addPlayer(whoseTurn)
                }
            }
            val declareWinners = declareWinner.values.toTypedArray()
            val winners = winner.values.toTypedArray()
            log.info("${declareWinners.contentToString()}宣告胜利，胜利者有${winners.contentToString()}")
            game.allPlayerSetRoleFaceUp()
            whoseTurn.game!!.end(declareWinner.values.toList(), winner.values.toList())
            return ResolveResult(null, false)
        }
        return ResolveResult(StartWaitForChengQing(whoseTurn, receiveOrder, afterDieResolve), true)
    }

    companion object {
        private val log = Logger.getLogger(CheckWin::class.java)
    }
}