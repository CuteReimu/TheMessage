package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.OnSendCardimport

com.fengsheng.phase.ReceivePhaseSenderSkillimport com.fengsheng.protos.Commonimport com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Fengshengimport com.fengsheng.protos.Roleimport com.fengsheng.protos.Role.skill_wait_for_jiang_hu_ling_a_tocimport com.fengsheng.protos.Role.skill_wait_for_jiang_hu_ling_b_tocimport com.fengsheng.skill.JiangHuLing.executeJiangHuLingAimport com.fengsheng.skill.JiangHuLing.executeJiangHuLingBimport com.google.protobuf.GeneratedMessageV3import io.netty.util.Timeoutimport io.netty.util.TimerTaskimport org.apache.log4j.Loggerimport java.util.Listimport java.util.concurrent.*
/**
 * 王富贵技能【江湖令】：你传出情报后，可以宣言一个颜色。本回合中，当情报被接收后，你可以从接收者的情报区弃置一张被宣言颜色的情报，若弃置的是黑色情报，则你摸一张牌。
 */
class JiangHuLing : TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.JIANG_HU_LING
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm is OnSendCard) {
            val r: Player = fsm.whoseTurn
            if (r.findSkill<Skill?>(skillId) == null) return null
            if (r.getSkillUseCount(skillId) >= 1) return null
            r.addSkillUseCount(skillId)
            return ResolveResult(executeJiangHuLingA(fsm, r), true)
        }
        return null
    }

    private class executeJiangHuLingA(fsm: Fsm, r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_jiang_hu_ling_a_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location())
                    builder.waitingSecond = 15
                    if (player === r) {
                        val seq2: Int = player.seq
                        builder.seq = seq2
                        GameExecutor.Companion.post(
                            player.getGame(),
                            Runnable {
                                if (player.checkSeq(seq2)) player.getGame().tryContinueResolveProtocol(
                                    player,
                                    Role.skill_jiang_hu_ling_a_tos.newBuilder().setEnable(false).setSeq(seq2).build()
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
                GameExecutor.Companion.post(r.getGame(), Runnable {
                    val colors = List.of(color.Black, color.Red, color.Blue)
                    val color = colors[ThreadLocalRandom.current().nextInt(colors.size)]
                    r.getGame().tryContinueResolveProtocol(
                        r,
                        Role.skill_jiang_hu_ling_a_tos.newBuilder().setEnable(true).setColor(color).build()
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
            if (message !is Role.skill_jiang_hu_ling_a_tos) {
                log.error("错误的协议")
                return null
            }
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message.color == color.UNRECOGNIZED) {
                log.error("未知的颜色类型")
                return null
            }
            r.incrSeq()
            val skills = r.skills
            val skills2 = arrayOfNulls<Skill>(skills.size + 1)
            System.arraycopy(skills, 0, skills2, 0, skills.size)
            val skill = JiangHuLing2(message.color)
            skills2[skills2.size - 1] = skill
            skill.init(r.game)
            r.skills = skills2
            log.info(r.toString() + "发动了[江湖令]，宣言了" + message.color)
            for (p in r.game.players) {
                (p as? HumanPlayer)?.send(
                    Role.skill_jiang_hu_ling_a_toc.newBuilder()
                        .setPlayerId(p.getAlternativeLocation(r.location()))
                        .setColor(message.color).build()
                )
            }
            return ResolveResult(fsm, true)
        }

        val fsm: Fsm
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
        }

        companion object {
            private val log = Logger.getLogger(executeJiangHuLingA::class.java)
        }
    }

    private class JiangHuLing2(color: color) : TriggeredSkill {
        override fun getSkillId(): SkillId? {
            return SkillId.JIANG_HU_LING2
        }

        override fun execute(g: Game): ResolveResult? {
            if (g.fsm is ReceivePhaseSenderSkill) {
                val r: Player = fsm.whoseTurn
                if (r.findSkill<Skill?>(skillId) == null) return null
                if (r.getSkillUseCount(skillId) >= 1) return null
                var containsColor = false
                for (card in fsm.inFrontOfWhom.getMessageCards().values) {
                    if (card.colors.contains(color)) {
                        containsColor = true
                        break
                    }
                }
                if (!containsColor) return null
                r.addSkillUseCount(skillId)
                return ResolveResult(executeJiangHuLingB(fsm, color), true)
            }
            return null
        }

        val color: color

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
        }
    }

    private class executeJiangHuLingB(fsm: ReceivePhaseSenderSkill, color: color) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.whoseTurn.game.players) {
                if (p is HumanPlayer) {
                    val builder = skill_wait_for_jiang_hu_ling_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(fsm.whoseTurn.location())
                    builder.color = color
                    builder.waitingSecond = 15
                    if (p === fsm.whoseTurn) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(
                            GameExecutor.Companion.post(
                                p.getGame(),
                                Runnable {
                                    if (p.checkSeq(seq2)) p.getGame().tryContinueResolveProtocol(
                                        p,
                                        Fengsheng.end_receive_phase_tos.newBuilder().setSeq(seq2).build()
                                    )
                                },
                                p.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                                TimeUnit.SECONDS
                            )
                        )
                    }
                    p.send(builder.build())
                }
            }
            if (fsm.whoseTurn is RobotPlayer) {
                val target = fsm.inFrontOfWhom
                if (target!!.isAlive) {
                    for (card in target.messageCards.values) {
                        if (card.colors.contains(color) && !(p === target && color != Common.color.Black && card.colors.size == 1)) {
                            GameExecutor.Companion.post(
                                p.getGame(),
                                Runnable {
                                    p.getGame().tryContinueResolveProtocol(
                                        p,
                                        Role.skill_jiang_hu_ling_b_tos.newBuilder().setCardId(card.id).build()
                                    )
                                },
                                2,
                                TimeUnit.SECONDS
                            )
                            return null
                        }
                    }
                }
                GameExecutor.Companion.TimeWheel.newTimeout(TimerTask { timeout: Timeout? ->
                    p.getGame().tryContinueResolveProtocol(p, Fengsheng.end_receive_phase_tos.getDefaultInstance())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.whoseTurn) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message is Fengsheng.end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    log.error("操作太晚了, required Seq: " + player.seq + ", actual Seq: " + message.seq)
                    return null
                }
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message !is Role.skill_jiang_hu_ling_b_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.whoseTurn
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            val target = fsm.inFrontOfWhom
            if (!target!!.isAlive) {
                log.error("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                return null
            }
            if (!card.colors.contains(color)) {
                log.error("你选择的情报不是宣言的颜色")
                return null
            }
            r!!.incrSeq()
            log.info(r.toString() + "发动了[江湖令]，弃掉了" + target + "面前的" + card)
            target.deleteMessageCard(card.id)
            r.game.deck.discard(card)
            fsm.receiveOrder.removePlayerIfNotHaveThreeBlack(target)
            for (p in r.game.players) {
                (p as? HumanPlayer)?.send(
                    Role.skill_jiang_hu_ling_b_toc.newBuilder().setCardId(card.id)
                        .setPlayerId(p.getAlternativeLocation(r.location())).build()
                )
            }
            if (card.colors.contains(Common.color.Black)) r.draw(1)
            return ResolveResult(fsm, true)
        }

        val fsm: ReceivePhaseSenderSkill
        val color: color

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
        }

        companion object {
            private val log = Logger.getLogger(executeJiangHuLingB::class.java)
        }
    }

    companion object {
        fun resetJiangHuLing(game: Game) {
            for (p in game.players) {
                val skills = p.skills
                var containsJiangHuLing = false
                for (skill in skills) {
                    if (skill.skillId == SkillId.JIANG_HU_LING2) {
                        containsJiangHuLing = true
                        break
                    }
                }
                if (containsJiangHuLing) {
                    val skills2: MutableList<Skill> = ArrayList(skills.size - 1)
                    for (skill in skills) {
                        if (skill.skillId != SkillId.JIANG_HU_LING2) skills2.add(skill)
                    }
                    p.skills = skills2.toTypedArray()
                    val listeningSkills = game.listeningSkills
                    for (i in listeningSkills.indices.reversed()) {
                        if (listeningSkills[i] is JiangHuLing2) {
                            listeningSkills.removeAt(i)
                            break
                        }
                    }
                }
            }
        }
    }
}