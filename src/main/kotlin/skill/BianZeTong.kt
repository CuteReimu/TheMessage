package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.DiaoBao
import com.fengsheng.card.JieHuo
import com.fengsheng.card.PoYi
import com.fengsheng.card.WuDao
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 陈大耳技能【变则通】：你的传递阶段开始时，摸一张牌，然后你可以宣言两种卡牌类型“A”和“B”，直到回合结束，所有玩家的A牌只能当B牌使用。
 *
 * （AB可选 [【破译】][PoYi] [【调包】][DiaoBao] [【误导】][WuDao] [【截获】][JieHuo] ，且不能相同）
 */
class BianZeTong : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.BIAN_ZE_TONG

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? SendPhaseStart ?: return null
        askWhom === fsm.player || return null
        askWhom.getSkillUseCount(skillId) == 0 || return null
        askWhom.addSkillUseCount(skillId)
        return ResolveResult(executeBianZeTong(fsm), true)
    }

    private data class executeBianZeTong(val fsm: SendPhaseStart) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.player
            log.info("${r}发动了[变则通]")
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_wait_for_bian_ze_tong_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (p === r) {
                        val seq = p.seq
                        builder.seq = seq
                        p.timeout = GameExecutor.post(p.game!!, {
                            if (p.checkSeq(seq)) {
                                val builder2 = skill_bian_ze_tong_tos.newBuilder()
                                builder2.enable = false
                                builder2.seq = seq
                                p.game!!.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val types = validCardTypes.shuffled()
                    val builder2 = skill_bian_ze_tong_tos.newBuilder()
                    builder2.enable = true
                    builder2.cardTypeA = types[0]
                    builder2.cardTypeB = types[1]
                    r.game!!.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            r.draw(1)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.player) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_bian_ze_tong_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = fsm.player
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                for (p in r.game!!.players) {
                    if (p is HumanPlayer) {
                        val builder = skill_bian_ze_tong_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(r.location)
                        builder.enable = false
                        p.send(builder.build())
                    }
                }
                return ResolveResult(fsm, true)
            }
            if (message.cardTypeA == message.cardTypeB) {
                log.error("A和B不能相同")
                (player as? HumanPlayer)?.sendErrorMessage("A和B不能相同")
                return null
            }
            if (message.cardTypeA !in validCardTypes || message.cardTypeB !in validCardTypes) {
                log.error("A和B只能是【破译】【调包】【误导】【截获】")
                (player as? HumanPlayer)?.sendErrorMessage("A和B只能是【破译】【调包】【误导】【截获】")
                return null
            }
            r.incrSeq()
            log.info("${r}宣言了${message.cardTypeA}和${message.cardTypeB}")
            r.game!!.players.forEach { it!!.skills += BianZeTong2(message.cardTypeA, message.cardTypeB) }
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_bian_ze_tong_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.enable = true
                    builder.cardTypeA = message.cardTypeA
                    builder.cardTypeB = message.cardTypeB
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(YuSiWangPo::class.java)
        }
    }

    /**
     * 有这个技能的玩家，[cardTypeA]只能当作[cardTypeB]使用
     */
    private class BianZeTong2(cardTypeA: card_type, cardTypeB: card_type) : OneTurnSkill,
        ConvertCardSkill(cardTypeA, cardTypeB, true)


    companion object {
        private val validCardTypes = listOf(Po_Yi, Diao_Bao, Wu_Dao, Jie_Huo)
    }
}