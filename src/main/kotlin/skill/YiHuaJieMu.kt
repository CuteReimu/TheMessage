package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnAddMessageCard
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 韩梅技能【移花接木】：争夺阶段，你可以翻开此角色牌，然后从一名角色的情报区选择一张情报，将其置入另一名角色的情报区，若如此做会让其收集三张或更多同色情报，则改为将该情报加入你的手牌。
 */
class YiHuaJieMu : AbstractSkill(), ActiveSkill {
    override val skillId = SkillId.YI_HUA_JIE_MU

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (fsm == null || r !== fsm.whoseFightTurn) {
            log.error("[移花接木]的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("[移花接木]的使用时机不对")
            return
        }
        if (r.roleFaceUp) {
            log.error("你现在正面朝上，不能发动[移花接木]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[移花接木]")
            return
        }
        if (g.players.all { !it!!.alive || it.messageCards.isEmpty() }) {
            log.error("场上没有情报，不能发动[移花接木]")
            (r as? HumanPlayer)?.sendErrorMessage("场上没有情报，不能发动[移花接木]")
            return
        }
        if (g.players.count { it!!.alive } < 2) {
            log.error("场上没有两名存活的角色，不能发动[移花接木]")
            (r as? HumanPlayer)?.sendErrorMessage("场上没有两名存活的角色，不能发动[移花接木]")
            return
        }
        r.incrSeq()
        g.playerSetRoleFaceUp(r, true)
        r.addSkillUseCount(skillId)
        log.info("${r}发动了[移花接木]")
        g.resolve(executeYiHuaJieMu(fsm, r))
    }

    private data class executeYiHuaJieMu(val fsm: FightPhaseIdle, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val alivePlayers = r.game!!.players.filterNotNull().filter { it.alive }
            val fromPlayer = alivePlayers.filter { it.messageCards.isNotEmpty() }.random()
            val card = fromPlayer.messageCards.random()
            val toPlayer = alivePlayers.filter { it !== fromPlayer }.random()
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_yi_hua_jie_mu_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond * 2
                    if (p === r) {
                        val seq = p.seq
                        builder.seq = seq
                        p.timeout = GameExecutor.post(p.game!!, {
                            if (p.checkSeq(seq)) {
                                val builder2 = skill_yi_hua_jie_mu_b_tos.newBuilder()
                                builder2.fromPlayerId = p.getAlternativeLocation(fromPlayer.location)
                                builder2.cardId = card.id
                                builder2.toPlayerId = p.getAlternativeLocation(toPlayer.location)
                                builder2.seq = seq
                                r.game!!.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val builder2 = skill_yi_hua_jie_mu_b_tos.newBuilder()
                    builder2.fromPlayerId = r.getAlternativeLocation(fromPlayer.location)
                    builder2.cardId = card.id
                    builder2.toPlayerId = r.getAlternativeLocation(toPlayer.location)
                    r.game!!.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (r !== player) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_yi_hua_jie_mu_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.fromPlayerId < 0 || message.fromPlayerId >= player.game!!.players.size) {
                log.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            val fromPlayer = player.game!!.players[player.getAbstractLocation(message.fromPlayerId)]!!
            if (!fromPlayer.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            if (message.toPlayerId < 0 || message.toPlayerId >= player.game!!.players.size) {
                log.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            val toPlayer = player.game!!.players[player.getAbstractLocation(message.toPlayerId)]!!
            if (!toPlayer.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            if (message.fromPlayerId == message.toPlayerId) {
                log.error("选择的两个目标不能相同")
                (player as? HumanPlayer)?.sendErrorMessage("选择的两个目标不能相同")
                return null
            }
            val card = fromPlayer.findMessageCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                return null
            }
            player.incrSeq()
            fromPlayer.deleteMessageCard(card.id)
            val joinIntoHand = toPlayer.checkThreeSameMessageCard(card)
            if (joinIntoHand) {
                player.cards.add(card)
                log.info("${fromPlayer}面前的${card}加入了${player}的手牌")
            } else {
                toPlayer.messageCards.add(card)
                log.info("${fromPlayer}面前的${card}加入了${toPlayer}的情报区")
            }
            for (p in player.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_yi_hua_jie_mu_b_toc.newBuilder()
                    builder.cardId = card.id
                    builder.joinIntoHand = joinIntoHand
                    builder.playerId = p.getAlternativeLocation(player.location)
                    builder.fromPlayerId = p.getAlternativeLocation(fromPlayer.location)
                    builder.toPlayerId = p.getAlternativeLocation(toPlayer.location)
                    p.send(builder.build())
                }
            }
            val newFsm = fsm.copy(whoseFightTurn = fsm.inFrontOfWhom)
            if (!joinIntoHand)
                return ResolveResult(OnAddMessageCard(fsm.whoseTurn, newFsm), true)
            return ResolveResult(newFsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeYiHuaJieMu::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(YiHuaJieMu::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val p = e.whoseFightTurn
            if (p.roleFaceUp) return false
            val g = p.game!!
            if (g.players.all { !it!!.alive || it.messageCards.isEmpty() }) return false
            if (g.players.count { it!!.alive } < 2) return false
            GameExecutor.post(e.whoseFightTurn.game!!, {
                skill.executeProtocol(g, e.whoseFightTurn, skill_yi_hua_jie_mu_a_tos.getDefaultInstance())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}