package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.role.*
import com.fengsheng.protos.Fengsheng.select_role_tos
import com.fengsheng.protos.gameStartToc
import com.fengsheng.protos.selectRoleToc
import com.fengsheng.protos.selectRoleTos
import com.fengsheng.protos.waitForSelectRoleToc
import com.fengsheng.skill.RoleCache
import com.fengsheng.skill.RoleSkillsData
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 等待玩家选择角色
 */
data class WaitForSelectRole(val game: Game, val options: List<List<RoleSkillsData>>) : WaitingFsm {
    private val selected = MutableList<RoleSkillsData?>(game.players.size) { null }
    override val whoseTurn = game.players.random()!!

    override fun resolve(): ResolveResult? {
        for (player in game.players) {
            if (player is HumanPlayer) {
                if (player.needWaitLoad)
                    player.send(gameStartToc { })
                else
                    notifySelectRole(player)
                player.timeout = GameExecutor.post(game, {
                    game.tryContinueResolveProtocol(player, selectRoleTos {
                        role = options[player.location].firstOrNull()?.role ?: unknown
                    })
                }, player.getWaitSeconds(Config.WaitSecond * 2 + 2).toLong(), TimeUnit.SECONDS)
            } else {
                selected[player!!.location] = options[player.location].run {
                    if (Config.IsGmEnable) return@run firstOrNull()
                    val aiPreferRole = aiPreferRole.toMutableSet()
                    if (player.identity == Black) {
                        aiPreferRole -= sp_gu_xiao_meng
                    }
                    if (player.identity == Blue) {
                        aiPreferRole -= cp_xiao_jiu
                    }
                    if (player.identity == Red) {
                        aiPreferRole -= cp_han_mei
                    }
                    filter { it.role in aiPreferRole }.ifEmpty {
                        RoleCache.filterForbidRoles(aiPreferRole).filter {
                            options.all { option -> option.all { o -> it != o.role } } &&
                                selected.all { o -> it != o?.role }
                        }.map { RoleCache.getRoleSkillsData(it) }
                    }.ifEmpty { this }.randomOrNull()
                } ?: RoleSkillsData()
                player.roleSkillsData = selected[player.location]!!
                player.originRole = selected[player.location]!!.role
            }
        }
        game.playTime = System.currentTimeMillis()
        for (role in selected) if (role == null) return null
        return ResolveResult(StartGame(game, whoseTurn), true)
    }

    override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
        if (message !is select_role_tos) {
            logger.error("正在等待选择角色")
            player.sendErrorMessage("正在等待选择角色")
            return null
        }
        if (selected[player.location] != null) {
            logger.error("你已经选了角色")
            player.sendErrorMessage("你已经选了角色")
            return null
        }
        val roleSkillsData =
            if (message.role == unknown && options[player.location].isEmpty()) RoleSkillsData()
            else options[player.location].find { o -> o.role == message.role }
        if (roleSkillsData == null) {
            logger.error("你没有这个角色")
            player.sendErrorMessage("你没有这个角色")
            return null
        }
        player.incrSeq()
        selected[player.location] = roleSkillsData
        player.roleSkillsData = roleSkillsData
        player.originRole = roleSkillsData.role
        player.send(selectRoleToc { role = roleSkillsData.role })
        for (role in selected) if (role == null) return null
        return ResolveResult(StartGame(game, whoseTurn), true)
    }

    fun notifySelectRole(player: HumanPlayer) {
        player.send(waitForSelectRoleToc {
            playerCount = game.players.size
            identity = player.identity
            secretTask = player.secretTask
            roles.addAll(options[player.location].map { it.role }.ifEmpty { listOf(unknown) })
            waitingSecond = Config.WaitSecond * 2
            possibleSecretTask.addAll(game.possibleSecretTasks)
        })
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

        private val aiPreferRole = setOf(
            duan_mu_jing,
            lao_bie,
            shao_xiu,
            fei_yuan_long_chuan,
            wang_kui,
            zheng_wen_xian,
            lao_han,
            gu_xiao_meng,
            li_ning_yu,
            cheng_xiao_die,
            bai_xiao_nian,
            shang_yu,
            li_xing,
            pei_ling,
            xuan_qing_zi,
            bai_cang_lang,
            bai_fei_fei,
            xiao_jiu,
            zhang_yi_ting,
            wang_fu_gui,
            sp_gu_xiao_meng,
            sp_li_ning_yu,
            sp_han_mei,
            ma_li_ya,
            wang_tian_xiang,
            qin_yuan_yuan,
            sp_cheng_xiao_die,
            gao_qiao_zhi_zi,
            sheng_lao_ban,
            jian_xian_sheng,
            sp_xiao_jiu,
            qian_min,
            lao_hu,
            chen_an_na,
            ya_pao,
            jin_zi_lai,
            adult_xiao_jiu,
            adult_han_mei,
            sp_a_fu_luo_la,
            qin_wu_ming,
            li_shu_yun,
            ling_su_qiu,
            xiao_ling_dang,
            chen_da_er,
            sun_shou_mo,
            huo_che_si_ji,
            cp_xiao_jiu,
            cp_han_mei,
            huang_ji_ren,
            sp_bai_fei_fei,
            han_mei,
            lian_yuan,
            a_fu_luo_la,
        )
    }
}
