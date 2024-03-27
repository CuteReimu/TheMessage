package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.card_type.Jie_Huo
import com.fengsheng.protos.Common.card_type.Wu_Dao
import com.fengsheng.protos.Role.skill_bi_feng_tos
import com.fengsheng.protos.skillBiFengToc
import com.fengsheng.protos.skillBiFengTos
import com.fengsheng.protos.waitForSkillBiFengToc
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
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
            event.cardType in listOf(Jie_Huo, Wu_Dao) || return@findEvent false
            askWhom.getSkillUseCount(skillId) == 0
        } ?: return null
        return ResolveResult(ExcuteBiFeng(g.fsm!!, event, askWhom), true)
    }

    private data class ExcuteBiFeng(val fsm: Fsm, val event: UseCardEvent, val r: Player) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            val g = r.game!!
            g.players.send { p ->
                waitForSkillBiFengToc {
                    playerId = p.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond
                    if (p === r) {
                        val seq = p.seq
                        this.seq = seq
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq)) {
                                g.tryContinueResolveProtocol(p, skillBiFengTos {
                                    enable = false
                                    this.seq = seq
                                })
                            }
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    g.tryContinueResolveProtocol(r, skillBiFengTos { enable = true })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            val pb = message as? skill_bi_feng_tos
            if (pb == null) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            if (player != r) {
                logger.error("没有轮到你操作")
                player.sendErrorMessage("没有轮到你操作")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(pb.seq)) {
                logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${pb.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            player.incrSeq()
            if (!pb.enable)
                return ResolveResult(fsm, true)
            logger.info("${r}发动了[避风]")
            r.addSkillUseCount(SkillId.BI_FENG)
            player.game!!.players.send { p ->
                skillBiFengToc {
                    playerId = p.getAlternativeLocation(player.location)
                    event.card?.let { this.card = it.toPbCard() }
                    event.targetPlayer?.let { targetPlayerId = p.getAlternativeLocation(it.location) }
                }
            }
            r.draw(2)
            event.valid = false
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(e: FightPhaseIdle): Boolean {
            val player = e.whoseFightTurn
            player.getSkillUseCount(SkillId.BI_FENG) == 0 || return false
            for (type in listOf(Jie_Huo, Wu_Dao).shuffled()) {
                !player.cannotPlayCard(type) || continue
                for (card in player.cards) {
                    val (ok, _) = player.canUseCardTypes(type, card)
                    ok || continue
                    GameExecutor.post(player.game!!, {
                        if (type == Wu_Dao) {
                            val target = listOf(
                                e.inFrontOfWhom.getNextLeftAlivePlayer(),
                                e.inFrontOfWhom.getNextRightAlivePlayer()
                            ).random()
                            card.asCard(type).execute(player.game!!, player, target)
                        } else {
                            card.asCard(type).execute(player.game!!, player)
                        }
                    }, 3, TimeUnit.SECONDS)
                    return true
                }
            }
            return false
        }
    }
}
