package com.fengsheng.skill

import com.fengsheng.Gameimport

com.fengsheng.GameExecutorimport com.fengsheng.HumanPlayerimport com.fengsheng.Playerimport com.fengsheng.card.*import com.fengsheng.phase.FightPhaseIdleimport

com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Roleimport com.fengsheng.protos.Role.skill_ji_song_tocimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.*import java.util.concurrent.*

/**
 * 鬼脚技能【急送】：争夺阶段限一次，你可以弃置两张手牌，或从你的情报区弃置一张非黑色情报，然后将待收情报移至一名角色面前。
 */
class JiSong : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.JI_SONG
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn) {
            log.error("现在不是发动[急送]的时机")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[急送]一回合只能发动一次")
            return
        }
        val pb = message as Role.skill_ji_song_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]
        if (!target.isAlive) {
            log.error("目标已死亡")
            return
        }
        if (target === fsm.inFrontOfWhom) {
            log.error("情报本来就在他面前")
            return
        }
        var messageCard: Card? = null
        val cards = arrayOfNulls<Card>(2)
        if (pb.cardIdsCount == 0 && pb.messageCard != 0) {
            if (r.findMessageCard(pb.messageCard).also { messageCard = it } == null) {
                log.error("没有这张牌")
                return
            } else if (messageCard.getColors().contains(color.Black)) {
                log.error("这张牌不是非黑色")
                return
            }
        } else if (pb.cardIdsCount == 2 && pb.messageCard == 0) {
            for (i in 0..1) {
                if (r.findCard(pb.getCardIds(i)).also { cards[i] = it } == null) {
                    log.error("没有这张牌")
                    return
                }
            }
        } else {
            log.error("发动技能支付的条件不正确")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        if (messageCard != null) {
            log.info(r.toString() + "发动了[急送]，弃掉了面前的" + messageCard + "，将情报移至" + target + "面前")
            r.deleteMessageCard(messageCard.getId())
        } else {
            log.info(r.toString() + "发动了[急送]，选择弃掉两张手牌，将情报移至" + target + "面前")
            g.playerDiscardCard(r, *cards)
        }
        fsm.inFrontOfWhom = target
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_ji_song_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location())
                builder.targetPlayerId = p.getAlternativeLocation(target.location())
                if (messageCard != null) builder.messageCard = messageCard!!.toPbCard()
                p.send(builder.build())
            }
        }
        fsm.whoseFightTurn = fsm.inFrontOfWhom
        g.continueResolve()
    }

    companion object {
        private val log = Logger.getLogger(JiSong::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.getSkillUseCount(SkillId.JI_SONG) > 0) return false
            if (player.cards.size < 2) return false
            val colors = e.messageCard.colors
            if (colors.size != 1) return false
            if (colors[0] == color.Black) {
                if (e.inFrontOfWhom !== player) return false
            } else {
                val identity = player.identity
                val identity2 = e.inFrontOfWhom.identity
                if (identity != color.Black && identity == identity2) return false
                if (identity2 == color.Black || colors[0] != identity2) return false
            }
            val players: MutableList<Player> = ArrayList()
            for (p in player.game.players) {
                if (p !== e.inFrontOfWhom && p.isAlive) players.add(p)
            }
            if (players.isEmpty()) return false
            val random: Random = ThreadLocalRandom.current()
            val target = players[random.nextInt(players.size)]
            val cards = arrayOfNulls<Card>(2)
            var i = 0
            for (c in player.cards.values) {
                cards[i++] = c
                if (i >= 2) break
            }
            GameExecutor.Companion.post(player.game, Runnable {
                skill.executeProtocol(
                    player.game, player, Role.skill_ji_song_tos.newBuilder().addCardIds(cards[0].getId()).addCardIds(
                        cards[1].getId()
                    )
                        .setTargetPlayerId(player.getAlternativeLocation(target.location())).build()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}