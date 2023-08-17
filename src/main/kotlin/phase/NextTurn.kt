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
import com.fengsheng.skill.JiangHuLing
import com.fengsheng.skill.JinBi
import com.fengsheng.skill.QiangLing
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
        if (checkDisturberWin(game))
            return ResolveResult(null, false)
        if (game.checkOnlyOneAliveIdentityPlayers())
            return ResolveResult(null, false)
        var whoseTurn = player.location
        while (true) {
            whoseTurn = (whoseTurn + 1) % game.players.size
            val player = game.players[whoseTurn]!!
            if (player.alive) {
                game.players.forEach { it!!.resetSkillUseCount() }
                InvalidSkill.reset(game)
                JinBi.resetJinBi(game)
                QiangLing.resetQiangLing(game)
                JiangHuLing.resetJiangHuLing(game)
                return ResolveResult(DrawPhase(player), true)
            }
        }
    }

    private fun checkDisturberWin(game: Game): Boolean { // 无需判断簒夺者和左右逢源，因为簒夺者、搅局者、左右逢源都要求是自己回合
        val players = game.players.filterNotNull().filter { !it.lose }
        if (player.identity != Black || player.secretTask != Disturber) return false // 不是搅局者
        if (players.any { it !== player && it.alive && it.messageCards.countTrueCard() < 2 }) return false
        val declaredWinners = arrayOf(player)
        val winner = arrayListOf(player)
        if (winner.any { it.findSkill(WEI_SHENG) != null && it.roleFaceUp })
            winner.addAll(players.filter { it.identity == Has_No_Identity })
        val winners = winner.toTypedArray()
        log.info("${declaredWinners.contentToString()}宣告胜利，胜利者有${winners.contentToString()}")
        game.allPlayerSetRoleFaceUp()
        game.players.forEach { it!!.notifyWin(arrayOf(), winners) }
        game.end(winner)
        return true
    }

    companion object {
        private val log = Logger.getLogger(NextTurn::class.java)
    }
}