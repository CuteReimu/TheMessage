package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.card.count
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.skill_jie_dao_sha_ren_a_tos
import com.fengsheng.protos.Role.skill_jie_dao_sha_ren_b_tos
import com.fengsheng.protos.skillJieDaoShaRenAToc
import com.fengsheng.protos.skillJieDaoShaRenATos
import com.fengsheng.protos.skillJieDaoShaRenBToc
import com.fengsheng.protos.skillJieDaoShaRenBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 商玉技能【借刀杀人】：争夺阶段，你可以翻开此角色牌，然后抽取另一名角色的一张手牌并展示之。若展示的牌是：**黑色**，则你可以将其置入一名角色的情报区，并将你的角色牌翻至面朝下。**非黑色**，则你摸一张牌。
 */
class JieDaoShaRen : ActiveSkill {
    override val skillId = SkillId.JIE_DAO_SHA_REN

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            logger.error("现在不是发动[借刀杀人]的时机")
            r.sendErrorMessage("现在不是发动[借刀杀人]的时机")
            return
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[借刀杀人]")
            r.sendErrorMessage("你现在正面朝上，不能发动[借刀杀人]")
            return
        }
        val pb = message as skill_jie_dao_sha_ren_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            logger.error("目标错误")
            r.sendErrorMessage("目标错误")
            return
        }
        if (pb.targetPlayerId == 0) {
            logger.error("不能以自己为目标")
            r.sendErrorMessage("不能以自己为目标")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            r.sendErrorMessage("目标已死亡")
            return
        }
        if (target.cards.isEmpty()) {
            logger.error("目标没有手牌")
            r.sendErrorMessage("目标没有手牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        val card = target.cards.random()
        g.resolve(ExecuteJieDaoShaRen(fsm, r, target, card))
    }

    private data class ExecuteJieDaoShaRen(
        val fsm: FightPhaseIdle,
        val r: Player,
        val target: Player,
        val card: Card
    ) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            val g = r.game!!
            target.deleteCard(card.id)
            r.cards.add(card)
            g.addEvent(GiveCardEvent(fsm.whoseTurn, target, r))
            logger.info("${r}对${target}发动了[借刀杀人]，抽取了一张手牌$card")
            g.players.send { p ->
                skillJieDaoShaRenAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    card = this@ExecuteJieDaoShaRen.card.toPbCard()
                    if (this@ExecuteJieDaoShaRen.card.isBlack()) {
                        waitingSecond = Config.WaitSecond
                        if (p === r) {
                            val seq2 = p.seq
                            seq = seq2
                            p.timeout = GameExecutor.post(g, {
                                if (p.checkSeq(seq2)) {
                                    g.tryContinueResolveProtocol(r, skillJieDaoShaRenBTos {
                                        enable = false
                                        seq = seq2
                                    })
                                }
                            }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        }
                    }
                }
            }
            if (!card.isBlack()) {
                r.draw(1)
                return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    var value = Int.MIN_VALUE
                    var target2 = r
                    for (p in g.sortedFrom(g.players, r.location)) {
                        p.alive || continue
                        val result = r.calculateMessageCardValue(fsm.whoseTurn, p, card)
                        if (result > value) {
                            value = result
                            target2 = p
                        }
                    }
                    g.tryContinueResolveProtocol(r, skillJieDaoShaRenBTos {
                        enable = true
                        targetPlayerId = r.getAlternativeLocation(target2.location)
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jie_dao_sha_ren_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                g.players.send {
                    skillJieDaoShaRenBToc {
                        playerId = it.getAlternativeLocation(r.location)
                        enable = false
                    }
                }
                return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                logger.error("目标错误")
                player.sendErrorMessage("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            r.incrSeq()
            logger.info("${r}将${card}置于${target}的情报区")
            r.deleteCard(card.id)
            target.messageCards.add(card)
            g.players.send {
                skillJieDaoShaRenBToc {
                    card = this@ExecuteJieDaoShaRen.card.toPbCard()
                    playerId = it.getAlternativeLocation(r.location)
                    enable = true
                    targetPlayerId = it.getAlternativeLocation(target.location)
                }
            }
            g.playerSetRoleFaceUp(r, false)
            g.addEvent(AddMessageCardEvent(fsm.whoseTurn))
            return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
        }
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            !player.roleFaceUp || return false
            player.game!!.players.anyoneWillWinOrDie(e) || return false
            val availableTargets = player.game!!.players.filter {
                it!!.alive && it.isEnemy(player) && it.cards.isNotEmpty()
            }.ifEmpty { return false }
            val weights = availableTargets.map { it!! to it.cards.count(Black).toDouble() / it.cards.size }
            val totalWeight = weights.sumOf { it.second }
            var target = availableTargets.first()!!
            if (totalWeight > 0.0) { // 按权重随机
                var weight = Random.nextDouble(totalWeight)
                for ((p, w) in weights) {
                    if (weight < w) {
                        target = p
                        break
                    }
                    weight -= w
                }
            } else {
                target = availableTargets.random()!!
            }
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skillJieDaoShaRenATos {
                    targetPlayerId = player.getAlternativeLocation(target.location)
                })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
