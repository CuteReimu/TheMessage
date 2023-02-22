package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.phase.CheckWinimport

com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Common.secret_taskimport com.fengsheng.skill.Skillimport com.fengsheng.skill.SkillIdimport java.util.List com.fengsheng.protos.Common
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Loggerimport java.util.*import java.util.List

/**
 * 判断是否有人胜利
 *  * 只有接收阶段正常接收情报才会进入 [ReceivePhaseSenderSkill]
 *  * 其它情况均为置入情报区，一律进入这里。
 */
class CheckWin(
    /**
     * 谁的回合
     */
    var whoseTurn: Player,
    /**
     * 接收第三张黑色情报的顺序
     */
    val receiveOrder: ReceiveOrder,
    /**
     * 濒死结算后的下一个动作
     */
    var afterDieResolve: Fsm?
) : Fsm {
    constructor(whoseTurn: Player, afterDieResolve: Fsm?) : this(whoseTurn, ReceiveOrder(), afterDieResolve)

    override fun resolve(): ResolveResult? {
        val game = whoseTurn.game
        var stealer: Player? = null // 簒夺者
        var mutator: Player? = null // 诱变者
        val redPlayers: MutableList<Player> = ArrayList()
        val bluePlayers: MutableList<Player> = ArrayList()
        for (p in game.players) {
            if (p.isLose) continue
            when (p.identity) {
                color.Black -> {
                    if (p.secretTask == secret_task.Stealer) stealer =
                        p else if (p.secretTask == secret_task.Mutator) mutator = p
                }

                color.Red -> redPlayers.add(p)
                color.Blue -> bluePlayers.add(p)
            }
        }
        var declareWinner: MutableList<Player> = ArrayList()
        var winner: MutableList<Player> = ArrayList()
        var redWin = false
        var blueWin = false
        var mutatorMayWin = false
        for (player in game.players) {
            if (player.isLose) continue
            var red = 0
            var blue = 0
            for (card in player.messageCards.values) {
                for (color in card.colors) {
                    if (color == Common.color.Red) red++ else if (color == Common.color.Blue) blue++
                }
            }
            if (red >= 3 || blue >= 3) {
                mutatorMayWin = true
            }
            when (player.identity) {
                color.Black -> {
                    if (player.secretTask == secret_task.Collector && (red >= 3 || blue >= 3)) {
                        declareWinner.add(player)
                        winner.add(player)
                    }
                }

                color.Red -> {
                    if (red >= 3) {
                        declareWinner.add(player)
                        redWin = true
                    }
                }

                color.Blue -> {
                    if (blue >= 3) {
                        declareWinner.add(player)
                        blueWin = true
                    }
                }
            }
        }
        if (redWin) {
            winner.addAll(redPlayers)
            if (game.players.size == 4) winner.addAll(bluePlayers)
        }
        if (blueWin) {
            winner.addAll(bluePlayers)
            if (game.players.size == 4) winner.addAll(redPlayers)
        }
        if (declareWinner.isEmpty() && mutator != null && mutatorMayWin) {
            declareWinner.add(mutator)
            winner.add(mutator)
        }
        if (!declareWinner.isEmpty() && stealer != null && stealer === whoseTurn) {
            declareWinner = List.of(stealer)
            winner = ArrayList(declareWinner)
        }
        if (!declareWinner.isEmpty()) {
            var hasGuXiaoMeng = false
            for (p in winner) {
                if (p.findSkill<Skill?>(SkillId.WEI_SHENG) != null && p.isRoleFaceUp) {
                    hasGuXiaoMeng = true
                    break
                }
            }
            if (hasGuXiaoMeng) {
                for (p in game.players) {
                    if (!p.isLose && p.identity == color.Has_No_Identity) {
                        winner.add(p)
                    }
                }
            }
            val declareWinners = declareWinner.toTypedArray()
            val winners = winner.toTypedArray()
            log.info(Arrays.toString(declareWinners) + "宣告胜利，胜利者有" + Arrays.toString(winners))
            game.allPlayerSetRoleFaceUp()
            for (p in game.players) p.notifyWin(declareWinners, winners)
            whoseTurn.game.end(winner)
            return ResolveResult(null, false)
        }
        return ResolveResult(StartWaitForChengQing(whoseTurn, receiveOrder, afterDieResolve), true)
    }

    companion object {
        private val log = Logger.getLogger(CheckWin::class.java)
    }
}