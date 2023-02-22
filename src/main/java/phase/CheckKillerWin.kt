package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Commonimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Common.secret_taskimport com.fengsheng.skill.Skillimport com.fengsheng.skill.SkillIdimport org.apache.log4j.Loggerimport java.util.*
/**
 * 判断镇压者获胜条件，或者只剩一个人存活
 *
 * @param whoseTurn       谁的回合
 * @param diedQueue       死亡顺序
 * @param afterDieResolve 死亡结算后的下一个动作
 */
class CheckKillerWin(whoseTurn: Player, diedQueue: List<Player?>, afterDieResolve: Fsm?) : Fsm {
    override fun resolve(): ResolveResult? {
        if (diedQueue.isEmpty()) return ResolveResult(afterDieResolve, true)
        val players = whoseTurn.game.players
        var killer: Player? = null
        var stealer: Player? = null
        for (p in players) {
            if (p.isLose || p.identity != color.Black) continue
            if (p.secretTask == secret_task.Killer) killer = p else if (p.secretTask == secret_task.Stealer) stealer = p
        }
        var declaredWinner: MutableList<Player?> = ArrayList()
        var winner: MutableList<Player?> = ArrayList()
        if (whoseTurn === killer) {
            for (whoDie in diedQueue) {
                var count = 0
                for (card in whoDie!!.messageCards.values) {
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
            if (whoDie!!.identity == color.Black && whoDie.secretTask == secret_task.Pioneer) {
                var count = 0
                for (card in whoDie.messageCards.values) {
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
        if (!declaredWinner.isEmpty() && stealer != null && stealer === whoseTurn) {
            declaredWinner = java.util.List.of(stealer)
            winner = ArrayList(declaredWinner)
        }
        if (!declaredWinner.isEmpty()) {
            var hasGuXiaoMeng = false
            for (p in winner) {
                if (p!!.findSkill<Skill?>(SkillId.WEI_SHENG) != null && p.isRoleFaceUp) {
                    hasGuXiaoMeng = true
                    break
                }
            }
            if (hasGuXiaoMeng) {
                for (p in players) {
                    if (!p.isLose && p.identity == color.Has_No_Identity) {
                        winner.add(p)
                    }
                }
            }
            val declaredWinners = declaredWinner.toTypedArray()
            val winners = winner.toTypedArray()
            log.info(Arrays.toString(declaredWinners) + "宣告胜利，胜利者有" + Arrays.toString(winners))
            whoseTurn.game.allPlayerSetRoleFaceUp()
            for (p in players) p.notifyWin(declaredWinners, winners)
            whoseTurn.game.end(winner)
            return ResolveResult(null, false)
        }
        var alivePlayer: Player? = null
        for (p in players) {
            if (p.isAlive) {
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
            whoseTurn.game.allPlayerSetRoleFaceUp()
            for (p in players) {
                p.notifyWin(arrayOfNulls(0), arrayOfNulls(0))
            }
            whoseTurn.game.end(emptyList())
            return ResolveResult(null, false)
        }
        // 只剩1个人存活，游戏结束
        onlyOneAliveWinner(whoseTurn.game, alivePlayer)
        return ResolveResult(null, false)
    }

    val whoseTurn: Player
    val diedQueue: List<Player?>
    val afterDieResolve: Fsm?

    init {
        this.card = card
        this.sendPhase = sendPhase
        this.r = r
        this.target = target
        this.card = card
        this.wantType = wantType
        this.r = r
        this.target = target
        this.card = card
        this.player = player
        this.card = card
        this.card = card
        this.drawCards = drawCards
        this.players = players
        this.mainPhaseIdle = mainPhaseIdle
        this.dieSkill = dieSkill
        this.player = player
        this.player = player
        this.onUseCard = onUseCard
        this.game = game
        this.whoseTurn = whoseTurn
        this.messageCard = messageCard
        this.dir = dir
        this.targetPlayer = targetPlayer
        this.lockedPlayers = lockedPlayers
        this.whoseTurn = whoseTurn
        this.messageCard = messageCard
        this.inFrontOfWhom = inFrontOfWhom
        this.player = player
        this.whoseTurn = whoseTurn
        this.diedQueue = diedQueue
        this.afterDieResolve = afterDieResolve
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
                    if (!p.isLose && identity == p.identity) {
                        winner.add(p)
                    }
                }
            } else {
                winner.add(alivePlayer)
            }
            var hasGuXiaoMeng = false
            for (p in winner) {
                if (p.findSkill<Skill?>(SkillId.WEI_SHENG) != null && p.isRoleFaceUp) {
                    hasGuXiaoMeng = true
                    break
                }
            }
            if (hasGuXiaoMeng) {
                for (p in players) {
                    if (!p.isLose && p.identity == color.Has_No_Identity) {
                        winner.add(p)
                    }
                }
            }
            val winners = winner.toTypedArray()
            log.info("只剩下" + alivePlayer + "存活，胜利者有" + Arrays.toString(winners))
            g.allPlayerSetRoleFaceUp()
            for (p in players) {
                p.notifyWin(arrayOfNulls(0), winners)
            }
            g.end(winner)
        }
    }
}