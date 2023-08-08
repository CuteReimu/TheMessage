package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 池镜海技能【避风】：争夺阶段限一次，[“观海”][GuanHai]后你可以无效你使用的【截获】或【误导】，然后摸两张牌。
 */
class BiFeng : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.BI_FENG

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? OnUseCard ?: return null
        if (fsm.player != fsm.askWhom) return null
        if (fsm.askWhom.findSkill(skillId) == null) return null
        if (fsm.cardType != Common.card_type.Jie_Huo && fsm.cardType != Common.card_type.Wu_Dao) return null
        if (fsm.askWhom.getSkillUseCount(skillId) >= fsm.askWhom.getSkillUseCount(SkillId.GUAN_HAI)) return null
        fsm.askWhom.addSkillUseCount(skillId)
        return ResolveResult(excuteBiFeng(fsm), true)
    }

    private data class excuteBiFeng(val fsm: OnUseCard) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.askWhom
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = wait_for_skill_bi_feng_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(fsm.askWhom.location)
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq = p.seq
                        builder.seq = seq
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq)) {
                                val builder2 = skill_bi_feng_tos.newBuilder()
                                builder2.enable = false
                                builder2.seq = seq
                                g.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val builder2 = skill_bi_feng_tos.newBuilder()
                    builder2.enable = true
                    g.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            val pb = message as? skill_bi_feng_tos
            if (pb == null) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (player != fsm.askWhom) {
                log.error("没有轮到你操作")
                (player as? HumanPlayer)?.sendErrorMessage("没有轮到你操作")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(pb.seq)) {
                log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${pb.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            player.incrSeq()
            if (!pb.enable)
                return ResolveResult(fsm, true)
            log.info("${fsm.askWhom}发动了[避风]")
            fsm.askWhom.addSkillUseCount(SkillId.BI_FENG, 99999)
            for (p in player.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_bi_feng_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(player.location)
                    builder.card = fsm.card!!.toPbCard()
                    fsm.targetPlayer?.also { builder.targetPlayerId = p.getAlternativeLocation(it.location) }
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm.copy(valid = false), true)
        }

        companion object {
            private val log = Logger.getLogger(excuteBiFeng::class.java)
        }
    }
}