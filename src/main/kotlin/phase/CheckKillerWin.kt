package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Game
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.secret_task
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

/**
 * 判断镇压者获胜条件，或者只剩一个人存活
 *
 * @param whoseTurn       谁的回合
 * @param diedQueue       死亡顺序
 * @param afterDieResolve 死亡结算后的下一个动作
 */
data class CheckKillerWin(val whoseTurn: Player, val diedQueue: ArrayList<Player>, val afterDieResolve: Fsm) : Fsm {
    override fun resolve(): ResolveResult {
        if (diedQueue.isEmpty()) return ResolveResult(afterDieResolve, true)
        val players = whoseTurn.game!!.players
        var killer: Player? = null
        var stealer: Player? = null
        for (p in players) {
            if (p!!.lose || p.identity != color.Black) continue
            if (p.secretTask == secret_task.Killer) killer = p else if (p.secretTask == secret_task.Stealer) stealer = p
        }
        var declaredWinner = arrayListOf<Player>()
        var winner = arrayListOf<Player>()
        if (whoseTurn === killer) {
            for (whoDie in diedQueue) {
                var count = 0
                for (card in whoDie.messageCards) {
                    for (color in card.colors) {
                        if (color != Common.color.Black) {
                            count++
                            break
                        }
                    }
                }
                if (count >= 2) {
                    declaredWinner.add(killer)
                    winner.add(killer)
                }
            }
        }
        for (whoDie in diedQueue) {
            if (whoDie.identity == color.Black && whoDie.secretTask == secret_task.Pioneer) {
                var count = 0
                for (card in whoDie.messageCards) {
                    for (color in card.colors) {
                        if (color != Common.color.Black) {
                            count++
                            break
                        }
                    }
                }
                if (count >= 1) {
                    declaredWinner.add(whoDie)
                    winner.add(whoDie)
                }
                break
            }
        }
        if (declaredWinner.isNotEmpty() && stealer != null && stealer === whoseTurn) {
            declaredWinner = arrayListOf(stealer)
            winner = ArrayList(declaredWinner)
        }
        if (declaredWinner.isNotEmpty()) {
            var hasGuXiaoMeng = false
            for (p in winner) {
                if (p.findSkill(SkillId.WEI_SHENG) != null && p.roleFaceUp) {
                    hasGuXiaoMeng = true
                    break
                }
            }
            if (hasGuXiaoMeng) {
                for (p in players) {
                    if (!p!!.lose && p.identity == color.Has_No_Identity) {
                        winner.add(p)
                    }
                }
            }
            val declaredWinners = declaredWinner.toTypedArray()
            val winners = winner.toTypedArray()
            log.info("${declaredWinners.contentToString()}宣告胜利，胜利者有${winners.contentToString()}")
            whoseTurn.game!!.allPlayerSetRoleFaceUp()
            for (p in players) p!!.notifyWin(declaredWinners, winners)
            whoseTurn.game!!.end(winner)
            return ResolveResult(null, false)
        }
        var alivePlayer: Player? = null
        for (p in players) {
            if (p!!.alive) {
                alivePlayer = if (alivePlayer == null) {
                    p
                } else {
                    // 至少有2个人存活，游戏继续
                    return ResolveResult(DieSkill(whoseTurn, diedQueue, whoseTurn, afterDieResolve), true)
                }
            }
        }
        if (alivePlayer == null) {
            // 全部死亡，游戏结束
            log.info("全部死亡，游戏结束")
            whoseTurn.game!!.allPlayerSetRoleFaceUp()
            for (p in players) {
                p!!.notifyWin(arrayOf(), arrayOf())
            }
            whoseTurn.game!!.end(emptyList())
            return ResolveResult(null, false)
        }
        // 只剩1个人存活，游戏结束
        onlyOneAliveWinner(whoseTurn.game!!, alivePlayer)
        return ResolveResult(null, false)
    }

    companion object {
        private val log = Logger.getLogger(CheckKillerWin::class.java)

        /**
         * 只剩1个人存活，游戏结束
         */
        fun onlyOneAliveWinner(g: Game, alivePlayer: Player) {
            val players = g.players
            val winner: MutableList<Player> = ArrayList()
            val identity = alivePlayer.identity
            if (identity == color.Red || identity == color.Blue) {
                for (p in players) {
                    if (!p!!.lose && identity == p.identity) {
                        winner.add(p)
                    }
                }
            } else {
                winner.add(alivePlayer)
            }
            var hasGuXiaoMeng = false
            for (p in winner) {
                if (p.findSkill(SkillId.WEI_SHENG) != null && p.roleFaceUp) {
                    hasGuXiaoMeng = true
                    break
                }
            }
            if (hasGuXiaoMeng) {
                for (p in players) {
                    if (!p!!.lose && p.identity == color.Has_No_Identity) {
                        winner.add(p)
                    }
                }
            }
            val winners = winner.toTypedArray()
            log.info("只剩下${alivePlayer}存活，胜利者有${winners.contentToString()}")
            g.allPlayerSetRoleFaceUp()
            for (p in players) {
                p!!.notifyWin(arrayOf(), winners)
            }
            g.end(winner)
        }
    }
}