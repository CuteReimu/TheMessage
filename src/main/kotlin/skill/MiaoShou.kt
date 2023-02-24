package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.FightPhaseIdleimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Roleimport com.fengsheng.protos.Role.skill_miao_shou_a_tocimport com.fengsheng.protos.Role.skill_miao_shou_b_tocimport com.fengsheng.skill.MiaoShou.executeMiaoShouimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 阿芙罗拉技能【妙手】：争夺阶段，你可以翻开此角色牌，然后弃置待接收情报，并查看一名角色的手牌和情报区，从中选择一张牌作为待收情报，面朝上移至一名角色的面前。
 */
class MiaoShou : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.MIAO_SHOU
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn) {
            log.error("现在不是发动[妙手]的时机")
            return
        }
        if (r.isRoleFaceUp) {
            log.error("你现在正面朝上，不能发动[妙手]")
            return
        }
        val pb = message as Role.skill_miao_shou_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]
        if (!target.isAlive) {
            log.error("目标已死亡")
            return
        }
        if (target.cards.isEmpty() && target.messageCards.isEmpty()) {
            log.error("目标没有手牌，也没有情报牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        g.deck.discard(fsm.messageCard)
        g.resolve(executeMiaoShou(fsm, r, target))
    }

    private class executeMiaoShou(fsm: FightPhaseIdle, r: Player, target: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game
            log.info(r.toString() + "对" + target + "发动了[妙手]")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_miao_shou_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    builder.waitingSecond = 20
                    builder.messageCard = fsm.messageCard.toPbCard()
                    if (p === r) {
                        for (card in target.cards.values) builder.addCards(card.toPbCard())
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(GameExecutor.Companion.post(g, Runnable {
                            if (p.checkSeq(seq2)) {
                                for (card in target.cards.values) {
                                    g.tryContinueResolveProtocol(
                                        r, Role.skill_miao_shou_b_tos.newBuilder()
                                            .setCardId(card.id).setTargetPlayerId(0).setSeq(seq2).build()
                                    )
                                    return@post
                                }
                                for (card in target.messageCards.values) {
                                    g.tryContinueResolveProtocol(
                                        r, Role.skill_miao_shou_b_tos.newBuilder()
                                            .setMessageCardId(card.id).setTargetPlayerId(0).setSeq(seq2).build()
                                    )
                                    return@post
                                }
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.Companion.post(g, Runnable {
                    for (card in target.cards.values) {
                        g.tryContinueResolveProtocol(
                            r, Role.skill_miao_shou_b_tos.newBuilder()
                                .setCardId(card.id).setTargetPlayerId(0).build()
                        )
                        return@post
                    }
                    for (card in target.messageCards.values) {
                        g.tryContinueResolveProtocol(
                            r, Role.skill_miao_shou_b_tos.newBuilder()
                                .setMessageCardId(card.id).setTargetPlayerId(0).build()
                        )
                        return@post
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
            if (message !is Role.skill_miao_shou_b_tos) {
                log.error("错误的协议")
                return null
            }
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            if (message.cardId != 0 && message.messageCardId != 0) {
                log.error("只能选择手牌或情报其中之一")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                return null
            }
            val target2 = g.players[r.getAbstractLocation(message.targetPlayerId)]
            if (!target2.isAlive) {
                log.error("目标已死亡")
                return null
            }
            val card: Card?
            if (message.cardId == 0 && message.messageCardId == 0) {
                log.error("必须选择一张手牌或情报")
                return null
            } else if (message.messageCardId == 0) {
                card = target.deleteCard(message.cardId)
                if (card == null) {
                    log.error("没有这张牌")
                    return null
                }
            } else {
                card = target.deleteMessageCard(message.messageCardId)
                if (card == null) {
                    log.error("没有这张牌")
                    return null
                }
            }
            r.incrSeq()
            log.info(r.toString() + "将" + card + "作为情报，面朝上移至" + target2 + "的面前")
            fsm.messageCard = card
            fsm.inFrontOfWhom = target2
            fsm.whoseFightTurn = fsm.inFrontOfWhom
            fsm.isMessageCardFaceUp = true
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_miao_shou_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.fromPlayerId = p.getAlternativeLocation(target.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target2.location())
                    if (message.cardId != 0) builder.card = card.toPbCard() else builder.messageCardId = card.id
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        val fsm: FightPhaseIdle
        val r: Player
        val target: Player

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
        }

        companion object {
            private val log = Logger.getLogger(executeMiaoShou::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(MiaoShou::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.isRoleFaceUp) return false
            val players: MutableList<Player> = ArrayList()
            for (p in player.game.players) {
                if (p.isAlive && (!p.cards.isEmpty() || !p.messageCards.isEmpty())) {
                    players.add(p)
                }
            }
            if (players.isEmpty()) return false
            if (ThreadLocalRandom.current().nextInt(players.size) != 0) return false
            val p = players[ThreadLocalRandom.current().nextInt(players.size)]
            GameExecutor.Companion.post(player.game, Runnable {
                skill.executeProtocol(
                    player.game, player, Role.skill_miao_shou_a_tos.newBuilder().setTargetPlayerId(p.location()).build()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}