package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.OnUseCardimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Role.skill_wait_for_zhuan_jiao_tocimport com.fengsheng.skill.ZhuanJiao.executeZhuanJiao com.fengsheng.card.*import com.fengsheng.protos.Commonimport

com.fengsheng.protos.Roleimport com.google.protobuf.GeneratedMessageV3
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Loggerimport java.util.ArrayListimport java.util.concurrent.*
/**
 * 白小年技能【转交】：你使用一张手牌后，可以从你的情报区选择一张非黑色情报，将其置入另一名角色的情报区，然后你摸两张牌。你不能通过此技能让任何角色收集三张或更多同色情报。
 */
class ZhuanJiao : AbstractSkill(), TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.ZHUAN_JIAO
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm !is OnUseCard || fsm.askWhom !== fsm.player || fsm.askWhom.findSkill<Skill>(skillId) == null) return null
        var notBlack = false
        for (card in fsm.askWhom.getMessageCards().values) {
            if (!card.colors.contains(color.Black)) {
                notBlack = true
                break
            }
        }
        if (!notBlack) return null
        if (fsm.askWhom.getSkillUseCount(skillId) > 0) return null
        fsm.askWhom.addSkillUseCount(skillId)
        val r: Player = fsm.askWhom
        val oldResolveFunc: Fsm = fsm.resolveFunc
        fsm.resolveFunc = Fsm {
            r.resetSkillUseCount(skillId)
            oldResolveFunc.resolve()
        }
        return ResolveResult(executeZhuanJiao(fsm), true)
    }

    private class executeZhuanJiao(fsm: OnUseCard) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.askWhom
            for (player in r!!.game.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_zhuan_jiao_toc.newBuilder()
                    builder.setPlayerId(player.getAlternativeLocation(r.location())).waitingSecond = 15
                    if (player === r) {
                        val seq2: Int = player.seq
                        builder.seq = seq2
                        player.setTimeout(
                            GameExecutor.Companion.post(
                                r.getGame(),
                                Runnable {
                                    r.getGame().tryContinueResolveProtocol(
                                        r,
                                        Role.skill_zhuan_jiao_tos.newBuilder().setEnable(false).setSeq(seq2).build()
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
            if (r is RobotPlayer) {
                var messageCard: Card? = null
                for (card in r.getMessageCards().values) {
                    if (!card.colors.contains(color.Black)) {
                        messageCard = card
                        break
                    }
                }
                if (messageCard != null) {
                    val finalCard: Card = messageCard
                    val players: MutableList<Player> = ArrayList()
                    val identity = r.getIdentity()
                    val color = messageCard.colors[0]
                    for (p in r.getGame().players) {
                        if (p === r || !p.isAlive) continue
                        if (identity == Common.color.Black) {
                            if (color == p.identity) continue
                        } else {
                            if (p.identity != Common.color.Black && p.identity != identity) continue
                        }
                        var count = 0
                        for (c in p.messageCards.values) {
                            if (c.colors.contains(color)) count++
                        }
                        if (count < 2) players.add(p)
                    }
                    if (!players.isEmpty()) {
                        val target = players[ThreadLocalRandom.current().nextInt(players.size)]
                        GameExecutor.Companion.post(r.getGame(), Runnable {
                            r.getGame().tryContinueResolveProtocol(
                                r, Role.skill_zhuan_jiao_tos.newBuilder()
                                    .setTargetPlayerId(r.getAlternativeLocation(target.location()))
                                    .setEnable(true).setCardId(finalCard.id).build()
                            )
                        }, 2, TimeUnit.SECONDS)
                        return null
                    }
                }
                GameExecutor.Companion.post(
                    r.getGame(),
                    Runnable {
                        r.getGame().tryContinueResolveProtocol(
                            r,
                            Role.skill_zhuan_jiao_tos.newBuilder().setEnable(false).build()
                        )
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
            if (message !is Role.skill_zhuan_jiao_tos) {
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
                return ResolveResult(fsm, true)
            }
            val card = r.findMessageCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                return null
            }
            if (card.colors.contains(color.Black)) {
                log.error("不是非黑色情报")
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
            val target = r.game.players[r.getAbstractLocation(message.targetPlayerId)]
            if (!target.isAlive) {
                log.error("目标已死亡")
                return null
            }
            if (target.checkThreeSameMessageCard(card)) {
                log.error("你不能通过此技能让任何角色收集三张或更多同色情报")
                return null
            }
            r.incrSeq()
            log.info(r.toString() + "发动了[转交]")
            r.deleteMessageCard(card.id)
            target.addMessageCard(card)
            log.info(r.toString() + "面前的" + card + "移到了" + target + "面前")
            for (p in g.players) {
                (p as? HumanPlayer)?.send(
                    Role.skill_zhuan_jiao_toc.newBuilder().setCardId(card.id)
                        .setPlayerId(p.getAlternativeLocation(r.location()))
                        .setTargetPlayerId(p.getAlternativeLocation(target.location())).build()
                )
            }
            r.draw(2)
            return ResolveResult(fsm, true)
        }

        val fsm: OnUseCard

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
        }

        companion object {
            private val log = Logger.getLogger(executeZhuanJiao::class.java)
        }
    }
}