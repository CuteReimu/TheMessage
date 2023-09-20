package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.DieSkill
import com.fengsheng.phase.OnAddMessageCard
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 李宁玉技能【遗信】：你死亡前，可以将一张手牌置入另一名角色的情报区。
 */
class YiXin : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.YI_XIN

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? DieSkill ?: return null
        askWhom == fsm.diedQueue[fsm.diedIndex] || return null
        askWhom.findSkill(skillId) != null || return null
        askWhom.roleFaceUp || return null
        askWhom.cards.isNotEmpty() || return null
        askWhom.getSkillUseCount(skillId) == 0 || return null
        askWhom.addSkillUseCount(skillId)
        return ResolveResult(executeYiXin(fsm, askWhom), true)
    }

    private data class executeYiXin(val fsm: DieSkill, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_yi_xin_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (player == r) {
                        val seq2 = player.seq
                        builder.seq = seq2
                        GameExecutor.post(
                            r.game!!,
                            {
                                val builder2 = skill_yi_xin_tos.newBuilder()
                                builder2.enable = false
                                builder2.seq = seq2
                                r.game!!.tryContinueResolveProtocol(r, builder2.build())
                            },
                            player.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                            TimeUnit.SECONDS
                        )
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                if (r.cards.isNotEmpty()) {
                    val card = r.cards.find { it.isBlack() } ?: r.cards.first()
                    r.game!!.players.filter { it!!.alive && r !== it && card.isBlack() == r.isEnemy(it) }
                        .randomOrNull()?.let { target ->
                            GameExecutor.post(r.game!!, {
                                val builder = skill_yi_xin_tos.newBuilder()
                                builder.enable = true
                                builder.cardId = card.id
                                builder.targetPlayerId = r.getAlternativeLocation(target.location)
                                r.game!!.tryContinueResolveProtocol(r, builder.build())
                            }, 2, TimeUnit.SECONDS)
                            return null
                        }
                }
                GameExecutor.post(
                    r.game!!,
                    {
                        val builder = skill_yi_xin_tos.newBuilder()
                        builder.enable = false
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    },
                    2,
                    TimeUnit.SECONDS
                )
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_yi_xin_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                for (p in g.players) {
                    (p as? HumanPlayer)?.send(skill_yi_xin_toc.newBuilder().setEnable(false).build())
                }
                return ResolveResult(fsm, true)
            }
            val card = r.findCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                return null
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
            r.incrSeq()
            log.info("${r}发动了[遗信]")
            r.deleteCard(card.id)
            target.messageCards.add(card)
            fsm.receiveOrder.addPlayerIfHasThreeBlack(target)
            log.info("${r}将${card}放置在${target}面前")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_yi_xin_toc.newBuilder()
                    builder.enable = true
                    builder.card = card.toPbCard()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            return ResolveResult(OnAddMessageCard(fsm.whoseTurn, fsm), true)
        }

        companion object {
            private val log = Logger.getLogger(executeYiXin::class.java)
        }
    }
}