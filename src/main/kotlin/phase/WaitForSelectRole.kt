package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.role
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.skill.RoleSkillsData
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 等待玩家选择角色
 */
data class WaitForSelectRole(val game: Game, val options: List<List<RoleSkillsData>>) : WaitingFsm {
    private val selected: Array<RoleSkillsData?> = arrayOfNulls(game.players.size)
    override fun resolve(): ResolveResult? {
        for (player in game.players) {
            if (player is HumanPlayer) {
                val builder = wait_for_select_role_toc.newBuilder()
                builder.playerCount = game.players.size
                builder.identity = player.identity
                builder.secretTask = player.secretTask
                builder.addAllRoles(options[player.location].map { it.role }.ifEmpty { listOf(role.unknown) })
                builder.waitingSecond = 30
                player.send(builder.build())
                player.timeout =
                    GameExecutor.post(game, {
                        game.tryContinueResolveProtocol(
                            player,
                            select_role_tos.newBuilder().setRole(builder.getRoles(0)).build()
                        )
                    }, player.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                selected[player!!.location] = options[player.location].run {
                    robotPrefer.forEach { role -> find { o -> o.role == role }?.let { o -> return@run o } }
                    filterNot { it.role in robotDisgust }.ifEmpty { this }.firstOrNull() ?: RoleSkillsData()
                }
                player.roleSkillsData = selected[player.location]!!
            }
        }
        for (role in selected) if (role == null) return null
        return ResolveResult(StartGame(game), true)
    }

    override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
        if (message !is select_role_tos) {
            log.error("正在等待选择角色")
            return null
        }
        if (player.role != role.unknown) {
            log.error("你已经选了角色")
            return null
        }
        val roleSkillsData = options[player.location].find { o -> o.role == message.role }
        if (roleSkillsData == null) {
            log.error("你没有这个角色")
            return null
        }
        player.incrSeq()
        selected[player.location] = roleSkillsData
        player.roleSkillsData = roleSkillsData
        (player as? HumanPlayer)?.send(select_role_toc.newBuilder().setRole(roleSkillsData.role).build())
        for (role in selected) if (role == null) return null
        return ResolveResult(StartGame(game), true)
    }

    companion object {
        private val log = Logger.getLogger(WaitForSelectRole::class.java)

        private val robotPrefer = listOf(role.shang_yu)
        private val robotDisgust = listOf(role.jin_sheng_huo, role.mao_bu_ba, role.wang_tian_xiang)
    }
}