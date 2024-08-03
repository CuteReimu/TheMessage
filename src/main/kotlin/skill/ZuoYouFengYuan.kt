package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Role.skill_zuo_you_feng_yuan_tos
import com.fengsheng.protos.skillZuoYouFengYuanToc
import com.fengsheng.protos.skillZuoYouFengYuanTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 秦圆圆技能【左右逢源】：争夺阶段，你可以翻开此角色牌，然后指定两名角色，他们弃置所有手牌，然后摸三张牌（由你指定的角色先摸）。
 */
class ZuoYouFengYuan : ActiveSkill {
    override val skillId = SkillId.ZUO_YOU_FENG_YUAN

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        val fsm = g.fsm as? FightPhaseIdle
        if (fsm == null || fsm.whoseFightTurn !== r) {
            logger.error("没有轮到你操作")
            r.sendErrorMessage("没有轮到你操作")
            return
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[左右逢源]")
            r.sendErrorMessage("你现在正面朝上，不能发动[左右逢源]")
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
            r.sendErrorMessage("必须选择两个目标")
            return
        }
        val targets = pb.targetPlayerIdsList.map {
            if (it < 0 || it >= g.players.size) {
                logger.error("目标错误：$it")
                r.sendErrorMessage("目标错误：$it")
                return
            }
            val target = g.players[r.getAbstractLocation(it)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                r.sendErrorMessage("目标已死亡")
                return
            }
            target
        }
        r.incrSeq()
        g.playerSetRoleFaceUp(r, true)
        logger.info("${r}对${targets.joinToString()}发动了[左右逢源]")
        g.players.send {
            skillZuoYouFengYuanToc {
                playerId = it.getAlternativeLocation(r.location)
                targetPlayerIds.addAll(pb.targetPlayerIdsList)
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
            val alivePlayers = r.game!!.players.filter { it!!.alive }
            val inFront = e.inFrontOfWhom
            val card = e.messageCard
            val willWinOrDie = // 敌人要赢了或者队友要死了
                alivePlayers.any { it!!.isEnemy(r) && it.willWin(e.whoseTurn, inFront, card) } ||
                    inFront.isPartnerOrSelf(r) && !r.willWin(e.whoseTurn, inFront, card) && inFront.willDie(card)
            val p =
                if (!willWinOrDie) alivePlayers.filter { if (it!!.isEnemy(r)) it.cards.size > 3 else it.cards.size < 3 }
                else alivePlayers.filter { it === r || if (it!!.isEnemy(r)) it.cards.size >= 3 else it.cards.size <= 3 }
            p.size >= 2 || return false
            val players = p.sortedByDescending { abs(it!!.cards.size - 3.1) } // 7牌＞0牌＞6牌＞1牌＞5牌＞2牌＞4牌＞3牌
            GameExecutor.post(r.game!!, {
                skill.executeProtocol(r.game!!, r, skillZuoYouFengYuanTos {
                    targetPlayerIds.add(r.getAlternativeLocation(players[0]!!.location))
                    targetPlayerIds.add(r.getAlternativeLocation(players[1]!!.location))
                })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
