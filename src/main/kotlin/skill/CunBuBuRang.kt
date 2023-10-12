package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 凌素秋技能【寸步不让】：在其他角色获得你的手牌结算之后，你可以抽该角色一张手牌。你在回合外弃置手牌后，可以摸一张牌。
 */
class CunBuBuRang : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.CUN_BU_BU_RANG

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event1 = g.findEvent<DiscardCardEvent>(this) { event ->
            askWhom === event.player || return@findEvent false
            askWhom !== event.whoseTurn
        }
        if (event1 != null) {
            log.info("${askWhom}发动了[寸步不让]")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_cun_bu_bu_rang_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(askWhom.location)
                    builder.enable = true
                    builder.isDrawCard = true
                    p.send(builder.build())
                }
            }
            askWhom.draw(1)
            return null
        }
        val event2 = g.findEvent<GiveCardEvent>(this) { event ->
            askWhom === event.fromPlayer || return@findEvent false
            askWhom !== event.toPlayer || return@findEvent false
            event.toPlayer.alive || return@findEvent false
            event.toPlayer.cards.isNotEmpty()
        }
        if (event2 != null)
            return ResolveResult(executeCunBuBuRang(g.fsm!!, event2.whoseTurn, askWhom, event2.toPlayer), true)
        return null
    }

    private data class executeCunBuBuRang(
        val fsm: Fsm,
        val whoseTurn: Player,
        val r: Player,
        val target: Player
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_cun_bu_bu_rang_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.targetPlayerId = player.getAlternativeLocation(target.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq = player.seq
                        builder.seq = seq
                        player.timeout = GameExecutor.post(r.game!!, {
                            val builder2 = skill_cun_bu_bu_rang_tos.newBuilder()
                            builder2.enable = true
                            builder2.seq = seq
                            r.game!!.tryContinueResolveProtocol(r, builder2.build())
                        }, player.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        // 晚一秒提示凌素秋，以防客户端动画bug
                        GameExecutor.post(r.game!!, { player.send(builder.build()) }, 1, TimeUnit.SECONDS)
                    } else {
                        player.send(builder.build())
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val builder = skill_cun_bu_bu_rang_tos.newBuilder()
                    builder.enable = true
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
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
            if (message !is skill_cun_bu_bu_rang_tos) {
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
                    (p as? HumanPlayer)?.send(skill_cun_bu_bu_rang_toc.newBuilder().setEnable(false).build())
                }
                return ResolveResult(fsm, true)
            }
            val card = target.cards.random()
            r.incrSeq()
            log.info("${r}对${target}发动了[寸步不让]，抽取了$card")
            target.deleteCard(card.id)
            r.cards.add(card)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_cun_bu_bu_rang_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.enable = true
                    builder.isDrawCard = false
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target) builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            g.addEvent(GiveCardEvent(whoseTurn, target, r))
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeCunBuBuRang::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(CunBuBuRang::class.java)
    }
}