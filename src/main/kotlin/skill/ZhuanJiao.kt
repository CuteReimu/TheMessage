package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 白小年技能【转交】：你使用一张手牌后，可以从你的情报区选择一张非黑色情报，将其置入另一名角色的情报区，然后你摸两张牌。你不能通过此技能让任何角色收集三张或更多同色情报。
 */
class ZhuanJiao : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.ZHUAN_JIAO

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? OnUseCard
        if (fsm == null || fsm.askWhom !== fsm.player || fsm.askWhom.findSkill(skillId) == null)
            return null
        if (fsm.askWhom.messageCards.all { it.colors.contains(color.Black) }) return null
        if (fsm.askWhom.getSkillUseCount(skillId) > 0) return null
        fsm.askWhom.addSkillUseCount(skillId)
        val r = fsm.askWhom
        val oldResolveFunc = fsm.resolveFunc
        return ResolveResult(executeZhuanJiao(fsm.copy(resolveFunc = object : Fsm {
            override fun resolve(): ResolveResult? {
                r.resetSkillUseCount(skillId)
                return oldResolveFunc.resolve()
            }
        })), true)
    }

    private data class executeZhuanJiao(val fsm: OnUseCard) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.askWhom
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_zhuan_jiao_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = 15
                    if (player === r) {
                        val seq2 = player.seq
                        builder.seq = seq2
                        player.timeout =
                            GameExecutor.post(
                                r.game!!,
                                {
                                    val builder2 = skill_zhuan_jiao_tos.newBuilder()
                                    builder2.enable = false
                                    builder2.seq = seq2
                                    r.game!!.tryContinueResolveProtocol(r, builder2.build())
                                },
                                player.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                                TimeUnit.SECONDS
                            )
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                val messageCard = r.messageCards.find { !it.colors.contains(color.Black) }
                if (messageCard != null) {
                    val identity = r.identity
                    val c = messageCard.colors.first()
                    val players = r.game!!.players.filter { p ->
                        if (p === r || !p!!.alive) return@filter false
                        if (identity == color.Black) {
                            if (c == p.identity) return@filter false
                        } else {
                            if (p.identity != color.Black && p.identity != identity) return@filter false
                        }
                        p.messageCards.count { it.colors.contains(c) } < 2
                    }
                    if (players.isNotEmpty()) {
                        val target = players[Random.nextInt(players.size)]!!
                        GameExecutor.post(r.game!!, {
                            val builder = skill_zhuan_jiao_tos.newBuilder()
                            builder.targetPlayerId = r.getAlternativeLocation(target.location)
                            builder.enable = true
                            builder.cardId = messageCard.id
                            r.game!!.tryContinueResolveProtocol(r, builder.build())
                        }, 2, TimeUnit.SECONDS)
                        return null
                    }
                }
                GameExecutor.post(
                    r.game!!,
                    {
                        val builder = skill_zhuan_jiao_tos.newBuilder()
                        builder.enable = false
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
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
            if (message !is skill_zhuan_jiao_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.askWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
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
            val target = r.game!!.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                log.error("目标已死亡")
                return null
            }
            if (target.checkThreeSameMessageCard(card)) {
                log.error("你不能通过此技能让任何角色收集三张或更多同色情报")
                return null
            }
            r.incrSeq()
            log.info("${r}发动了[转交]")
            r.deleteMessageCard(card.id)
            target.messageCards.add(card)
            log.info("${r}面前的${card}移到了${target}面前")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_zhuan_jiao_toc.newBuilder()
                    builder.cardId = card.id
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            r.draw(2)
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeZhuanJiao::class.java)
        }
    }
}