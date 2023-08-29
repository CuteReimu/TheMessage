package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.UseChengQingOnDying
import com.fengsheng.phase.WaitForChengQing
import com.fengsheng.protos.Common.role.unknown
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * SP端木静技能【后来人】：你濒死时，可以翻开此角色牌并将其移出游戏，弃置你情报区中的情报，直到剩下一张红色或蓝色情报为止，然后从角色牌堆顶秘密抽取三张牌，从中选择一张作为你的新角色牌（隐藏角色则面朝下），剩余的角色牌放回角色牌堆底。
 */
class HouLaiRen : AbstractSkill(), ActiveSkill {
    override val skillId = SkillId.HOU_LAI_REN

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? WaitForChengQing
        if (fsm == null || r !== fsm.askWhom || r !== fsm.whoDie) {
            log.error("还没有结算到你濒死")
            (r as? HumanPlayer)?.sendErrorMessage("还没有结算到你濒死")
            return
        }
        if (r.roleFaceUp) {
            log.error("你现在正面朝上，不能发动[后来人]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[后来人]")
            return
        }
        val pb = message as skill_hou_lai_ren_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val card = r.findMessageCard(message.remainCardId)
        if (card == null) {
            log.error("没有这张情报")
            (r as? HumanPlayer)?.sendErrorMessage("没有这张情报")
            return
        }
        if (card.isPureBlack()) {
            log.error("你必须选择一张红色情报或蓝色情报")
            (r as? HumanPlayer)?.sendErrorMessage("你必须选择一张红色情报或蓝色情报")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        val roles = RoleCache.getRandomRoles(3, g.players.map { it!!.role }.toSet())
        val discardCards = r.messageCards.filter { it.id != message.remainCardId }.toTypedArray()
        log.info("${r}发动了[后来人]，弃掉了${discardCards.contentToString()}")
        r.messageCards.clear()
        r.messageCards.add(card)
        g.deck.discard(*discardCards)
        log.info("${r}抽取了三张角色牌：${roles.map { it.name }.toTypedArray().contentToString()}")
        g.resolve(executeHouLaiRen(fsm, r, message.remainCardId, roles))
    }

    private data class executeHouLaiRen(
        val fsm: WaitForChengQing,
        val r: Player,
        val remainCardId: Int,
        val roles: Array<RoleSkillsData>
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_hou_lai_ren_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.remainCardId = remainCardId
                    builder.waitingSecond = Config.WaitSecond * 2
                    if (p === r) {
                        roles.forEach { builder.addRoles(it.role) }
                        val seq2 = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_hou_lai_ren_b_tos.newBuilder()
                                builder2.role = roles.first().role
                                builder2.seq = seq2
                                g.tryContinueResolveProtocol(r, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val builder2 = skill_hou_lai_ren_b_tos.newBuilder()
                    builder2.role = roles.first().role
                    g.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_hou_lai_ren_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            val roleSkillsData = roles.find { it.role == message.role }
            if (roleSkillsData == null) {
                log.error("你没有这个角色")
                (r as? HumanPlayer)?.sendErrorMessage("你没有这个角色")
                return null
            }
            r.incrSeq()
            log.info("${r}变成了${roleSkillsData.name}")
            r.roleSkillsData = roleSkillsData
            r.roleSkillsData.skills.filterIsInstance<TriggeredSkill>().forEach { it.init(r.game!!) }
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_hou_lai_ren_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.role = if (r.roleFaceUp || p === r) r.role else unknown
                    p.send(builder.build())
                }
            }
            return ResolveResult(UseChengQingOnDying(fsm), true)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as executeHouLaiRen

            if (fsm != other.fsm) return false
            if (r != other.r) return false
            if (!roles.contentEquals(other.roles)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = fsm.hashCode()
            result = 31 * result + r.hashCode()
            result = 31 * result + roles.contentHashCode()
            return result
        }

        companion object {
            private val log = Logger.getLogger(executeHouLaiRen::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(HouLaiRen::class.java)
        fun ai(e: WaitForChengQing, skill: ActiveSkill): Boolean {
            val player = e.askWhom
            if (player !== e.whoDie) return false
            if (player.roleFaceUp) return false
            val card = player.messageCards.filter { !it.isPureBlack() }.randomOrNull() ?: return false
            GameExecutor.post(player.game!!, {
                val builder = skill_hou_lai_ren_a_tos.newBuilder()
                builder.remainCardId = card.id
                skill.executeProtocol(player.game!!, player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}