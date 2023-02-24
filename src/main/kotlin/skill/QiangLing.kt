package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.OnChooseReceiveCardimport

com.fengsheng.phase.OnSendCardimport com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Common.card_typeimport com.fengsheng.protos.Role.skill_wait_for_qiang_ling_tocimport com.fengsheng.skill.QiangLing.executeQiangLingimport java.util.concurrent.*import java.util.function.Consumer

com.fengsheng.protos.Roleimport com.google.protobuf.GeneratedMessageV3
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Loggerimport java.util.*import java.util.concurrent.*
import java.util.function.Consumer

/**
 * 张一挺技能【强令】：你传出情报后，或你决定接收情报后，可以宣言至多两个卡牌名称。本回合中，所有角色均不能使用被宣言的卡牌。
 */
class QiangLing : TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.QIANG_LING
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm is OnSendCard) {
            val r: Player = fsm.whoseTurn
            if (r.findSkill<Skill?>(skillId) == null) return null
            if (r.getSkillUseCount(skillId) >= 1) return null
            r.addSkillUseCount(skillId)
            return ResolveResult(executeQiangLing(fsm, r), true)
        } else if (g.fsm is OnChooseReceiveCard) {
            val r: Player = fsm.inFrontOfWhom
            if (r.findSkill<Skill?>(skillId) == null) return null
            if (r.getSkillUseCount(skillId) >= 2) return null
            r.addSkillUseCount(skillId, 2)
            return ResolveResult(executeQiangLing(fsm, r), true)
        }
        return null
    }

    private class executeQiangLing(fsm: Fsm, r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_qiang_ling_toc.newBuilder()
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
                                    Role.skill_qiang_ling_tos.newBuilder().setEnable(false).setSeq(seq2).build()
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
                    val result: MutableList<card_type> = ArrayList()
                    if (r.getGame().qiangLingTypes.isEmpty()) {
                        val cardTypes = arrayOf(card_type.Jie_Huo, card_type.Diao_Bao, card_type.Wu_Dao)
                        val idx = ThreadLocalRandom.current().nextInt(cardTypes.size)
                        for (i in cardTypes.indices) {
                            if (i != idx) result.add(cardTypes[i])
                        }
                    } else {
                        val cardTypes = EnumSet.of(card_type.Jie_Huo, card_type.Diao_Bao, card_type.Wu_Dao)
                        r.getGame().qiangLingTypes.forEach(Consumer { o: card_type -> cardTypes.remove(o) })
                        result.add(card_type.Po_Yi)
                        for (t in cardTypes) {
                            result.add(t)
                            break
                        }
                    }
                    r.getGame().tryContinueResolveProtocol(
                        r,
                        Role.skill_qiang_ling_tos.newBuilder().setEnable(true).addAllTypes(result).build()
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
            if (message !is Role.skill_qiang_ling_tos) {
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
            if (message.typesCount == 0) {
                log.error("enable为true时types不能为0")
                return null
            }
            for (t in message.typesList) {
                if (t == card_type.UNRECOGNIZED || t == null) {
                    log.error("未知的卡牌类型$t")
                    return null
                }
            }
            r.incrSeq()
            log.info(r.toString() + "发动了[强令]，禁止了" + Arrays.toString(message.typesList.toTypedArray()))
            r.game.qiangLingTypes.addAll(message.typesList)
            for (p in r.game.players) {
                (p as? HumanPlayer)?.send(
                    Role.skill_qiang_ling_toc.newBuilder()
                        .setPlayerId(p.getAlternativeLocation(r.location()))
                        .addAllTypes(message.typesList).build()
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
        }

        companion object {
            private val log = Logger.getLogger(executeQiangLing::class.java)
        }
    }

    companion object {
        fun resetQiangLing(game: Game) {
            game.qiangLingTypes.clear()
        }
    }
}