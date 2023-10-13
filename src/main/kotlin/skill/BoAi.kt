package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 白沧浪技能【博爱】：出牌阶段限一次，你可以摸一张牌，然后可以将一张手牌交给另一名角色，若交给了女性角色，则你再摸一张牌。
 */
class BoAi : MainPhaseSkill(), InitialSkill {
    override val skillId = SkillId.BO_AI

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            log.error("现在不是出牌阶段空闲时点")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[博爱]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[博爱]一回合只能发动一次")
            return
        }
        val pb = message as skill_bo_ai_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.resolve(executeBoAi(g.fsm!!, r))
    }

    private data class executeBoAi(val fsm: Fsm, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            log.info("${r}发动了[博爱]")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_bo_ai_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (p === r) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_bo_ai_b_tos.newBuilder()
                                builder2.cardId = 0
                                builder2.seq = seq2
                                r.game!!.tryContinueResolveProtocol(r, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            r.draw(1)
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val player = r.game!!.players.find { it!!.alive && it.isPartner(r) && it.isFemale }
                    if (player == null) {
                        r.game!!.tryContinueResolveProtocol(r, skill_bo_ai_b_tos.newBuilder().setCardId(0).build())
                        return@post
                    }
                    val builder = skill_bo_ai_b_tos.newBuilder()
                    builder.cardId = r.cards.first().id
                    builder.targetPlayerId = r.getAlternativeLocation(player.location)
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_bo_ai_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                (player as? HumanPlayer)?.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.cardId == 0) {
                r.incrSeq()
                for (p in g.players) {
                    if (p is HumanPlayer) {
                        val builder = skill_bo_ai_b_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(r.location)
                        builder.enable = false
                        p.send(builder.build())
                    }
                }
                return ResolveResult(MainPhaseIdle(r), true)
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            if (message.targetPlayerId == 0) {
                log.error("不能以自己为目标")
                (player as? HumanPlayer)?.sendErrorMessage("不能以自己为目标")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            val card = r.findCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                return null
            }
            r.incrSeq()
            log.info("${r}将${card}交给$target")
            r.deleteCard(card.id)
            target.cards.add(card)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_bo_ai_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.enable = true
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target) builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            if (target.isFemale) r.draw(1)
            g.addEvent(GiveCardEvent(r, r, target))
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeBoAi::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(BoAi::class.java)
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.whoseTurn.getSkillUseCount(SkillId.BO_AI) > 0) return false
            GameExecutor.post(e.whoseTurn.game!!, {
                skill.executeProtocol(
                    e.whoseTurn.game!!, e.whoseTurn, skill_bo_ai_a_tos.getDefaultInstance()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}