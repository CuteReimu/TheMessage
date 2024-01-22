package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.card_type.Jie_Huo
import com.fengsheng.protos.Common.card_type.Wu_Dao
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 池镜海技能【避风】：争夺阶段限一次，[“观海”][GuanHai]后你可以无效你使用的【截获】或【误导】，然后摸两张牌。
 */
class BiFeng : TriggeredSkill {
    override val skillId = SkillId.BI_FENG

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<UseCardEvent>(this) { event ->
            event.player === askWhom || return@findEvent false
            event.cardType in arrayOf(Jie_Huo, Wu_Dao) || return@findEvent false
            askWhom.getSkillUseCount(skillId) == 0
        } ?: return null
        return ResolveResult(excuteBiFeng(g.fsm!!, event, askWhom), true)
    }

    private data class excuteBiFeng(val fsm: Fsm, val event: UseCardEvent, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = wait_for_skill_bi_feng_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
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
            if (player != r) {
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
            log.info("${r}发动了[避风]")
            r.addSkillUseCount(SkillId.BI_FENG)
            for (p in player.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_bi_feng_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(player.location)
                    builder.card = event.card!!.toPbCard()
                    event.targetPlayer?.also { builder.targetPlayerId = p.getAlternativeLocation(it.location) }
                    p.send(builder.build())
                }
            }
            r.draw(2)
            event.valid = false
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(excuteBiFeng::class.java)
        }
    }
}