package com.fengsheng.skill

import com.fengsheng.*

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Roleimport com.fengsheng.protos.Role.skill_bo_ai_a_tocimport com.fengsheng.protos.Role.skill_bo_ai_b_tocimport com.fengsheng.skill.BoAi.executeBoAiimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 白沧浪技能【博爱】：出牌阶段限一次，你可以摸一张牌，然后可以将一张手牌交给另一名角色，若交给了女性角色，则你再摸一张牌。
 */
class BoAi : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.BO_AI
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is MainPhaseIdle || r !== fsm.player) {
            log.error("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[博爱]一回合只能发动一次")
            return
        }
        val pb = message as Role.skill_bo_ai_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.resolve(executeBoAi(r))
    }

    private class executeBoAi(r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game
            log.info(r.toString() + "发动了[博爱]")
            r.draw(1)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_bo_ai_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(GameExecutor.Companion.post(g, Runnable {
                            if (p.checkSeq(seq2)) {
                                r.getGame().tryContinueResolveProtocol(
                                    r,
                                    Role.skill_bo_ai_b_tos.newBuilder().setCardId(0).setSeq(seq2).build()
                                )
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.Companion.post(g, Runnable {
                    val players: MutableList<Player> = ArrayList()
                    for (player in r.getGame().players) {
                        if (player.isAlive && player !== r && player.isFemale) players.add(player)
                    }
                    if (players.isEmpty()) {
                        r.getGame()
                            .tryContinueResolveProtocol(r, Role.skill_bo_ai_b_tos.newBuilder().setCardId(0).build())
                        return@post
                    }
                    val player = players[ThreadLocalRandom.current().nextInt(players.size)]
                    for (card in r.getCards().values) {
                        r.getGame().tryContinueResolveProtocol(
                            r, Role.skill_bo_ai_b_tos.newBuilder().setCardId(card.id)
                                .setTargetPlayerId(r.getAlternativeLocation(player.location())).build()
                        )
                        break
                    }
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is Role.skill_bo_ai_b_tos) {
                log.error("错误的协议")
                return null
            }
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            if (message.cardId == 0) {
                r.incrSeq()
                return ResolveResult(MainPhaseIdle(r), true)
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
            val card = r.findCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                return null
            }
            r.incrSeq()
            log.info(r.toString() + "将" + card + "交给" + target)
            r.deleteCard(card.id)
            target.addCard(card)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_bo_ai_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    if (p === r || p === target) builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            if (target.isFemale) r.draw(1)
            return ResolveResult(MainPhaseIdle(r), true)
        }

        val r: Player

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
        }

        companion object {
            private val log = Logger.getLogger(executeBoAi::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(BoAi::class.java)
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.player.getSkillUseCount(SkillId.BO_AI) > 0) return false
            GameExecutor.Companion.post(e.player.game, Runnable {
                skill.executeProtocol(
                    e.player.game, e.player, Role.skill_bo_ai_a_tos.getDefaultInstance()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}