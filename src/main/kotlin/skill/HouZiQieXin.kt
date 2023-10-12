package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.card.Card
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnAddMessageCard
import com.fengsheng.protos.Role.skill_hou_zi_qie_xin_toc
import com.fengsheng.protos.Role.skill_hou_zi_qie_xin_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 小铃铛技能【猴子窃信】：出牌阶段限一次，你可以用手牌和一名其他角色情报区的完全同色的情报互换。
 */
class HouZiQieXin : MainPhaseSkill(), InitialSkill {
    override val skillId = SkillId.HOU_ZI_QIE_XIN

    override fun mainPhaseNeedNotify(r: Player) =
        super.mainPhaseNeedNotify(r) && r.game!!.players.any {
            it !== r && it!!.alive && it.messageCards.any { card1 ->
                r.cards.any { card2 -> card1.colorExactlyTheSame(card2) }
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
            log.error("[猴子窃信]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[猴子窃信]一回合只能发动一次")
            return
        }
        val pb = message as skill_hou_zi_qie_xin_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        val handCard = r.findCard(message.handCardId)
        if (handCard == null) {
            log.error("没有这张牌")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        if (message.targetPlayerId == 0) {
            log.error("不能以自己为目标")
            (r as? HumanPlayer)?.sendErrorMessage("不能以自己为目标")
            return
        }
        val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
        if (!target.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        val messageCard = target.findMessageCard(message.messageCardId)
        if (messageCard == null) {
            log.error("没有这张情报")
            (r as? HumanPlayer)?.sendErrorMessage("没有这张情报")
            return
        }
        if (!handCard.colorExactlyTheSame(messageCard)) {
            log.error("选择的两张牌不是完全同色")
            (r as? HumanPlayer)?.sendErrorMessage("选择的两张牌不是完全同色")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        log.info("${r}发动了[猴子窃信]，将手牌的${handCard}和${target}情报区的${messageCard}交换")
        r.deleteCard(handCard.id)
        target.deleteMessageCard(messageCard.id)
        r.cards.add(messageCard)
        target.messageCards.add(handCard)
        for (p in r.game!!.players) {
            if (p is HumanPlayer) {
                val builder = skill_hou_zi_qie_xin_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.handCard = handCard.toPbCard()
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                builder.messageCardId = message.messageCardId
                p.send(builder.build())
            }
        }
        g.resolve(OnAddMessageCard(r, fsm))
    }

    companion object {
        private val log = Logger.getLogger(HouZiQieXin::class.java)

        private fun Card.colorExactlyTheSame(card: Card): Boolean {
            if (colors.size != card.colors.size) return false
            val c1 = colors.sortedBy { it.number }
            val c2 = card.colors.sortedBy { it.number }
            c1.forEachIndexed { index, color -> if (color != c2[index]) return false }
            return true
        }

        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseTurn
            player.getSkillUseCount(SkillId.HOU_ZI_QIE_XIN) == 0 || return false
            val playerAndCard = player.game!!.players.flatMap {
                if (it !== player && it!!.alive) {
                    it.messageCards.mapNotNull { card ->
                        if (player.cards.any { c -> c.hasSameColor(card) }) PlayerAndCard(it, card)
                        else null
                    }
                } else emptyList()
            }.randomOrNull() ?: return false
            val card = player.cards.filter { it.hasSameColor(playerAndCard.card) }.randomOrNull() ?: return false
            GameExecutor.post(player.game!!, {
                val builder = skill_hou_zi_qie_xin_tos.newBuilder()
                builder.handCardId = card.id
                builder.targetPlayerId = player.getAlternativeLocation(playerAndCard.player.location)
                builder.messageCardId = playerAndCard.card.id
                skill.executeProtocol(player.game!!, player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}