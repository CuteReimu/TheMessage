package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.count
import com.fengsheng.card.filter
import com.fengsheng.phase.OnAddMessageCard
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 玛利亚技能【藏身教堂】：当你传出的情报被接受后，若接收者是隐藏角色，则你摸一张牌（摸牌走摸牌协议），并可以将该角色翻至面朝下；若是公开角色，则可以将其一张黑色情报加入你的手牌或置入你的情报区。
 */
class CangShenJiaoTang : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.CANG_SHEN_JIAO_TANG

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        fsm.askWhom === fsm.sender || return null
        val r = fsm.sender
        r.findSkill(skillId) != null || return null
        r.getSkillUseCount(skillId) == 0 || return null
        r.addSkillUseCount(skillId)
        val target = fsm.inFrontOfWhom
        val isHiddenRole = !target.isPublicRole
        val timeoutSecond = Config.WaitSecond
        for (player in g.players) {
            if (player is HumanPlayer) {
                val builder = skill_cang_shen_jiao_tang_a_toc.newBuilder()
                builder.playerId = player.getAlternativeLocation(r.location)
                builder.targetPlayerId = player.getAlternativeLocation(target.location)
                builder.isHiddenRole = isHiddenRole
                if (isHiddenRole && target.roleFaceUp || !isHiddenRole && target.messageCards.count(Black) > 0) {
                    builder.waitingSecond = timeoutSecond
                    if (player === r) builder.seq = player.seq
                }
                player.send(builder.build())
            }
        }
        if (isHiddenRole) {
            log.info("${r}发动了[藏身教堂]")
            r.draw(1)
        }
        if (isHiddenRole && target.roleFaceUp)
            return ResolveResult(executeCangShenJiaoTangB(fsm, timeoutSecond), true)
        if (!isHiddenRole && target.messageCards.count(Black) > 0)
            return ResolveResult(executeCangShenJiaoTangC(fsm, timeoutSecond), true)
        return ResolveResult(fsm, true)
    }

    private data class executeCangShenJiaoTangB(val fsm: ReceivePhaseSkill, val timeoutSecond: Int) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.sender
            if (r is HumanPlayer) {
                val seq2 = r.seq
                r.timeout = GameExecutor.post(r.game!!, {
                    if (r.checkSeq(seq2)) {
                        val builder = skill_cang_shen_jiao_tang_b_tos.newBuilder()
                        builder.enable = false
                        builder.seq = seq2
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    }
                }, r.getWaitSeconds(timeoutSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(r.game!!, {
                    val builder = skill_cang_shen_jiao_tang_b_tos.newBuilder()
                    builder.enable = true
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.sender) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_cang_shen_jiao_tang_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            player.incrSeq()
            val target = fsm.inFrontOfWhom
            if (message.enable) player.game!!.playerSetRoleFaceUp(target, false)
            for (p in player.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_cang_shen_jiao_tang_b_toc.newBuilder()
                    builder.enable = message.enable
                    builder.playerId = p.getAlternativeLocation(player.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeCangShenJiaoTangB::class.java)
        }
    }

    private data class executeCangShenJiaoTangC(val fsm: ReceivePhaseSkill, val timeoutSecond: Int) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.sender
            if (r is HumanPlayer) {
                val seq2 = r.seq
                r.timeout = GameExecutor.post(r.game!!, {
                    if (r.checkSeq(seq2)) {
                        val builder = skill_cang_shen_jiao_tang_c_tos.newBuilder()
                        builder.enable = false
                        builder.seq = seq2
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    }
                }, r.getWaitSeconds(timeoutSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(r.game!!, {
                    val builder = skill_cang_shen_jiao_tang_c_tos.newBuilder()
                    builder.enable = true
                    builder.cardId = fsm.inFrontOfWhom.messageCards.filter(Black).random().id
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.sender) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_cang_shen_jiao_tang_c_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            val target = fsm.inFrontOfWhom
            if (message.enable) {
                val card = target.findMessageCard(message.cardId)
                if (card == null) {
                    log.error("没有这张情报")
                    (player as? HumanPlayer)?.sendErrorMessage("没有这张情报")
                    return null
                }
                if (!card.isBlack()) {
                    log.error("目标情报不是黑色的")
                    (player as? HumanPlayer)?.sendErrorMessage("目标情报不是黑色的")
                    return null
                }
                if (message.asMessageCard) {
                    if (target === player) {
                        log.error("你不能把情报从自己面前移到自己面前")
                        (player as? HumanPlayer)?.sendErrorMessage("你不能把情报从自己面前移到自己面前")
                        return null
                    }
                    log.info("${player}发动了[藏身教堂]，将${target}面前的${card}移到自己面前")
                    target.deleteMessageCard(message.cardId)
                    fsm.receiveOrder.removePlayerIfNotHaveThreeBlack(target)
                    player.messageCards.add(card)
                    fsm.receiveOrder.addPlayerIfHasThreeBlack(player)
                } else {
                    log.info("${player}发动了[藏身教堂]，将${target}面前的${card}加入了手牌")
                    target.deleteMessageCard(message.cardId)
                    fsm.receiveOrder.removePlayerIfNotHaveThreeBlack(target)
                    player.cards.add(card)
                }
            }
            player.incrSeq()
            for (p in player.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_cang_shen_jiao_tang_c_toc.newBuilder()
                    builder.enable = message.enable
                    builder.playerId = p.getAlternativeLocation(player.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.cardId = message.cardId
                    builder.asMessageCard = message.asMessageCard
                    p.send(builder.build())
                }
            }
            if (message.asMessageCard)
                return ResolveResult(OnAddMessageCard(fsm.whoseTurn, fsm), true)
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeCangShenJiaoTangC::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(CangShenJiaoTang::class.java)
    }
}