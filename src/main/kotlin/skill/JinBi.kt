package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.MainPhaseIdleimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Roleimport com.fengsheng.protos.Role.skill_jin_bi_a_tocimport com.fengsheng.protos.Role.skill_jin_bi_b_tocimport com.fengsheng.skill.JinBi.executeJinBiimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.*import java.util.concurrent.*

/**
 * 王田香技能【禁闭】：出牌阶段限一次，你可以指定一名角色，除非其交给你两张手牌，否则其本回合不能使用手牌，且所有角色技能无效。
 */
class JinBi : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.JIN_BI
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is MainPhaseIdle || r !== fsm.player) {
            log.error("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[禁闭]一回合只能发动一次")
            return
        }
        val pb = message as Role.skill_jin_bi_a_tos
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
        r.incrSeq()
        r.addSkillUseCount(skillId)
        log.info(r.toString() + "对" + target + "发动了[禁闭]")
        g.resolve(executeJinBi(r, target))
    }

    private class executeJinBi(r: Player, target: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            if (target.cards.size < 2) {
                doExecuteJinBi()
                return ResolveResult(MainPhaseIdle(r), true)
            }
            for (p in r.game.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jin_bi_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    builder.waitingSecond = 15
                    if (p === target) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        GameExecutor.Companion.post(
                            p.getGame(),
                            Runnable {
                                if (p.checkSeq(seq2)) p.getGame().tryContinueResolveProtocol(
                                    p,
                                    Role.skill_jin_bi_b_tos.newBuilder().setSeq(seq2).build()
                                )
                            },
                            p.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                            TimeUnit.SECONDS
                        )
                    }
                    p.send(builder.build())
                }
            }
            if (target is RobotPlayer) GameExecutor.Companion.post(
                target.getGame(),
                Runnable {
                    target.getGame().tryContinueResolveProtocol(target, Role.skill_jin_bi_b_tos.getDefaultInstance())
                },
                2,
                TimeUnit.SECONDS
            )
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== target) {
                log.error("你不是被禁闭的目标")
                return null
            }
            if (message !is Role.skill_jin_bi_b_tos) {
                log.error("错误的协议")
                return null
            }
            val g = target.game
            if (target is HumanPlayer && !target.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + target.seq + ", actual Seq: " + message.seq)
                return null
            }
            if (message.cardIdsCount == 0) {
                r.incrSeq()
                doExecuteJinBi()
                return ResolveResult(MainPhaseIdle(r), true)
            } else if (message.cardIdsCount != 2) {
                log.error("给的牌数量不对：" + message.cardIdsCount)
                return null
            }
            val cards = arrayOfNulls<Card>(2)
            for (i in cards.indices) {
                val card = target.findCard(message.getCardIds(i))
                if (card == null) {
                    log.error("没有这张牌")
                    return null
                }
                cards[i] = card
            }
            for (card in cards) target.deleteCard(card.getId())
            r.addCard(*cards)
            log.info(target.toString() + "给了" + r + Arrays.toString(cards))
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jin_bi_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    if (p === r || p === target) {
                        for (card in cards) builder.addCards(card!!.toPbCard())
                    } else {
                        builder.unknownCardCount = 2
                    }
                    p.send(builder.build())
                }
            }
            return ResolveResult(MainPhaseIdle(r), true)
        }

        private fun doExecuteJinBi() {
            log.info(target.toString() + "进入了[禁闭]状态")
            val g = r.game
            g.jinBiPlayer = target
            val skills = target.skills
            for (i in skills.indices) skills[i] = JinBiSkill(skills[i])
            for (p in g.players) {
                (p as? HumanPlayer)?.send(
                    skill_jin_bi_b_toc.newBuilder()
                        .setPlayerId(p.getAlternativeLocation(r.location()))
                        .setTargetPlayerId(p.getAlternativeLocation(target.location())).build()
                )
            }
        }

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
        }

        companion object {
            private val log = Logger.getLogger(executeJinBi::class.java)
        }
    }

    private class JinBiSkill(val originSkill: Skill) : AbstractSkill() {
        override fun getSkillId(): SkillId? {
            return SkillId.BEI_JIN_BI
        }
    }

    companion object {
        private val log = Logger.getLogger(JinBi::class.java)
        fun resetJinBi(game: Game) {
            val player = game.jinBiPlayer
            if (player != null) {
                val skills = player.skills
                for (i in skills.indices) {
                    if (skills[i] is JinBiSkill) skills[i] = (skills[i] as JinBiSkill).originSkill
                }
                game.jinBiPlayer = null
            }
        }

        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.player.getSkillUseCount(SkillId.JIN_BI) > 0) return false
            val players: MutableList<Player> = ArrayList()
            for (p in e.player.game.players) {
                if (p !== e.player && p.isAlive) players.add(p)
            }
            if (players.isEmpty()) return false
            val player = players[ThreadLocalRandom.current().nextInt(players.size)]
            GameExecutor.Companion.post(e.player.game, Runnable {
                skill.executeProtocol(
                    e.player.game,
                    e.player,
                    Role.skill_jin_bi_a_tos.newBuilder()
                        .setTargetPlayerId(e.player.getAlternativeLocation(player.location())).build()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}