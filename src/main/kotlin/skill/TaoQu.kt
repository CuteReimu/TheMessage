package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.card.count
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Role.skill_tao_qu_a_tos
import com.fengsheng.protos.Role.skill_tao_qu_b_tos
import com.fengsheng.protos.skillTaoQuAToc
import com.fengsheng.protos.skillTaoQuATos
import com.fengsheng.protos.skillTaoQuBToc
import com.fengsheng.protos.skillTaoQuBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * SP白菲菲技能【套取】：出牌阶段限一次，你可以展示两张含含相同颜色的牌，将一名其他角色情报区的一张同色情报加入手牌，其摸一张牌。
 */
class TaoQu : MainPhaseSkill() {
    override val skillId = SkillId.TAO_QU

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player): Boolean = super.mainPhaseNeedNotify(r) && listOf(Black, Red, Blue).any {
        r.cards.count(it) >= 2 && r.game!!.players.any { p ->
            p !== r && p!!.alive && p.messageCards.any { c -> it in c.colors }
        }
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        val fsm = g.fsm as? MainPhaseIdle
        if (r !== fsm?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            r.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[套取]一回合只能发动一次")
            r.sendErrorMessage("[套取]一回合只能发动一次")
            return
        }
        val pb = message as skill_tao_qu_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.cardIdsCount != 2) {
            logger.error("你必须选择两张手牌")
            r.sendErrorMessage("你必须选择两张手牌")
            return
        }
        val cards = pb.cardIdsList.map {
            val card = r.findCard(it)
            if (card == null) {
                logger.error("没有这张牌")
                r.sendErrorMessage("没有这张牌")
                return
            }
            card
        }
        val colors = cards[0].colors.filter { it in cards[1].colors }
        if (colors.isEmpty()) {
            logger.error("你选择的两张牌不含相同颜色")
            r.sendErrorMessage("你选择的两张牌不含相同颜色")
            return
        }
        if (!colors.any { c ->
                g.players.any {
                    it !== r && it!!.alive && it.messageCards.any { card -> c in card.colors }
                }
            }) {
            logger.error("除自己以外场上没有你选择的颜色的情报")
            r.sendErrorMessage("除自己以外场上没有你选择的颜色的情报")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}发动了[套取]，展示了${cards.joinToString()}")
        g.resolve(executeTaoQu(fsm, r, cards, colors))
    }

    private data class executeTaoQu(
        val fsm: MainPhaseIdle,
        val r: Player,
        val cards: List<Card>,
        val colors: List<color>
    ) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            g.players.send { p ->
                skillTaoQuAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    colors.addAll(this@executeTaoQu.colors)
                    this@executeTaoQu.cards.forEach { cards.add(it.toPbCard()) }
                    waitingSecond = Config.WaitSecond
                    if (p === r) {
                        val seq = p.seq
                        this.seq = seq
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq)) {
                                val playerAndCard = g.players.flatMap {
                                    if (it === p || !it!!.alive) emptyList()
                                    else it.messageCards.mapNotNull { card ->
                                        if (!card.colors.any { c -> c in colors }) null
                                        else PlayerAndCard(it, card)
                                    }
                                }.random()
                                g.tryContinueResolveProtocol(r, skillTaoQuBTos {
                                    targetPlayerId = p.getAlternativeLocation(playerAndCard.player.location)
                                    cardId = playerAndCard.card.id
                                    this.seq = seq
                                })
                            }
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
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
                    }.minBy { RobotPlayer.cardOrder[it.card.type]!! }
                    g.tryContinueResolveProtocol(r, skillTaoQuBTos {
                        targetPlayerId = r.getAlternativeLocation(playerAndCard.player.location)
                        cardId = playerAndCard.card.id
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== fsm.whoseTurn) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_tao_qu_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val g = player.game!!
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                logger.error("目标错误")
                player.sendErrorMessage("目标错误")
                return null
            }
            if (message.targetPlayerId == 0) {
                logger.error("不能以自己为目标")
                player.sendErrorMessage("不能以自己为目标")
                return null
            }
            val target = g.players[player.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.cardId)
            if (card == null) {
                logger.error("没有这张情报")
                player.sendErrorMessage("没有这张情报")
                return null
            }
            if (!card.colors.any { it in colors }) {
                logger.error("选择的情报没有该颜色")
                player.sendErrorMessage("选择的情报没有该颜色")
                return null
            }
            player.incrSeq()
            logger.info("${player}将${target}面前的${card}加入了手牌")
            target.deleteMessageCard(card.id)
            player.cards.add(card)
            g.players.send {
                skillTaoQuBToc {
                    playerId = it.getAlternativeLocation(player.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    cardId = card.id
                }
            }
            target.draw(1)
            g.addEvent(DiscardCardEvent(fsm.whoseTurn, player))
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseTurn
            player.getSkillUseCount(SkillId.TAO_QU) == 0 || return false
            val players =
                player.game!!.players.filter { it!!.alive && it.isEnemy(player) && it.messageCards.isNotEmpty() }
            val color = listOf(Red, Blue).filter {
                player.cards.count(it) >= 2 && players.any { p -> p!!.messageCards.any { c -> it in c.colors } }
            }.randomOrNull() ?: return false
            val cardIds = player.cards.filter { color in it.colors }.shuffled().take(2).map { it.id }
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skillTaoQuATos { this.cardIds.addAll(cardIds) })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
