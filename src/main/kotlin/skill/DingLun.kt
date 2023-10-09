package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.NextTurn
import com.fengsheng.phase.ReceivePhase
import com.fengsheng.protos.Role.skill_ding_lun_toc
import com.fengsheng.protos.Role.skill_ding_lun_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 李书云技能【定论】：争夺阶段，若情报在你面前，可以翻开此角色，直接成功接收，但若因此达成同色三张，则改为入手。
 */
class DingLun : InitialSkill, ActiveSkill {
    override val skillId = SkillId.DING_LUN

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            log.error("不是你发技能的时机")
            (r as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
            return
        }
        if (r !== fsm.inFrontOfWhom) {
            log.error("情报不在你面前，不能发动[定论]")
            (r as? HumanPlayer)?.sendErrorMessage("情报不在你面前，不能发动[定论]")
            return
        }
        if (r.roleFaceUp) {
            log.error("你现在正面朝上，不能发动[定论]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[定论]")
            return
        }
        val pb = message as skill_ding_lun_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        log.info("${r}发动了[定论]")
        val joinIntoHand = r.checkThreeSameMessageCard(fsm.messageCard)
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_ding_lun_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.card = fsm.messageCard.toPbCard()
                builder.joinIntoHand = joinIntoHand
                p.send(builder.build())
            }
        }
        if (joinIntoHand) {
            log.info("${r}将${fsm.messageCard}加入了手牌")
            r.cards.add(fsm.messageCard)
            g.resolve(NextTurn(fsm.whoseTurn))
        } else g.resolve(ReceivePhase(fsm.whoseTurn, fsm.sender, fsm.messageCard, r))
    }

    companion object {
        private val log = Logger.getLogger(DingLun::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            !player.roleFaceUp || return false
            player === e.inFrontOfWhom || return false
            !e.messageCard.isPureBlack() || return false
            Random.nextBoolean() || return false
            GameExecutor.post(e.whoseFightTurn.game!!, {
                skill.executeProtocol(
                    e.whoseFightTurn.game!!, e.whoseFightTurn, skill_ding_lun_tos.getDefaultInstance()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}