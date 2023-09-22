package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.role
import com.fengsheng.protos.Common.secret_task.*
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
                if (player.needWaitLoad)
                    player.send(game_start_toc.getDefaultInstance())
                else
                    notifySelectRole(player)
                player.timeout =
                    GameExecutor.post(game, {
                        val autoSelect = options[player.location].firstOrNull()?.role ?: role.unknown
                        game.tryContinueResolveProtocol(
                            player,
                            select_role_tos.newBuilder().setRole(autoSelect).build()
                        )
                    }, player.getWaitSeconds(Config.WaitSecond * 2 + 2).toLong(), TimeUnit.SECONDS)
            } else {
                val prefer = if (player!!.identity == color.Black) blackPrefer else redBluePrefer
                val disgust = if (player.identity == color.Black) blackDisgust else redBlueDisgust
                selected[player.location] = options[player.location].run {
                    prefer.forEach { role -> find { o -> o.role == role }?.let { o -> return@run o } }
                    filterNot { it.role in disgust }.ifEmpty { this }.firstOrNull() ?: RoleSkillsData()
                }
                player.roleSkillsData = selected[player.location]!!
                player.originRole = selected[player.location]!!.role
            }
        }
        for (role in selected) if (role == null) return null
        return ResolveResult(StartGame(game), true)
    }

    override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
        if (message !is select_role_tos) {
            log.error("正在等待选择角色")
            (player as? HumanPlayer)?.sendErrorMessage("正在等待选择角色")
            return null
        }
        if (selected[player.location] != null) {
            log.error("你已经选了角色")
            (player as? HumanPlayer)?.sendErrorMessage("你已经选了角色")
            return null
        }
        val roleSkillsData =
            if (message.role == role.unknown && options[player.location].isEmpty()) RoleSkillsData()
            else options[player.location].find { o -> o.role == message.role }
        if (roleSkillsData == null) {
            log.error("你没有这个角色")
            (player as? HumanPlayer)?.sendErrorMessage("你没有这个角色")
            return null
        }
        player.incrSeq()
        selected[player.location] = roleSkillsData
        player.roleSkillsData = roleSkillsData
        player.originRole = roleSkillsData.role
        (player as? HumanPlayer)?.send(select_role_toc.newBuilder().setRole(roleSkillsData.role).build())
        for (role in selected) if (role == null) return null
        return ResolveResult(StartGame(game), true)
    }

    fun notifySelectRole(player: HumanPlayer) {
        val builder = wait_for_select_role_toc.newBuilder()
        builder.playerCount = game.players.size
        builder.identity = player.identity
        builder.secretTask = player.secretTask
        builder.addAllRoles(options[player.location].map { it.role }.ifEmpty { listOf(role.unknown) })
        builder.waitingSecond = Config.WaitSecond * 2
        builder.addAllPossibleSecretTask(game.possibleSecretTasks)
        player.send(builder.build())
        player.notifyPossibleSecretTasks()
    }

    companion object {
        private val log = Logger.getLogger(WaitForSelectRole::class.java)

        private val blackPrefer = listOf(role.shang_yu)
        private val redBluePrefer = blackPrefer + listOf(role.xiao_jiu, role.sp_gu_xiao_meng, role.bai_xiao_nian)
        private val redBlueDisgust = listOf(role.jin_sheng_huo, role.mao_bu_ba, role.wang_tian_xiang)
        private val blackDisgust = redBlueDisgust + listOf(role.xiao_jiu, role.sp_gu_xiao_meng, role.bai_xiao_nian)
        private val allTasks = listOf(Killer, Stealer, Collector, Mutator, Pioneer, Disturber, Sweeper)

        private fun HumanPlayer.notifyPossibleSecretTasks() {
            GameExecutor.post(game!!, {
                sendErrorMessage(when {
                    game!!.possibleSecretTasks.size <= 4 ->
                        game!!.possibleSecretTasks.joinToString(
                            separator = "",
                            prefix = "本局游戏中的神秘人将从以下随机："
                        ) {
                            Player.identityColorToString(color.Black, it)
                                .replaceFirst("神秘人", "")
                                .replaceFirst("[", "【")
                                .replaceFirst("]", "】")
                        }

                    game!!.possibleSecretTasks.size < allTasks.size ->
                        (allTasks - game!!.possibleSecretTasks.toSet()).joinToString(
                            separator = "",
                            prefix = "本局游戏已排除以下神秘人："
                        ) {
                            Player.identityColorToString(color.Black, it)
                                .replaceFirst("神秘人", "")
                                .replaceFirst("[", "【")
                                .replaceFirst("]", "】")
                        }

                    else -> "本局游戏的神秘人将会从${allTasks.size}种任务中随机"
                })
            }, 1, TimeUnit.SECONDS)
        }
    }
}