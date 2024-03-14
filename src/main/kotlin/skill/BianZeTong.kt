package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.DiaoBao
import com.fengsheng.card.JieHuo
import com.fengsheng.card.PoYi
import com.fengsheng.card.WuDao
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Role.skill_bian_ze_tong_tos
import com.fengsheng.protos.skillBianZeTongToc
import com.fengsheng.protos.skillBianZeTongTos
import com.fengsheng.protos.skillWaitForBianZeTongToc
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 陈大耳技能【变则通】：你的传递阶段开始时，摸一张牌，然后你可以宣言两种卡牌类型“A”和“B”，直到回合结束，所有玩家的A牌只能当B牌使用。
 *
 * （AB可选 [【破译】][PoYi] [【调包】][DiaoBao] [【误导】][WuDao] [【截获】][JieHuo] ，且不能相同）
 */
class BianZeTong : TriggeredSkill {
    override val skillId = SkillId.BIAN_ZE_TONG

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        g.findEvent<SendPhaseStartEvent>(this) { event ->
            askWhom === event.whoseTurn
        } ?: return null
        return ResolveResult(executeBianZeTong(g.fsm!!, askWhom), true)
    }

    private data class executeBianZeTong(val fsm: Fsm, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            logger.info("${r}发动了[变则通]")
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    p.send(skillWaitForBianZeTongToc {
                        playerId = p.getAlternativeLocation(r.location)
                        waitingSecond = Config.WaitSecond
                        if (p === r) {
                            val seq = p.seq
                            this.seq = seq
                            p.timeout = GameExecutor.post(p.game!!, {
                                if (p.checkSeq(seq)) {
                                    p.game!!.tryContinueResolveProtocol(p, skillBianZeTongTos {
                                        enable = false
                                        this.seq = seq
                                    })
                                }
                            }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        }
                    })
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    r.game!!.tryContinueResolveProtocol(r, skillBianZeTongTos {
                        enable = true
                        cardTypeA = listOf(Diao_Bao, Wu_Dao, Jie_Huo).run {
                            filter { type -> r.cards.all { it.type != type } }.ifEmpty { this }
                        }.random()
                        cardTypeB = Po_Yi
                    })
                }, 3, TimeUnit.SECONDS)
            }
            r.draw(1)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_bian_ze_tong_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                for (p in r.game!!.players) {
                    if (p is HumanPlayer) {
                        p.send(skillBianZeTongToc {
                            playerId = p.getAlternativeLocation(r.location)
                            enable = false
                        })
                    }
                }
                return ResolveResult(fsm, true)
            }
            if (message.cardTypeA == message.cardTypeB) {
                logger.error("A和B不能相同")
                (player as? HumanPlayer)?.sendErrorMessage("A和B不能相同")
                return null
            }
            if (message.cardTypeA !in validCardTypes || message.cardTypeB !in validCardTypes) {
                logger.error("A和B只能是【破译】【调包】【误导】【截获】")
                (player as? HumanPlayer)?.sendErrorMessage("A和B只能是【破译】【调包】【误导】【截获】")
                return null
            }
            r.incrSeq()
            logger.info("${r}宣言了${message.cardTypeA}和${message.cardTypeB}")
            r.game!!.players.forEach { it!!.skills += BianZeTong2(message.cardTypeA, message.cardTypeB) }
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    p.send(skillBianZeTongToc {
                        playerId = p.getAlternativeLocation(r.location)
                        enable = true
                        cardTypeA = message.cardTypeA
                        cardTypeB = message.cardTypeB
                    })
                }
            }
            return ResolveResult(fsm, true)
        }
    }

    /**
     * 有这个技能的玩家，[cardTypeA]只能当作[cardTypeB]使用
     */
    private class BianZeTong2(cardTypeA: card_type, cardTypeB: card_type) : OneTurnSkill,
        ConvertCardSkill(cardTypeA, listOf(cardTypeB), true) {
        override val isInitialSkill = false
    }

    companion object {
        private val validCardTypes = listOf(Po_Yi, Diao_Bao, Wu_Dao, Jie_Huo)
    }
}