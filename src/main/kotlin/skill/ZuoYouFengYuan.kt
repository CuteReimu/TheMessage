package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Role.skill_zuo_you_feng_yuan_toc
import com.fengsheng.protos.Role.skill_zuo_you_feng_yuan_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 秦圆圆技能【左右逢源】：争夺阶段，你可以翻开此角色牌，然后指定两名角色，他们弃置所有手牌，然后摸三张牌（由你指定的角色先摸）。
 */
class ZuoYouFengYuan : ActiveSkill {
    override val skillId = SkillId.ZUO_YOU_FENG_YUAN

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (fsm == null || fsm.whoseFightTurn !== r) {
            logger.error("没有轮到你操作")
            (r as? HumanPlayer)?.sendErrorMessage("没有轮到你操作")
            return
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[左右逢源]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[左右逢源]")
            return
        }
        val pb = message as skill_zuo_you_feng_yuan_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerIdsCount != 2) {
            logger.error("必须选择两个目标")
            (r as? HumanPlayer)?.sendErrorMessage("必须选择两个目标")
            return
        }
        val targets = pb.targetPlayerIdsList.map {
            if (it < 0 || it >= g.players.size) {
                logger.error("目标错误：$it")
                (r as? HumanPlayer)?.sendErrorMessage("目标错误：$it")
                return
            }
            val target = g.players[r.getAbstractLocation(it)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return
            }
            target
        }
        r.incrSeq()
        g.playerSetRoleFaceUp(r, true)
        logger.info("${r}对${targets.joinToString()}发动了[左右逢源]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_zuo_you_feng_yuan_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.addAllTargetPlayerIds(pb.targetPlayerIdsList)
                p.send(builder.build())
            }
        }
        targets.forEach {
            g.playerDiscardCard(it, it.cards.toList())
            g.addEvent(DiscardCardEvent(fsm.whoseTurn, it))
        }
        targets.forEach { it.draw(3) }
        g.resolve(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom))
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val r = e.whoseFightTurn
            !r.roleFaceUp || return false
            val players = r.game!!.players.toMutableList()
            players.removeIf { !it!!.alive }
            val willWin = players.find { it!!.isEnemy(r) && it.willWin(e.whoseTurn, e.inFrontOfWhom, e.messageCard) }
            if (willWin != null) {
                players.removeIf { it!!.isPartnerOrSelf(willWin) }
                players.size >= 2 || return false
                players.sortBy { it!!.cards.size }
            } else {
                players.removeIf { if (it!!.isEnemy(r)) it.cards.size <= 3 else it.cards.size >= 3 }
                players.size >= 2 || return false
                players.shuffle()
            }
            GameExecutor.post(r.game!!, {
                val builder = skill_zuo_you_feng_yuan_tos.newBuilder()
                builder.addTargetPlayerIds(r.getAlternativeLocation(players[0]!!.location))
                builder.addTargetPlayerIds(r.getAlternativeLocation(players[1]!!.location))
                skill.executeProtocol(r.game!!, r, builder.build())
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}