package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.DieSkill
import com.fengsheng.phase.OnAddMessageCard
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 老汉技能【如归】：你死亡前，可以将你情报区中的一张情报置入当前回合角色的情报区中。
 */
class RuGui : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.RU_GUI

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? DieSkill
        if (fsm == null || fsm.askWhom !== fsm.diedQueue[fsm.diedIndex] || fsm.askWhom.findSkill(skillId) == null)
            return null
        if (fsm.askWhom === fsm.whoseTurn) return null
        if (!fsm.whoseTurn.alive) return null
        if (fsm.askWhom.messageCards.isEmpty()) return null
        if (fsm.askWhom.getSkillUseCount(skillId) > 0) return null
        fsm.askWhom.addSkillUseCount(skillId)
        return ResolveResult(executeRuGui(fsm), true)
    }

    private data class executeRuGui(val fsm: DieSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.askWhom
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_ru_gui_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq2 = player.seq
                        builder.seq = seq2
                        player.timeout =
                            GameExecutor.post(
                                r.game!!,
                                {
                                    val builder2 = skill_ru_gui_tos.newBuilder()
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
                val card = r.messageCards.find { it.isBlack() }
                GameExecutor.post(
                    r.game!!,
                    {
                        val builder = skill_ru_gui_tos.newBuilder()
                        if (card != null) {
                            builder.enable = true
                            builder.cardId = card.id
                        } else {
                            builder.enable = false
                        }
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    },
                    2,
                    TimeUnit.SECONDS
                )
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.askWhom) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_ru_gui_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = fsm.askWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                for (p in g.players) {
                    (p as? HumanPlayer)?.send(skill_ru_gui_toc.newBuilder().setEnable(false).build())
                }
                return ResolveResult(fsm, true)
            }
            val card = r.findMessageCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                return null
            }
            val target = fsm.whoseTurn
            if (!target.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            r.incrSeq()
            log.info("${r}发动了[如归]")
            r.deleteMessageCard(card.id)
            target.messageCards.add(card)
            fsm.receiveOrder.addPlayerIfHasThreeBlack(target)
            log.info("${r}面前的${card}移到了${target}面前")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_ru_gui_toc.newBuilder()
                    builder.enable = true
                    builder.cardId = card.id
                    builder.playerId = p.getAlternativeLocation(r.location)
                    p.send(builder.build())
                }
            }
            return ResolveResult(OnAddMessageCard(fsm.whoseTurn, fsm), true)
        }

        companion object {
            private val log = Logger.getLogger(executeRuGui::class.java)
        }
    }
}