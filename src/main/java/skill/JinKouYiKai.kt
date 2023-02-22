package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.FightPhaseIdleimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Roleimport com.fengsheng.protos.Role.skill_jin_kou_yi_kai_a_tocimport com.fengsheng.skill.JinKouYiKai.executeJinKouYiKaiimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 玄青子技能【金口一开】：你的回合的争夺阶段限一次，你可以查看牌堆顶的一张牌，然后选择一项：
 *  * 你摸一张牌。 * 将牌堆顶的一张牌和待接收情报面朝下互换
 */
class JinKouYiKai : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.JIN_KOU_YI_KAI
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn || r !== fsm.whoseTurn) {
            log.error("现在不是发动[金口一开]的时机")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[金口一开]一回合只能发动一次")
            return
        }
        val pb = message as Role.skill_jin_kou_yi_kai_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        val cards = g.deck.peek(1)
        if (cards.isEmpty()) {
            log.error("牌堆没牌了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.resolve(executeJinKouYiKai(fsm, r, cards))
    }

    private class executeJinKouYiKai(fsm: FightPhaseIdle, r: Player, cards: MutableList<Card>) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game
            log.info(r.toString() + "发动了[金口一开]")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jin_kou_yi_kai_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.waitingSecond = 15
                    if (p === r) {
                        builder.card = cards[0].toPbCard()
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(GameExecutor.Companion.post(g, Runnable {
                            if (p.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(
                                    r, Role.skill_jin_kou_yi_kai_b_tos.newBuilder()
                                        .setExchange(false).setSeq(seq2).build()
                                )
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    }
                    p.send(builder.build())
                }
            }
            fsm.whoseFightTurn = fsm.inFrontOfWhom
            if (r is RobotPlayer) {
                GameExecutor.Companion.post(g, Runnable {
                    g.tryContinueResolveProtocol(
                        r, Role.skill_jin_kou_yi_kai_b_tos.newBuilder()
                            .setExchange(ThreadLocalRandom.current().nextBoolean()).build()
                    )
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is Role.skill_jin_kou_yi_kai_b_tos) {
                log.error("错误的协议")
                return null
            }
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            r.incrSeq()
            for (p in g.players) {
                (p as? HumanPlayer)?.send(
                    Role.skill_jin_kou_yi_kai_b_toc.newBuilder().setExchange(message.exchange)
                        .setPlayerId(p.getAlternativeLocation(r.location())).build()
                )
            }
            if (message.exchange) {
                val temp = cards[0]
                log.info(r.toString() + "将待接收情报" + fsm.messageCard + "与牌堆顶的" + temp + "互换")
                cards[0] = fsm.messageCard
                fsm.messageCard = temp
                fsm.isMessageCardFaceUp = false
            } else {
                r.draw(1)
            }
            return ResolveResult(fsm, true)
        }

        val fsm: FightPhaseIdle
        val r: Player
        val cards: MutableList<Card>

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
            this.fsm = fsm
            this.fsm = fsm
            this.target = target
            this.fsm = fsm
            this.r = r
            this.target = target
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.color = color
            this.fsm = fsm
            this.color = color
            this.fsm = fsm
            this.r = r
            this.cards = cards
        }

        companion object {
            private val log = Logger.getLogger(executeJinKouYiKai::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(JinKouYiKai::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player !== e.whoseTurn || player.getSkillUseCount(SkillId.JIN_KOU_YI_KAI) > 0) return false
            GameExecutor.Companion.post(player.game, Runnable {
                skill.executeProtocol(
                    player.game, player, Role.skill_jin_kou_yi_kai_a_tos.getDefaultInstance()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}