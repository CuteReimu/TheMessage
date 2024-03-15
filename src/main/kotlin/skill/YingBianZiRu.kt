package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.JieHuo
import com.fengsheng.card.WuDao
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.NextTurn
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * SP阿芙罗拉技能【应变自如】：争夺阶段，你可以翻开此角色，然后将待收情报翻开，根据颜其颜色执行操作：
 * 1. 红或蓝单色：视为对其使用了【截获】，摸一张牌。
 * 2. 黑单色：视为对其使用了【误导】，摸两张牌。
 * 3. 双色情报：弃置该情报，摸三张牌。
 */
class YingBianZiRu : ActiveSkill {
    override val skillId = SkillId.YING_BIAN_ZI_RU

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            logger.error("现在不是发动[应变自如]的时机")
            r.sendErrorMessage("现在不是发动[应变自如]的时机")
            return
        }
        if (fsm.isMessageCardFaceUp) {
            logger.error("情报面朝上，不能发动[应变自如]")
            r.sendErrorMessage("情报面朝上，不能发动[应变自如]")
            return
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[应变自如]")
            r.sendErrorMessage("你现在正面朝上，不能发动[应变自如]")
            return
        }
        val pb = message as skill_ying_bian_zi_ru_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        logger.info("${r}发动了[应变自如]，翻开了${fsm.messageCard}")
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        val timeout = Config.WaitSecond
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_ying_bian_zi_ru_a_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.card = fsm.messageCard.toPbCard()
                if (fsm.messageCard.isPureBlack()) {
                    builder.waitingSecond = timeout
                    if (p === r) builder.seq = p.seq
                }
                p.send(builder.build())
            }
        }
        g.fsm = fsm.copy(isMessageCardFaceUp = true)
        if (fsm.messageCard.colors.size == 1) {
            if (fsm.messageCard.colors.first() == Black) { // 黑单色，误导
                if (fsm.inFrontOfWhom === fsm.inFrontOfWhom.getNextLeftAlivePlayer() && fsm.inFrontOfWhom === fsm.inFrontOfWhom.getNextRightAlivePlayer()) {
                    // 死光了，只剩一个人了
                    r.draw(2)
                    g.resolve(WuDao.onUseCard(null, g, r, fsm.inFrontOfWhom))
                } else {
                    g.resolve(executeYingBianZiRu(fsm, r, timeout))
                }
            } else { // 红蓝，截获
                r.draw(1)
                JieHuo.execute(null, g, r)
            }
        } else { // 双色，弃掉摸三张牌
            g.deck.discard(fsm.messageCard)
            r.draw(3)
            g.resolve(NextTurn(fsm.whoseTurn))
        }
    }

    private data class executeYingBianZiRu(val fsm: FightPhaseIdle, val r: Player, val waitingSecond: Int) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            if (r is HumanPlayer) {
                val seq = r.seq
                r.timeout = GameExecutor.post(r.game!!, {
                    if (r.checkSeq(seq)) {
                        val target = listOf(
                            fsm.inFrontOfWhom.getNextLeftAlivePlayer(),
                            fsm.inFrontOfWhom.getNextRightAlivePlayer()
                        ).random()
                        val builder = skill_ying_bian_zi_ru_b_tos.newBuilder()
                        builder.targetPlayerId = r.getAlternativeLocation(target.location)
                        builder.seq = seq
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    }
                }, r.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(r.game!!, {
                    val left = fsm.inFrontOfWhom.getNextLeftAlivePlayer()
                    val right = fsm.inFrontOfWhom.getNextRightAlivePlayer()
                    val leftValue = r.calculateMessageCardValue(fsm.whoseTurn, left, fsm.messageCard)
                    val rightValue = r.calculateMessageCardValue(fsm.whoseTurn, right, fsm.messageCard)
                    val target = if (leftValue > rightValue) left else right
                    val builder = skill_ying_bian_zi_ru_b_tos.newBuilder()
                    builder.targetPlayerId = r.getAlternativeLocation(target.location)
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_ying_bian_zi_ru_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                logger.error("目标错误")
                player.sendErrorMessage("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            if (target !== fsm.inFrontOfWhom.getNextLeftAlivePlayer() && target !== fsm.inFrontOfWhom.getNextRightAlivePlayer()) {
                logger.error("只能误导给左右两边的玩家")
                player.sendErrorMessage("只能误导给左右两边的玩家")
                return null
            }
            r.incrSeq()
            logger.info("${r}视为对${target}使用了误导")
            r.draw(2)
            g.fsm = fsm
            return ResolveResult(WuDao.onUseCard(null, g, r, target), true)
        }
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            !player.roleFaceUp || return false
            !e.isMessageCardFaceUp || return false
            player.game!!.players.any {
                it!!.isEnemy(player) && it.willWin(e.whoseTurn, e.inFrontOfWhom, e.messageCard)
            } || e.inFrontOfWhom.run {
                isPartnerOrSelf(player) && willDie(e.messageCard)
                        && !willWin(e.whoseTurn, this, e.messageCard)
            } || return false
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skill_ying_bian_zi_ru_a_tos.getDefaultInstance())
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}