package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.card.JieHuo
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Role.skill_zuo_you_feng_yuan_toc
import com.fengsheng.protos.Role.skill_zuo_you_feng_yuan_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 秦圆圆技能【左右逢源】：争夺阶段，你可以翻开此角色牌，然后指定两名角色，他们弃置所有手牌，然后摸三张牌（由你指定的角色先摸）。
 */
class ZuoYouFengYuan : AbstractSkill(), ActiveSkill {
    override val skillId = SkillId.ZUO_YOU_FENG_YUAN

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (!JieHuo.canUse(g, r)) return
        if (r.roleFaceUp) {
            log.error("你现在正面朝上，不能发动[左右逢源]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[左右逢源]")
            return
        }
        val pb = message as skill_zuo_you_feng_yuan_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerIdsCount != 2) {
            log.error("必须选择两个目标")
            (r as? HumanPlayer)?.sendErrorMessage("必须选择两个目标")
            return
        }
        val targets = pb.targetPlayerIdsList.map {
            if (it < 0 || it >= g.players.size) {
                log.error("目标错误：$it")
                (r as? HumanPlayer)?.sendErrorMessage("目标错误：$it")
                return
            }
            val target = g.players[r.getAbstractLocation(it)]!!
            if (!target.alive) {
                log.error("目标已死亡")
                (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return
            }
            target
        }
        r.incrSeq()
        g.playerSetRoleFaceUp(r, true)
        log.info("${r}对${targets.toTypedArray().contentToString()}发动了[左右逢源]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_zuo_you_feng_yuan_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.addAllTargetPlayerIds(pb.targetPlayerIdsList)
                p.send(builder.build())
            }
        }
        targets.forEach { g.playerDiscardCard(it, *it.cards.toTypedArray()) }
        targets.forEach { it.draw(3) }
        g.continueResolve()
    }

    companion object {
        private val log = Logger.getLogger(ZuoYouFengYuan::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val r = e.whoseFightTurn
            if (r.roleFaceUp) return false
            val players = r.game!!.players
            if (Random.nextInt(players.size) != 0) return false
            val playersList = players.toMutableList()
            playersList.shuffle()
            GameExecutor.post(r.game!!, {
                val builder = skill_zuo_you_feng_yuan_tos.newBuilder()
                builder.addTargetPlayerIds(r.getAlternativeLocation(playersList[0]!!.location))
                builder.addTargetPlayerIds(r.getAlternativeLocation(playersList[1]!!.location))
                skill.executeProtocol(r.game!!, r, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}