package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.DieSkillimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Role.skill_wait_for_cheng_zhi_tocimport com.fengsheng.skill.ChengZhi.executeChengZhiimport java.util.concurrent.* com.fengsheng.protos.Roleimport com.google.protobuf.GeneratedMessageV3
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Loggerimport java.util.*import java.util.concurrent.*

/**
 * 顾小梦技能【承志】：一名其他角色死亡前，若此角色牌已翻开，则你获得其所有手牌，并查看其身份牌，你可以获得该身份牌，并将你原本的身份牌面朝下移出游戏。
 */
class ChengZhi : AbstractSkill(), TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.CHENG_ZHI
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm !is DieSkill || fsm.askWhom === fsm.diedQueue.get(fsm.diedIndex) || fsm.askWhom.findSkill<Skill>(
                skillId
            ) == null
        ) return null
        if (!fsm.askWhom.isAlive()) return null
        if (!fsm.askWhom.isRoleFaceUp()) return null
        if (fsm.askWhom.getSkillUseCount(skillId) > 0) return null
        fsm.askWhom.addSkillUseCount(skillId)
        return ResolveResult(executeChengZhi(fsm), true)
    }

    private class executeChengZhi(fsm: DieSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.askWhom
            val whoDie = fsm.diedQueue[fsm.diedIndex]
            val cards = whoDie!!.deleteAllCards()
            r!!.addCard(*cards)
            log.info(r.toString() + "发动了[承志]，获得了" + whoDie + "的" + Arrays.toString(cards))
            if (whoDie.identity == color.Has_No_Identity) return ResolveResult(fsm, true)
            for (player in r.game.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_cheng_zhi_toc.newBuilder()
                    builder.setPlayerId(player.getAlternativeLocation(r.location())).waitingSecond = 15
                    builder.diePlayerId = player.getAlternativeLocation(whoDie.location())
                    if (player === r) {
                        for (card in cards) builder.addCards(card.toPbCard())
                        builder.identity = whoDie.identity
                        builder.secretTask = whoDie.secretTask
                        val seq2: Int = player.seq
                        builder.seq = seq2
                        player.setTimeout(
                            GameExecutor.Companion.post(
                                r.getGame(),
                                Runnable {
                                    r.getGame().tryContinueResolveProtocol(
                                        r,
                                        Role.skill_cheng_zhi_tos.newBuilder().setEnable(false).setSeq(seq2).build()
                                    )
                                },
                                player.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                                TimeUnit.SECONDS
                            )
                        )
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) GameExecutor.Companion.post(
                r.getGame(),
                Runnable {
                    r.getGame()
                        .tryContinueResolveProtocol(r, Role.skill_cheng_zhi_tos.newBuilder().setEnable(true).build())
                },
                2,
                TimeUnit.SECONDS
            )
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.askWhom) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is Role.skill_cheng_zhi_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.askWhom
            val whoDie = fsm.diedQueue[fsm.diedIndex]
            val g = r!!.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                for (p in g.players) {
                    (p as? HumanPlayer)?.send(
                        Role.skill_cheng_zhi_toc.newBuilder().setEnable(false).setPlayerId(
                            p.getAlternativeLocation(
                                r.location()
                            )
                        )
                            .setDiePlayerId(p.getAlternativeLocation(whoDie!!.location())).build()
                    )
                }
                return ResolveResult(fsm, true)
            }
            r.incrSeq()
            r.identity = whoDie!!.identity
            r.secretTask = whoDie.secretTask
            whoDie.identity = color.Has_No_Identity
            log.info(r.toString() + "获得了" + whoDie + "的身份牌")
            for (p in g.players) {
                (p as? HumanPlayer)?.send(
                    Role.skill_cheng_zhi_toc.newBuilder().setEnable(true).setPlayerId(
                        p.getAlternativeLocation(
                            r.location()
                        )
                    )
                        .setDiePlayerId(p.getAlternativeLocation(whoDie.location())).build()
                )
            }
            return ResolveResult(fsm, true)
        }

        val fsm: DieSkill

        init {
            this.card = card
            this.sendPhase = sendPhase
            this.r = r
            this.target = target
            this.card = card
            this.wantType = wantType
            this.r = r
            this.target = target
            this.card = card
            this.player = player
            this.card = card
            this.card = card
            this.drawCards = drawCards
            this.players = players
            this.mainPhaseIdle = mainPhaseIdle
            this.dieSkill = dieSkill
            this.player = player
            this.player = player
            this.onUseCard = onUseCard
            this.game = game
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.dir = dir
            this.targetPlayer = targetPlayer
            this.lockedPlayers = lockedPlayers
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.inFrontOfWhom = inFrontOfWhom
            this.player = player
            this.whoseTurn = whoseTurn
            this.diedQueue = diedQueue
            this.afterDieResolve = afterDieResolve
            this.fightPhase = fightPhase
            this.player = player
            this.sendPhase = sendPhase
            this.dieGiveCard = dieGiveCard
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.inFrontOfWhom = inFrontOfWhom
            this.isMessageCardFaceUp = isMessageCardFaceUp
            this.waitForChengQing = waitForChengQing
            this.waitForChengQing = waitForChengQing
            this.whoseTurn = whoseTurn
            this.dyingQueue = dyingQueue
            this.diedQueue = diedQueue
            this.afterDieResolve = afterDieResolve
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.receiveOrder = receiveOrder
            this.inFrontOfWhom = inFrontOfWhom
            this.r = r
            this.fsm = fsm
            this.r = r
            this.playerAndCards = playerAndCards
            this.fsm = fsm
            this.selection = selection
            this.fromPlayer = fromPlayer
            this.waitingPlayer = waitingPlayer
            this.card = card
            this.r = r
            this.r = r
            this.target = target
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.target = target
            this.fsm = fsm
            this.fsm = fsm
            this.target = target
            this.needReturnCount = needReturnCount
            this.fsm = fsm
            this.fsm = fsm
            this.cards = cards
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
        }

        companion object {
            private val log = Logger.getLogger(executeChengZhi::class.java)
        }
    }
}