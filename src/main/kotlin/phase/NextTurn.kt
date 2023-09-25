package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Game
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.countTrueCard
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.color.Has_No_Identity
import com.fengsheng.protos.Common.secret_task.Disturber
import com.fengsheng.skill.InvalidSkill
import com.fengsheng.skill.OneTurnSkill
import com.fengsheng.skill.SkillId.WEI_SHENG
import org.apache.log4j.Logger

/**
 * 即将跳转到下一回合时
 *
 * @param player 当前回合的玩家（不是下回合的玩家）
 */
data class NextTurn(val player: Player) : Fsm {
    override fun resolve(): ResolveResult {
        val game = player.game!!
        val result = game.dealListeningSkill(player.location)
        if (result != null)
            return result
        if (checkDisturberWin(game))
            return ResolveResult(null, false)
        if (game.checkOnlyOneAliveIdentityPlayers())
            return ResolveResult(null, false)
        var whoseTurn = player.location
        while (true) {
            whoseTurn = (whoseTurn + 1) % game.players.size
            val player = game.players[whoseTurn]!!
            if (player.alive) {
                game.mainPhaseAlreadyNotify = false
                game.players.forEach { it!!.resetSkillUseCount() }
                InvalidSkill.reset(game)
                OneTurnSkill.reset(game)
                return ResolveResult(DrawPhase(player), true)
            }
        }
    }

    private fun checkDisturberWin(game: Game): Boolean { // 无需判断簒夺者和左右逢源，因为簒夺者、搅局者、左右逢源都要求是自己回合
        val players = game.players.filterNotNull().filter { !it.lose }
        if (player.identity != Black || player.secretTask != Disturber) return false // 不是搅局者
        if (players.any { it !== player && it.alive && it.messageCards.countTrueCard() < 2 }) return false
        val declaredWinner = listOf(player)
        val winner = arrayListOf(player)
        if (winner.any { it.findSkill(WEI_SHENG) != null && it.roleFaceUp })
            winner.addAll(players.filter { it.identity == Has_No_Identity })
        val declaredWinners = declaredWinner.toTypedArray()
        val winners = winner.toTypedArray()
        log.info("${declaredWinners.contentToString()}宣告胜利，胜利者有${winners.contentToString()}")
        game.allPlayerSetRoleFaceUp()
        game.end(declaredWinner, winner)
        return true
    }

    companion object {
        private val log = Logger.getLogger(NextTurn::class.java)
    }
}