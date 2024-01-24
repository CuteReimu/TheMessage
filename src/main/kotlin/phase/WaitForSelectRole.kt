package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.role
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.skill.RoleSkillsData
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 等待玩家选择角色
 */
data class WaitForSelectRole(val game: Game, val options: List<List<RoleSkillsData>>) : WaitingFsm {
    private val selected: Array<RoleSkillsData?> = arrayOfNulls(game.players.size)
    private val whoseTurn = Random.nextInt(game.players.size)

    override fun resolve(): ResolveResult? {
        for (player in game.players) {
            if (player is HumanPlayer) {
                if (player.needWaitLoad)
                    player.send(game_start_toc.getDefaultInstance())
                else
                    notifySelectRole(player)
                player.timeout = GameExecutor.post(game, {
                    val autoSelect = options[player.location].firstOrNull()?.role ?: role.unknown
                    game.tryContinueResolveProtocol(
                        player,
                        select_role_tos.newBuilder().setRole(autoSelect).build()
                    )
                }, player.getWaitSeconds(Config.WaitSecond * 2 + 2).toLong(), TimeUnit.SECONDS)
            } else {
                selected[player!!.location] = options[player.location].run {
                    if (Config.IsGmEnable) return@run firstOrNull()
                    find { it.role == role.shang_yu } ?: find { it.role == role.ya_pao }
                    ?: maxByOrNull { it.role.number % 1000 + it.role.number / 1000 * 30 }
                } ?: RoleSkillsData()
                player.roleSkillsData = selected[player.location]!!
                player.originRole = selected[player.location]!!.role
            }
        }
        for (role in selected) if (role == null) return null
        return ResolveResult(StartGame(game, whoseTurn), true)
    }

    override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
        if (message !is select_role_tos) {
            logger.error("正在等待选择角色")
            (player as? HumanPlayer)?.sendErrorMessage("正在等待选择角色")
            return null
        }
        if (selected[player.location] != null) {
            logger.error("你已经选了角色")
            (player as? HumanPlayer)?.sendErrorMessage("你已经选了角色")
            return null
        }
        val roleSkillsData =
            if (message.role == role.unknown && options[player.location].isEmpty()) RoleSkillsData()
            else options[player.location].find { o -> o.role == message.role }
        if (roleSkillsData == null) {
            logger.error("你没有这个角色")
            (player as? HumanPlayer)?.sendErrorMessage("你没有这个角色")
            return null
        }
        player.incrSeq()
        selected[player.location] = roleSkillsData
        player.roleSkillsData = roleSkillsData
        player.originRole = roleSkillsData.role
        (player as? HumanPlayer)?.send(select_role_toc.newBuilder().setRole(roleSkillsData.role).build())
        for (role in selected) if (role == null) return null
        return ResolveResult(StartGame(game, whoseTurn), true)
    }

    fun notifySelectRole(player: HumanPlayer) {
        val builder = wait_for_select_role_toc.newBuilder()
        builder.playerCount = game.players.size
        builder.identity = player.identity
        builder.secretTask = player.secretTask
        builder.addAllRoles(options[player.location].map { it.role }.ifEmpty { listOf(role.unknown) })
        builder.waitingSecond = Config.WaitSecond * 2
        builder.addAllPossibleSecretTask(game.possibleSecretTasks)
        builder.position = player.getAbstractLocation(whoseTurn) + 1
        player.send(builder.build())
        if (game.players.size < 5)
            player.notifyIdentity()
    }

    companion object {
        private fun HumanPlayer.notifyIdentity() {
            GameExecutor.post(game!!, {
                sendErrorMessage(
                    when (game!!.players.size) {
                        2 -> "2人局中双方身份完全随机"
                        3 -> "3人局中身份完全随机，但不会出现相同阵营，也不会所有人都是神秘人"
                        else -> "4人局有两名神秘人，当潜伏或军情宣胜时，另一方会共同胜利"
                    }
                )
            }, 1, TimeUnit.SECONDS)
        }
    }
}