package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.count
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Common.direction.Left
import com.fengsheng.protos.Common.direction.Right
import com.fengsheng.protos.Role.skill_you_di_shen_ru_tos
import com.fengsheng.protos.skillYouDiShenRuToc
import com.fengsheng.protos.skillYouDiShenRuTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 边云疆技能【诱敌深入】：整局限一次，你的传递阶段，改为将一张手牌作为情报明面传出，该情报含有身份颜色的玩家，在本阶段必须选则接收该情报，不含身份颜色的玩家不能选择接收。
 * （潜伏=红色，特工=蓝色，神秘人不受限）
 */
class YouDiShenRu : ActiveSkill {
    override val skillId = SkillId.YOU_DI_SHEN_RU

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = false

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        message as skill_you_di_shen_ru_tos
        if (r is HumanPlayer && !r.checkSeq(message.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val fsm = g.fsm as? SendPhaseStart
        if (fsm == null) {
            logger.error("[诱敌深入]的使用时机不对")
            r.sendErrorMessage("[诱敌深入]的使用时机不对")
            return
        }
        val card = r.findCard(message.cardId)
        if (card == null) {
            logger.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        if (message.targetPlayerId <= 0 || message.targetPlayerId >= r.game!!.players.size) {
            logger.error("目标错误: ${message.targetPlayerId}")
            r.sendErrorMessage("遇到了bug，试试把牌取消选择重新选一下")
            return
        }
        val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
        val lockPlayers = message.lockPlayerIdList.map {
            if (it < 0 || it >= g.players.size) {
                logger.error("锁定目标错误: $it")
                r.sendErrorMessage("锁定目标错误: $it")
                return
            }
            g.players[r.getAbstractLocation(it)]!!
        }
        val sendCardError = r.canSendCard(r, card, r.cards, message.cardDir, target, lockPlayers)
        if (sendCardError != null) {
            logger.error(sendCardError)
            r.sendErrorMessage(sendCardError)
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        r.skills = r.skills.filterNot { it === this }
        logger.info("${r}发动了[诱敌深入]")
        r.deleteCard(card.id)
        g.players.forEach { it!!.skills += YouDiShenRu2() }
        g.players.send { p ->
            skillYouDiShenRuToc {
                playerId = p.getAlternativeLocation(r.location)
                this.card = card.toPbCard()
                targetPlayerId = p.getAlternativeLocation(target.location)
                lockPlayers.forEach { lockPlayerIds.add(p.getAlternativeLocation(it.location)) }
                cardDir = message.cardDir
            }
        }
        g.resolve(
            OnSendCard(
                fsm.whoseTurn, fsm.whoseTurn, card, message.cardDir, target,
                lockPlayers, isMessageCardFaceUp = true, needRemoveCard = false, needNotify = false
            )
        )
    }

    private class YouDiShenRu2 : MustReceiveMessage() {
        override val isInitialSkill = false

        override fun mustReceive(sendPhase: SendPhaseIdle) =
            sendPhase.inFrontOfWhom.identity.let { it != Black && it in sendPhase.messageCard.colors }

        override fun cannotReceive(sendPhase: SendPhaseIdle) =
            sendPhase.inFrontOfWhom.identity.let { it != Black && it !in sendPhase.messageCard.colors }
    }

    companion object {
        fun ai(e: SendPhaseStart, skill: ActiveSkill): Boolean {
            val player = e.whoseTurn
            val game = player.game!!

            // Only consider using the skill if someone is close to winning
            player.game!!.players.any {
                it!!.alive && it.identity in listOf(Red, Blue) && it.messageCards.count(it.identity) == 2
            } || return false

            // Calculate normal (face-down) sending value
            val normalResult = player.calSendMessageCard()

            // Calculate face-up sending value by simulating YouDiShenRu effect
            val faceUpValue = calculateFaceUpSendingValue(player, normalResult, game)

            // Only use YouDiShenRu if face-up sending is significantly better
            // Use a threshold to account for the once-per-game nature of this skill
            val threshold = 20 // Adjust this value based on testing
            if (faceUpValue > normalResult.value + threshold) {
                GameExecutor.post(game, {
                    skill.executeProtocol(game, player, skillYouDiShenRuTos {
                        cardId = normalResult.card.id
                        targetPlayerId = player.getAlternativeLocation(normalResult.target.location)
                        cardDir = normalResult.dir
                        normalResult.lockedPlayers.forEach { lockPlayerId.add(player.getAlternativeLocation(it.location)) }
                    })
                }, 1, TimeUnit.SECONDS)
                return true
            }

            return false
        }

        private fun calculateFaceUpSendingValue(player: Player, result: SendMessageCardResult, game: Game): Double {
            // For face-up sending with YouDiShenRu, we need to account for forced/prohibited receiving
            val card = result.card
            val target = result.target
            val dir = result.dir

            // Simulate who would actually receive the card when sent face-up
            var currentPlayer = target
            var totalValue = 0.0
            var attempts = 0
            val maxAttempts = game.players.count { it!!.alive }

            while (attempts < maxAttempts) {
                attempts++

                if (!currentPlayer.alive) {
                    // Move to next player
                    currentPlayer = when (dir) {
                        Left -> currentPlayer.getNextLeftAlivePlayer()
                        Right -> currentPlayer.getNextRightAlivePlayer()
                        else -> player // For Up direction, return to sender
                    }
                    continue
                }

                // Check YouDiShenRu forcing/prohibiting rules
                val identity = currentPlayer.identity
                val mustReceive = identity != Black && identity in card.colors
                val cannotReceive = identity != Black && identity !in card.colors

                if (mustReceive || result.lockedPlayers.contains(currentPlayer) || currentPlayer === player) {
                    // This player must receive the card
                    totalValue = player.calculateMessageCardValue(player, currentPlayer, card, sender = player).toDouble()
                    break
                } else if (cannotReceive) {
                    // This player cannot receive, move to next
                    currentPlayer = when (dir) {
                        Left -> currentPlayer.getNextLeftAlivePlayer()
                        Right -> currentPlayer.getNextRightAlivePlayer()
                        else -> player // For Up direction, return to sender
                    }
                    continue
                } else {
                    // For Black identity players, use normal receiving logic with face-up consideration
                    // Since the card is face-up, they will use modified coefficients in their decision
                    val oldA = currentPlayer.coefficientA
                    val oldB = currentPlayer.coefficientB
                    currentPlayer.coefficientA = 1.0
                    currentPlayer.coefficientB = 0

                    val myValue = currentPlayer.calculateMessageCardValue(player, currentPlayer, card, sender = player)
                    val nextPlayer = when (dir) {
                        Left -> currentPlayer.getNextLeftAlivePlayer()
                        Right -> currentPlayer.getNextRightAlivePlayer()
                        else -> player
                    }
                    val nextValue = currentPlayer.calculateMessageCardValue(player, nextPlayer, card, sender = player)

                    // Restore coefficients
                    currentPlayer.coefficientA = oldA
                    currentPlayer.coefficientB = oldB

                    if (myValue > nextValue) {
                        // This player would choose to receive
                        totalValue = player.calculateMessageCardValue(player, currentPlayer, card, sender = player).toDouble()
                        break
                    } else {
                        // This player would not receive, move to next
                        currentPlayer = nextPlayer
                        continue
                    }
                }
            }

            return totalValue
        }
    }
}
