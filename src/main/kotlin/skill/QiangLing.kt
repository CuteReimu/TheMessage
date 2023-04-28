package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.OnChooseReceiveCard
import com.fengsheng.phase.OnSendCard
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 张一挺技能【强令】：你传出情报后，或你决定接收情报后，可以宣言至多两个卡牌名称。本回合中，所有角色均不能使用被宣言的卡牌。
 */
class QiangLing : TriggeredSkill {
    override val skillId = SkillId.QIANG_LING

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm
        if (fsm is OnSendCard) {
            val r = fsm.sender
            if (r.findSkill(skillId) == null) return null
            if (r.getSkillUseCount(skillId) >= 1) return null
            r.addSkillUseCount(skillId)
            return ResolveResult(executeQiangLing(fsm, r), true)
        } else if (fsm is OnChooseReceiveCard) {
            val r: Player = fsm.inFrontOfWhom
            if (r.findSkill(skillId) == null) return null
            if (r.getSkillUseCount(skillId) >= 2) return null
            r.addSkillUseCount(skillId, 2)
            return ResolveResult(executeQiangLing(fsm, r), true)
        }
        return null
    }

    private data class executeQiangLing(val fsm: Fsm, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_qiang_ling_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = 15
                    if (player === r) {
                        val seq2 = player.seq
                        builder.seq = seq2
                        GameExecutor.post(
                            player.game!!,
                            {
                                if (player.checkSeq(seq2)) {
                                    val builder2 = skill_qiang_ling_tos.newBuilder()
                                    builder2.enable = false
                                    builder2.seq = seq2
                                    player.game!!.tryContinueResolveProtocol(player, builder2.build())
                                }
                            },
                            player.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                            TimeUnit.SECONDS
                        )
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val result =
                        if (r.game!!.qiangLingTypes.isEmpty()) {
                            val cardTypes = arrayOf(Jie_Huo, Diao_Bao, Wu_Dao)
                            val index = Random.nextInt(cardTypes.size)
                            cardTypes.filterIndexed { i, _ -> i != index }
                        } else {
                            val cardTypes = arrayOf(Jie_Huo, Diao_Bao, Wu_Dao, Po_Yi)
                            cardTypes.filterNot { r.game!!.qiangLingTypes.contains(it) }
                        }
                    val builder = skill_qiang_ling_tos.newBuilder()
                    builder.enable = true
                    builder.addAllTypes(result)
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is skill_qiang_ling_tos) {
                log.error("错误的协议")
                return null
            }
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message.typesCount == 0) {
                log.error("enable为true时types不能为0")
                return null
            }
            for (t in message.typesList) {
                if (t == UNRECOGNIZED || t == null) {
                    log.error("未知的卡牌类型$t")
                    return null
                }
            }
            r.incrSeq()
            log.info("${r}发动了[强令]，禁止了${message.typesList.toTypedArray().contentToString()}")
            r.game!!.qiangLingTypes.addAll(message.typesList)
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_qiang_ling_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.addAllTypes(message.typesList)
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeQiangLing::class.java)
        }
    }

    companion object {
        fun resetQiangLing(game: Game) {
            game.qiangLingTypes.clear()
        }
    }
}