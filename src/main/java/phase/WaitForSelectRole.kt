package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.role
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.wait_for_select_role_toc
import com.fengsheng.skill.JiBan
import com.fengsheng.skill.RoleSkillsData
import com.fengsheng.skill.YingBian
import com.fengsheng.skill.YouDao
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

/**
 * 等待玩家选择角色
 */
data class WaitForSelectRole(val game: Game, val options: Array<RoleSkillsData>) : WaitingFsm {
    private val selected: Array<RoleSkillsData?> = arrayOfNulls(game.players.size)
    override fun resolve(): ResolveResult? {
        for (player in game.players) {
            if (player is HumanPlayer) {
                val builder = wait_for_select_role_toc.newBuilder()
                builder.playerCount = game.players.size
                builder.identity = player.identity
                builder.secretTask = player.secretTask
                val role1 = options[player.location].role
                val role2 = options[player.location + game.players.size].role
                if (role1 == role.unknown) {
                    builderAddRole(builder, role2)
                } else {
                    builderAddRole(builder, role1)
                    if (role2 != role.unknown) builderAddRole(builder, role2)
                }
                builder.waitingSecond = 30
                player.send(builder.build())
                player.timeout =
                    GameExecutor.post(game, {
                        game.tryContinueResolveProtocol(
                            player,
                            Fengsheng.select_role_tos.newBuilder().setRole(builder.getRoles(0)).build()
                        )
                    }, player.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                val spRole = spMap[options[player!!.location].role]
                if (spRole != null && ThreadLocalRandom.current().nextBoolean()) selected[player.location] =
                    spRole else selected[player.location] = options[player.location]
                player.roleSkillsData = selected[player.location]!!
            }
        }
        for (role in selected) if (role == null) return null
        return ResolveResult(StartGame(game), true)
    }

    override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
        if (message !is Fengsheng.select_role_tos) {
            log.error("正在等待选择角色")
            return null
        }
        if (player.role != role.unknown) {
            log.error("你已经选了角色")
            return null
        }
        val roleSkillsData = getRole(player.location, message.role)
        if (roleSkillsData == null) {
            log.error("你没有这个角色")
            return null
        }
        player.incrSeq()
        selected[player.location] = roleSkillsData
        player.roleSkillsData = roleSkillsData
        (player as? HumanPlayer)?.send(Fengsheng.select_role_toc.newBuilder().setRole(roleSkillsData.role).build())
        for (role in selected) if (role == null) return null
        return ResolveResult(StartGame(game), true)
    }

    private fun getRole(location: Int, role: role): RoleSkillsData? {
        if (options[location].role == role) return options[location] else if (options[location + options.size / 2].role == role) return options[location + options.size / 2]
        var spRoleSkillsData = spMap[options[location].role]
        if (spRoleSkillsData != null && spRoleSkillsData.role == role) return spRoleSkillsData
        spRoleSkillsData = spMap[options[location + options.size / 2].role]
        return if (spRoleSkillsData != null && spRoleSkillsData.role == role) spRoleSkillsData else null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WaitForSelectRole

        if (game != other.game) return false
        if (!options.contentEquals(other.options)) return false
        if (!selected.contentEquals(other.selected)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = game.hashCode()
        result = 31 * result + options.contentHashCode()
        result = 31 * result + selected.contentHashCode()
        return result
    }

    companion object {
        private val log = Logger.getLogger(WaitForSelectRole::class.java)
        private fun builderAddRole(builder: wait_for_select_role_toc.Builder, role: role?) {
            builder.addRoles(role)
            val spRoleSkillsData = spMap[role]
            if (spRoleSkillsData != null) builder.addRoles(spRoleSkillsData.role)
        }

        private val spMap = hashMapOf(
            Pair(
                role.gu_xiao_meng,
                RoleSkillsData("SP顾小梦", role.sp_gu_xiao_meng, true, true, JiBan())
            ),
            Pair(
                role.li_ning_yu,
                RoleSkillsData("SP李宁玉", role.sp_li_ning_yu, true, true, YingBian(), YouDao())
            )
        )

        fun getRoleName(role: role): String? {
            for (roleSkillsData in spMap.values) {
                if (roleSkillsData.role == role) return roleSkillsData.name
            }
            return null
        }
    }
}