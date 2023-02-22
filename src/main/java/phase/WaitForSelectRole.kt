package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.roleimport

com.fengsheng.protos.Fengshengimport com.fengsheng.protos.Fengsheng.wait_for_select_role_tocimport com.fengsheng.skill.JiBanimport com.fengsheng.skill.RoleSkillsDataimport com.fengsheng.skill.YingBianimport com.fengsheng.skill.YouDaoimport java.util.* com.google.protobuf.GeneratedMessageV3
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 等待玩家选择角色
 */
class WaitForSelectRole(private val game: Game, private val options: Array<RoleSkillsData?>?) : WaitingFsm {
    private val selected: Array<RoleSkillsData?>
    override fun resolve(): ResolveResult? {
        for (player in game.players) {
            if (player is HumanPlayer) {
                val builder = wait_for_select_role_toc.newBuilder()
                builder.playerCount = game.players.size
                builder.identity = player.getIdentity()
                builder.secretTask = player.getSecretTask()
                val role1 = options!![player.location()].getRole()
                val role2 = options[player.location() + game.players.size].getRole()
                if (role1 == role.unknown) {
                    builderAddRole(builder, role2)
                } else {
                    builderAddRole(builder, role1)
                    if (role2 != role.unknown) builderAddRole(builder, role2)
                }
                builder.waitingSecond = 30
                player.send(builder.build())
                player.setTimeout(
                    GameExecutor.Companion.post(
                        game,
                        Runnable {
                            game.tryContinueResolveProtocol(
                                player,
                                Fengsheng.select_role_tos.newBuilder().setRole(builder.getRoles(0)).build()
                            )
                        },
                        player.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                        TimeUnit.SECONDS
                    )
                )
            } else {
                val spRole = spMap[options!![player.location()].getRole()]
                if (spRole != null && ThreadLocalRandom.current().nextBoolean()) selected[player.location()] =
                    spRole else selected[player.location()] = options[player.location()]
                player.setRoleSkillsData(selected[player.location()])
            }
        }
        for (role in selected) if (role == null) return null
        return ResolveResult(StartGame(game), true)
    }

    override fun resolveProtocol(p: Player, message: GeneratedMessageV3): ResolveResult? {
        if (message !is Fengsheng.select_role_tos) {
            log.error("正在等待选择角色")
            return null
        }
        if (p.role != role.unknown) {
            log.error("你已经选了角色")
            return null
        }
        val roleSkillsData = getRole(p.location(), message.role)
        if (roleSkillsData == null) {
            log.error("你没有这个角色")
            return null
        }
        p.incrSeq()
        selected[p.location()] = roleSkillsData
        p.setRoleSkillsData(roleSkillsData)
        (p as? HumanPlayer)?.send(Fengsheng.select_role_toc.newBuilder().setRole(roleSkillsData.role).build())
        for (role in selected) if (role == null) return null
        return ResolveResult(StartGame(game), true)
    }

    private fun getRole(location: Int, role: role): RoleSkillsData? {
        if (options!![location].getRole() == role) return options[location] else if (options[location + options.size / 2].getRole() == role) return options[location + options.size / 2]
        var spRoleSkillsData = spMap[options[location].getRole()]
        if (spRoleSkillsData != null && spRoleSkillsData.role == role) return spRoleSkillsData
        spRoleSkillsData = spMap[options[location + options.size / 2].getRole()]
        return if (spRoleSkillsData != null && spRoleSkillsData.role == role) spRoleSkillsData else null
    }

    init {
        selected = arrayOfNulls(game.players.size)
    }

    companion object {
        private val log = Logger.getLogger(WaitForSelectRole::class.java)
        private fun builderAddRole(builder: wait_for_select_role_toc.Builder, role: role?) {
            builder.addRoles(role)
            val spRoleSkillsData = spMap[role]
            if (spRoleSkillsData != null) builder.addRoles(spRoleSkillsData.role)
        }

        private val spMap = EnumMap<role?, RoleSkillsData>(role::class.java)

        init {
            spMap[role.gu_xiao_meng] =
                RoleSkillsData("SP顾小梦", role.sp_gu_xiao_meng, true, true, JiBan())
            spMap[role.li_ning_yu] =
                RoleSkillsData("SP李宁玉", role.sp_li_ning_yu, true, true, YingBian(), YouDao())
        }

        fun getRoleName(role: role): String? {
            for (roleSkillsData in spMap.values) {
                if (roleSkillsData.role == role) return roleSkillsData.name
            }
            return null
        }
    }
}