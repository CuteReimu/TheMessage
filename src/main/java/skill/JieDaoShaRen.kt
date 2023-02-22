package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.CheckWin
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role
import com.fengsheng.protos.Role.skill_jie_dao_sha_ren_a_toc
import com.fengsheng.skill.JieDaoShaRen.executeJieDaoShaRen
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.*

/**
 * 商玉技能【借刀杀人】：争夺阶段，你可以翻开此角色牌，然后抽取另一名角色的一张手牌并展示之。若展示的牌是：**黑色**，则你可以将其置入一名角色的情报区，并将你的角色牌翻至面朝下。**非黑色**，则你摸一张牌。
 */
class JieDaoShaRen : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.JIE_DAO_SHA_REN
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn) {
            log.error("现在不是发动[借刀杀人]的时机")
            return
        }
        if (r.isRoleFaceUp) {
            log.error("你现在正面朝上，不能发动[借刀杀人]")
            return
        }
        val pb = message as Role.skill_jie_dao_sha_ren_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            return
        }
        if (pb.targetPlayerId == 0) {
            log.error("不能以自己为目标")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]
        if (!target.isAlive) {
            log.error("目标已死亡")
            return
        }
        if (target.cards.isEmpty()) {
            log.error("目标没有手牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        val cards = target.cards.values.toTypedArray()
        val card = cards[ThreadLocalRandom.current().nextInt(cards.size)]
        g.resolve(executeJieDaoShaRen(fsm, r, target, card))
    }

    private class executeJieDaoShaRen(fsm: FightPhaseIdle, r: Player, target: Player, val card: Card) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game
            target.deleteCard(card.id)
            r.addCard(card)
            log.info(r.toString() + "对" + target + "发动了[借刀杀人]，抽取了一张手牌" + card)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jie_dao_sha_ren_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    builder.card = card.toPbCard()
                    if (card.colors.contains(color.Black)) {
                        builder.waitingSecond = 15
                        if (p === r) {
                            val seq2: Int = p.seq
                            builder.seq = seq2
                            p.setTimeout(GameExecutor.Companion.post(g, Runnable {
                                if (p.checkSeq(seq2)) {
                                    g.tryContinueResolveProtocol(
                                        r, Role.skill_jie_dao_sha_ren_b_tos.newBuilder()
                                            .setEnable(false).setSeq(seq2).build()
                                    )
                                }
                            }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                        }
                    }
                    p.send(builder.build())
                }
            }
            fsm.whoseFightTurn = fsm.inFrontOfWhom
            if (!card.colors.contains(color.Black)) {
                r.draw(1)
                return ResolveResult(fsm, true)
            }
            if (r is RobotPlayer) {
                GameExecutor.Companion.post(g, Runnable {
                    val players: MutableList<Player> = ArrayList()
                    for (p in g.players) if (p !== r && p.isAlive) players.add(p)
                    if (players.isEmpty()) {
                        g.tryContinueResolveProtocol(
                            r,
                            Role.skill_jie_dao_sha_ren_b_tos.newBuilder().setEnable(false).build()
                        )
                    } else {
                        val target2 = players[ThreadLocalRandom.current().nextInt(players.size)]
                        g.tryContinueResolveProtocol(
                            r, Role.skill_jie_dao_sha_ren_b_tos.newBuilder().setEnable(true)
                                .setTargetPlayerId(r.getAlternativeLocation(target2.location())).build()
                        )
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
            if (message !is Role.skill_jie_dao_sha_ren_b_tos) {
                log.error("错误的协议")
                return null
            }
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]
            if (!target.isAlive) {
                log.error("目标已死亡")
                return null
            }
            r.incrSeq()
            log.info(r.toString() + "将" + card + "置于" + target + "的情报区")
            r.deleteCard(card.id)
            target.addMessageCard(card)
            val newFsm = CheckWin(fsm.whoseTurn, fsm)
            newFsm.receiveOrder.addPlayerIfHasThreeBlack(target)
            for (p in g.players) {
                (p as? HumanPlayer)?.send(
                    Role.skill_jie_dao_sha_ren_b_toc.newBuilder().setCard(card.toPbCard())
                        .setPlayerId(p.getAlternativeLocation(r.location()))
                        .setTargetPlayerId(p.getAlternativeLocation(target.location())).build()
                )
            }
            g.playerSetRoleFaceUp(r, false)
            return ResolveResult(newFsm, true)
        }

        val fsm: FightPhaseIdle
        val r: Player
        val target: Player

        init {
            this.sendPhase = sendPhase
            this.r = r
            this.target = target
            card = card
            this.wantType = wantType
            this.r = r
            this.target = target
            card = card
            this.player = player
            card = card
            card = card
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
            card = card
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
            this.fsm = fsm
            this.r = r
            this.target = target
            card = card
        }

        companion object {
            private val log = Logger.getLogger(executeJieDaoShaRen::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(JieDaoShaRen::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.isRoleFaceUp) return false
            val players: MutableList<Player> = ArrayList()
            for (p in player.game.players) {
                if (p !== player && p.isAlive && !p.cards.isEmpty()) {
                    var notBlack = false
                    for (card in p.cards.values) {
                        if (!card.colors.contains(color.Black)) {
                            notBlack = true
                            break
                        }
                    }
                    if (!notBlack) players.add(p)
                }
            }
            if (players.isEmpty()) return false
            val target = players[ThreadLocalRandom.current().nextInt(players.size)]
            GameExecutor.Companion.post(player.game, Runnable {
                skill.executeProtocol(
                    player.game, player, Role.skill_jie_dao_sha_ren_a_tos.newBuilder()
                        .setTargetPlayerId(player.getAlternativeLocation(target.location())).build()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}