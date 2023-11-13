package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.card.count
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * SP白菲菲技能【套取】：出牌阶段限一次，你可以弃置两张含含相同颜色的牌，将一名其他角色情报区的一张同色情报加入手牌。
 */
class TaoQu : MainPhaseSkill(), InitialSkill {
    override val skillId = SkillId.TAO_QU

    override fun mainPhaseNeedNotify(r: Player): Boolean =
        super.mainPhaseNeedNotify(r) && arrayOf(Black, Red, Blue).any {
            r.cards.count(it) >= 2 && r.game!!.players.any { p ->
                p !== r && p!!.alive && p.messageCards.any { c -> it in c.colors }
            }
        }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? MainPhaseIdle
        if (r !== fsm?.whoseTurn) {
            log.error("现在不是出牌阶段空闲时点")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[套取]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[套取]一回合只能发动一次")
            return
        }
        val pb = message as skill_tao_qu_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.cardIdsCount != 2) {
            log.error("你必须选择两张手牌")
            (r as? HumanPlayer)?.sendErrorMessage("你必须选择两张手牌")
            return
        }
        val cards = pb.cardIdsList.map {
            val card = r.findCard(it)
            if (card == null) {
                log.error("没有这张牌")
                (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return
            }
            card
        }
        val colors = cards[0].colors.filter { it in cards[1].colors }
        if (colors.isEmpty()) {
            log.error("你选择的两张牌不含相同颜色")
            (r as? HumanPlayer)?.sendErrorMessage("你选择的两张牌不含相同颜色")
            return
        }
        if (!colors.any { c ->
                g.players.any {
                    it !== r && it!!.alive && it.messageCards.any { card -> c in card.colors }
                }
            }) {
            log.error("除自己以外场上没有你选择的颜色的情报")
            (r as? HumanPlayer)?.sendErrorMessage("除自己以外场上没有你选择的颜色的情报")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        log.info("${r}发动了[套取]")
        g.playerDiscardCard(r, *cards.toTypedArray())
        g.resolve(executeTaoQu(fsm, r, colors))
    }

    private data class executeTaoQu(val fsm: MainPhaseIdle, val r: Player, val colors: List<color>) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_tao_qu_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.addAllColors(colors)
                    builder.waitingSecond = Config.WaitSecond
                    if (p === r) {
                        val seq = p.seq
                        builder.seq = seq
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq)) {
                                val playerAndCard = g.players.flatMap {
                                    if (it === p || !it!!.alive) emptyList()
                                    else it.messageCards.mapNotNull { card ->
                                        if (!card.colors.any { c -> c in colors }) null
                                        else PlayerAndCard(it, card)
                                    }
                                }.random()
                                val builder2 = skill_tao_qu_b_tos.newBuilder()
                                builder2.targetPlayerId = p.getAlternativeLocation(playerAndCard.player.location)
                                builder2.cardId = playerAndCard.card.id
                                builder2.seq = seq
                                g.tryContinueResolveProtocol(r, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val playerAndCard = g.players.flatMap {
                        if (!it!!.alive || !it.isEnemy(r)) emptyList()
                        else it.messageCards.mapNotNull { card ->
                            if (!card.colors.any { c -> c in colors }) null
                            else PlayerAndCard(it, card)
                        }
                    }.random()
                    val builder2 = skill_tao_qu_b_tos.newBuilder()
                    builder2.targetPlayerId = r.getAlternativeLocation(playerAndCard.player.location)
                    builder2.cardId = playerAndCard.card.id
                    g.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.whoseTurn) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_tao_qu_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = player.game!!
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            if (message.targetPlayerId == 0) {
                log.error("不能以自己为目标")
                (player as? HumanPlayer)?.sendErrorMessage("不能以自己为目标")
                return null
            }
            val target = g.players[player.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.cardId)
            if (card == null) {
                log.error("没有这张情报")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张情报")
                return null
            }
            if (!card.colors.any { it in colors }) {
                log.error("选择的情报没有该颜色")
                (player as? HumanPlayer)?.sendErrorMessage("选择的情报没有该颜色")
                return null
            }
            player.incrSeq()
            log.info("${player}将${target}面前的${card}加入了手牌")
            target.deleteMessageCard(card.id)
            player.cards.add(card)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_tao_qu_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(player.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.cardId = card.id
                    p.send(builder.build())
                }
            }
            g.addEvent(DiscardCardEvent(fsm.whoseTurn, player))
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeTaoQu::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(TaoQu::class.java)
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseTurn
            player.getSkillUseCount(SkillId.TAO_QU) == 0 || return false
            val players =
                player.game!!.players.filter { it!!.alive && it.isEnemy(player) && it.messageCards.isNotEmpty() }
            val color = arrayOf(Red, Blue).filter {
                player.cards.count(it) >= 2 && players.any { p -> p!!.messageCards.any { c -> it in c.colors } }
            }.randomOrNull() ?: return false
            val cardIds = player.cards.filter { color in it.colors }.shuffled().subList(0, 2).map { it.id }
            GameExecutor.post(player.game!!, {
                val builder = skill_tao_qu_a_tos.newBuilder()
                builder.addAllCardIds(cardIds)
                skill.executeProtocol(player.game!!, player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}