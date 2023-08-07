package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.OnAddMessageCard
import com.fengsheng.protos.Role.skill_xian_fa_zhi_ren_a_tos
import com.fengsheng.protos.Role.wait_for_skill_xian_fa_zhi_ren_a_toc
import com.google.protobuf.GeneratedMessageV3
import java.util.concurrent.TimeUnit

/**
 * 钱敏技能【先发制人】：一张牌因角色技能置入情报区后，或争夺阶段，你可以翻开此角色，然后弃置一名角色情报区的一张情报，并令一张角色牌本回合所有技能无效，若其是面朝下的隐藏角色牌，你可以将其翻开。
 */
class XianFaZhiRen : AbstractSkill(), ActiveSkill, TriggeredSkill {
    override val skillId = SkillId.XIAN_FA_ZHI_REN

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? OnAddMessageCard ?: return null
        val r = fsm.resolvingWhom
        if (r.findSkill(skillId) == null || !r.alive) return null
        if (r.roleFaceUp) return null
        if (g.players.all { it!!.messageCards.isEmpty() }) return null
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = wait_for_skill_xian_fa_zhi_ren_a_toc.newBuilder()
                builder.waitingSecond = 15
                if (p === r) {
                    val seq2 = p.seq
                    builder.seq = seq2
                    p.timeout = GameExecutor.post(g, {
                        if (p.checkSeq(seq2)) {
                            val builder2 = skill_xian_fa_zhi_ren_a_tos.newBuilder()
                            builder2.enable = false
                            g.tryContinueResolveProtocol(p, builder2.build())
                        }
                    }, p.getWaitSeconds(builder.waitingSecond).toLong(), TimeUnit.SECONDS)
                }
            }
        }
        if (r is RobotPlayer) {
            GameExecutor.post(g, {
                val builder2 = skill_xian_fa_zhi_ren_a_tos.newBuilder()
                builder2.enable = false
                g.tryContinueResolveProtocol(p, builder2.build())
            }, 2, TimeUnit.SECONDS)
        }
    }

    data class executeXianFaZhiRenA(val fsm: Fsm) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            TODO("Not yet implemented")
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            TODO("Not yet implemented")
        }
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        TODO("Not yet implemented")
    }

    data class executeXianFaZhiRenB(val fsm: Fsm) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            TODO("Not yet implemented")
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            TODO("Not yet implemented")
        }
    }
}