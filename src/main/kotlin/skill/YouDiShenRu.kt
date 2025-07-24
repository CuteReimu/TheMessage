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

            // 只有当有人接近获胜时才考虑使用技能
            player.game!!.players.any {
                it!!.alive && it.identity in listOf(Red, Blue) && it.messageCards.count(it.identity) == 2
            } || return false

            // 计算正常（暗面）传递的价值
            val normalResult = player.calSendMessageCard()

            // 通过模拟诱敌深入效果计算明面传递的价值
            val faceUpValue = calculateFaceUpSendingValue(player, normalResult, game)

            // 只有明面传递明显更好时才使用诱敌深入
            // 使用阈值来考虑此技能一局限一次的特性
            val threshold = 20 // 根据测试调整此值
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
            // 对于使用诱敌深入的明面传递，我们需要考虑强制/禁止接收的规则
            val card = result.card
            val target = result.target
            val dir = result.dir

            // 模拟明面传递时谁会实际接收卡牌
            var currentPlayer = target
            var totalValue = 0.0
            var attempts = 0
            val maxAttempts = game.players.count { it!!.alive }

            while (attempts < maxAttempts) {
                attempts++

                if (!currentPlayer.alive) {
                    // 移动到下一个玩家
                    currentPlayer = when (dir) {
                        Left -> currentPlayer.getNextLeftAlivePlayer()
                        Right -> currentPlayer.getNextRightAlivePlayer()
                        else -> player // 对于向上方向，返回发送者
                    }
                    continue
                }

                // 检查诱敌深入的强制/禁止规则
                val identity = currentPlayer.identity
                val mustReceive = identity != Black && identity in card.colors
                val cannotReceive = identity != Black && identity !in card.colors

                if (mustReceive || result.lockedPlayers.contains(currentPlayer) || currentPlayer === player) {
                    // 此玩家必须接收卡牌
                    totalValue = player.calculateMessageCardValue(player, currentPlayer, card, sender = player).toDouble()
                    break
                } else if (cannotReceive) {
                    // 此玩家不能接收，移动到下一个
                    currentPlayer = when (dir) {
                        Left -> currentPlayer.getNextLeftAlivePlayer()
                        Right -> currentPlayer.getNextRightAlivePlayer()
                        else -> player // 对于向上方向，返回发送者
                    }
                    continue
                } else {
                    // 对于黑色身份玩家，使用正常接收逻辑并考虑明面卡牌
                    // 由于卡牌是明面的，他们在决策时会使用修改后的系数
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

                    // 恢复系数
                    currentPlayer.coefficientA = oldA
                    currentPlayer.coefficientB = oldB

                    if (myValue > nextValue) {
                        // 此玩家会选择接收
                        totalValue = player.calculateMessageCardValue(player, currentPlayer, card, sender = player).toDouble()
                        break
                    } else {
                        // 此玩家不会接收，移动到下一个
                        currentPlayer = nextPlayer
                        continue
                    }
                }
            }

            return totalValue
        }
    }
}
