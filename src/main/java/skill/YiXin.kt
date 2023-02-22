package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.DieSkillimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Role.skill_wait_for_yi_xin_tocimport com.fengsheng.skill.YiXin.executeYiXin com.fengsheng.protos.Roleimport com.google.protobuf.GeneratedMessageV3
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 李宁玉技能【遗信】：你死亡前，可以将一张手牌置入另一名角色的情报区。
 */
class YiXin : AbstractSkill(), TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.YI_XIN
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm !is DieSkill || fsm.askWhom != fsm.diedQueue.get(fsm.diedIndex) || fsm.askWhom.findSkill<Skill>(
                skillId
            ) == null
        ) return null
        if (!fsm.askWhom.isRoleFaceUp()) return null
        if (fsm.askWhom.getCards().isEmpty()) return null
        if (fsm.askWhom.getSkillUseCount(skillId) > 0) return null
        fsm.askWhom.addSkillUseCount(skillId)
        return ResolveResult(executeYiXin(fsm), true)
    }

    private class executeYiXin(fsm: DieSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.askWhom
            for (player in r!!.game.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_yi_xin_toc.newBuilder()
                    builder.setPlayerId(player.getAlternativeLocation(r.location())).waitingSecond = 15
                    if (player == r) {
                        val seq2: Int = player.seq
                        builder.seq = seq2
                        GameExecutor.Companion.post(
                            r.game,
                            Runnable {
                                r.game.tryContinueResolveProtocol(
                                    r,
                                    Role.skill_yi_xin_tos.newBuilder().setEnable(false).setSeq(seq2).build()
                                )
                            },
                            player.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                            TimeUnit.SECONDS
                        )
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                if (fsm.whoseTurn.isAlive && r !== fsm.whoseTurn) {
                    for (card in r.getCards().values) {
                        GameExecutor.Companion.post(r.getGame(), Runnable {
                            r.getGame().tryContinueResolveProtocol(
                                r, Role.skill_yi_xin_tos.newBuilder().setEnable(true).setCardId(card.id)
                                    .setTargetPlayerId(r.getAlternativeLocation(fsm.whoseTurn.location())).build()
                            )
                        }, 2, TimeUnit.SECONDS)
                        return null
                    }
                }
                GameExecutor.Companion.post(
                    r.getGame(),
                    Runnable {
                        r.getGame()
                            .tryContinueResolveProtocol(r, Role.skill_yi_xin_tos.newBuilder().setEnable(false).build())
                    },
                    2,
                    TimeUnit.SECONDS
                )
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.askWhom) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is Role.skill_yi_xin_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.askWhom
            val g = r!!.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                for (p in g.players) {
                    (p as? HumanPlayer)?.send(Role.skill_yi_xin_toc.newBuilder().setEnable(false).build())
                }
                return ResolveResult(fsm, true)
            }
            val card = r.findCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                return null
            }
            if (message.targetPlayerId == 0) {
                log.error("不能以自己为目标")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]
            if (!target.isAlive) {
                log.error("目标已死亡")
                return null
            }
            r.incrSeq()
            log.info(r.toString() + "发动了[遗信]")
            r.deleteCard(card.id)
            target.addMessageCard(card)
            fsm.receiveOrder.addPlayerIfHasThreeBlack(target)
            log.info(r.toString() + "将" + card + "放置在" + target + "面前")
            for (p in g.players) {
                (p as? HumanPlayer)?.send(
                    Role.skill_yi_xin_toc.newBuilder().setEnable(true).setCard(card.toPbCard())
                        .setPlayerId(p.getAlternativeLocation(r.location()))
                        .setTargetPlayerId(p.getAlternativeLocation(target.location())).build()
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
        }

        companion object {
            private val log = Logger.getLogger(executeYiXin::class.java)
        }
    }
}