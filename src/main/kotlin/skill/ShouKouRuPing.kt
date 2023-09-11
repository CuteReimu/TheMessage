package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.DieSkill
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type.Shi_Tan
import com.fengsheng.protos.Common.card_type.Wei_Bi
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 哑炮技能【守口如瓶】：当你成为【试探】【威逼】的目标或死亡时，你自动翻开此角色，摸一张牌，然后可以交给其他角色一张牌，【试探】【威逼】无效。
 */
class ShouKouRuPing : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.SHOU_KOU_RU_PING

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm
        if (fsm is OnUseCard) {
            fsm.askWhom === fsm.targetPlayer || return null
            fsm.askWhom.alive || return null
            fsm.askWhom.findSkill(skillId) != null || return null
            fsm.cardType == Shi_Tan || fsm.cardType == Wei_Bi || return null
            fsm.askWhom.getSkillUseCount(skillId) == 0 || return null
            fsm.askWhom.addSkillUseCount(skillId)
            val r = fsm.askWhom
            log.info("${r}发动了[守口如瓶]")
            if (!r.roleFaceUp) g.playerSetRoleFaceUp(r, true)
            r.draw(1)
            val oldResolveFunc = fsm.resolveFunc
            return ResolveResult(executeShouKouRuPing(fsm.copy(resolveFunc = { valid: Boolean ->
                r.resetSkillUseCount(skillId)
                oldResolveFunc(valid)
            }), r), true)
        } else if (fsm is DieSkill) {
            fsm.askWhom == fsm.diedQueue[fsm.diedIndex] || return null
            fsm.askWhom.findSkill(skillId) != null || return null
            fsm.askWhom.getSkillUseCount(skillId) == 0 || return null
            fsm.askWhom.addSkillUseCount(skillId)
            val r = fsm.askWhom
            log.info("${r}发动了[守口如瓶]")
            if (!r.roleFaceUp) g.playerSetRoleFaceUp(r, true)
            r.draw(1)
            return ResolveResult(executeShouKouRuPing(fsm, r), true)
        }
        return null
    }

    private data class executeShouKouRuPing(val fsm: Fsm, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_shou_kou_ru_ping_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    if (fsm is OnUseCard) {
                        builder.isUseCard = true
                        builder.fromPlayerId = player.getAlternativeLocation(fsm.player.location)
                        builder.cardType = fsm.cardType
                        val card = fsm.card
                        if (card != null && card.type != Shi_Tan) builder.card = card.toPbCard()
                    } else {
                        builder.isUseCard = false
                    }
                    builder.waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq2 = player.seq
                        builder.seq = seq2
                        player.timeout = GameExecutor.post(r.game!!, {
                            if (player.checkSeq(seq2)) {
                                val builder2 = skill_shou_kou_ru_ping_tos.newBuilder()
                                builder2.enable = false
                                builder2.seq = seq2
                                r.game!!.tryContinueResolveProtocol(r, builder2.build())
                            }
                        }, player.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val builder2 = skill_shou_kou_ru_ping_tos.newBuilder()
                    val target = r.game!!.players.filter { it !== r && it!!.alive }.randomOrNull()
                    if (target != null) {
                        builder2.enable = true
                        builder2.targetPlayerId = r.getAlternativeLocation(target.location)
                        builder2.cardId = r.cards.random().id
                    } else {
                        builder2.enable = false
                    }
                    r.game!!.tryContinueResolveProtocol(r, builder2.build())
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
            if (message !is skill_shou_kou_ru_ping_tos) {
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
                    if (p is HumanPlayer) {
                        val builder = skill_shou_kou_ru_ping_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(r.location)
                        builder.enable = false
                        p.send(builder.build())
                    }
                }
                return ResolveResult(fsm, true)
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("错误的目标")
                (player as? HumanPlayer)?.sendErrorMessage("错误的目标")
                return null
            }
            if (message.targetPlayerId == 0) {
                log.error("不能给自己")
                (player as? HumanPlayer)?.sendErrorMessage("不能给自己")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            val card = r.deleteCard(message.cardId)
            if (card == null) {
                log.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            r.incrSeq()
            log.info("${r}给了${target}一张$card")
            target.cards.add(card)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_shou_kou_ru_ping_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.enable = true
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target) builder.giveCard = card.toPbCard()
                    if (fsm is OnUseCard) {
                        builder.isUseCard = true
                        builder.fromPlayerId = p.getAlternativeLocation(fsm.player.location)
                        val card2 = fsm.card
                        if (card2 != null && card2.type != Shi_Tan) builder.card = card2.toPbCard()
                    } else {
                        builder.isUseCard = false
                    }
                    p.send(builder.build())
                }
            }
            if (fsm is OnUseCard)
                return ResolveResult(fsm.copy(valid = false), true)
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeShouKouRuPing::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(ShouKouRuPing::class.java)
    }
}