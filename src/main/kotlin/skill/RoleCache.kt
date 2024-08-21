package com.fengsheng.skill

import com.fengsheng.protos.Common.role
import com.fengsheng.protos.Common.role.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.kotlin.logger
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

/**
 * 记录所有角色的工具类
 */
object RoleCache {
    private val mu = Mutex()
    private val cache = arrayListOf(
        RoleSkillsData("端木静", duan_mu_jing, true, true, XinSiChao()),
        RoleSkillsData("金生火", jin_sheng_huo, false, true, JinShen()),
        RoleSkillsData("老鳖", lao_bie, false, true, LianLuo(), MingEr()),
        RoleSkillsData("毛不拔", mao_bu_ba, false, true, QiHuoKeJu()),
        RoleSkillsData("邵秀", shao_xiu, true, true, MianLiCangZhen()),
        RoleSkillsData("肥原龙川", fei_yuan_long_chuan, false, true, GuiZha()),
        RoleSkillsData("王魁", wang_kui, false, true, YiYaHuanYa()),
        RoleSkillsData("鄭文先", zheng_wen_xian, false, false, TouTian(), HuanRi()),
        RoleSkillsData("韩梅", han_mei, true, false, YiHuaJieMu()),
        RoleSkillsData("白菲菲", bai_fei_fei, true, true, LianMin(), FuHei()),
        RoleSkillsData("老汉", lao_han, true, true, ShiSi(), RuGui()),
        RoleSkillsData("顾小梦", gu_xiao_meng, true, false, JiZhi(), ChengZhi(), WeiSheng()),
        RoleSkillsData("李宁玉", li_ning_yu, true, false, JiuJi(), ChengFu(), YiXin()),
        RoleSkillsData("程小蝶", cheng_xiao_die, false, true, ZhiYin(), JingMeng()),
        RoleSkillsData("商玉", shang_yu, true, false, JieDaoShaRen()),
        RoleSkillsData("裴玲", pei_ling, true, true, JiaoJi()),
        RoleSkillsData("鬼脚", gui_jiao, false, true, JiSong()),
        RoleSkillsData("白小年", bai_xiao_nian, false, true, ZhuanJiao()),
        RoleSkillsData("连鸢", lian_yuan, true, false, MiaoBiQiaoBian()),
        RoleSkillsData("王田香", wang_tian_xiang, false, true, JinBi()),
        RoleSkillsData("玄青子", xuan_qing_zi, false, true, JinKouYiKai()),
        RoleSkillsData("白沧浪", bai_cang_lang, false, true, BoAi()),
        RoleSkillsData("小九", xiao_jiu, false, false, GuangFaBao()),
        RoleSkillsData("张一挺", zhang_yi_ting, false, true, QiangLing()),
        RoleSkillsData("吴志国", wu_zhi_guo, false, true, JianRen()),
        RoleSkillsData("阿芙罗拉", a_fu_luo_la, true, false, MiaoShou()),
        RoleSkillsData("李醒", li_xing, false, false, SouJi()),
        RoleSkillsData("王富贵", wang_fu_gui, false, true, JiangHuLing()),
        RoleSkillsData("黄济仁", huang_ji_ren, false, false, DuiZhengXiaYao()),
        RoleSkillsData("白昆山", bai_kun_shan, false, false, DuJi()),
        RoleSkillsData("SP顾小梦", sp_gu_xiao_meng, true, true, JiBan()),
        RoleSkillsData("SP李宁玉", sp_li_ning_yu, true, true, YingBian(), YouDao()),
        RoleSkillsData("玛利亚", ma_li_ya, true, true, CangShenJiaoTang()),
        RoleSkillsData("钱敏", qian_min, false, false, XianFaZhiRen()),
        RoleSkillsData("青年韩梅", sp_han_mei, true, true, LengXueXunLian()),
        RoleSkillsData("池镜海", chi_jing_hai, false, true, GuanHai(), BiFeng()),
        RoleSkillsData("秦圆圆", qin_yuan_yuan, true, false, ZuoYouFengYuan(), BiYiShuangFei()),
        RoleSkillsData("SP连鸢", sp_lian_yuan, true, true, TanQiuZhenLi()),
        RoleSkillsData("盛老板", sheng_lao_ban, false, false, RuBiZhiShi(), ShenCang()),
        RoleSkillsData("SP程小蝶", sp_cheng_xiao_die, false, false, GongFen()),
        RoleSkillsData("高桥智子", gao_qiao_zhi_zi, true, true, HuoXin()),
        RoleSkillsData("简先生", jian_xian_sheng, false, true, CongRongYingDui()),
        RoleSkillsData("青年小九", sp_xiao_jiu, false, true, ChiZiZhiXin()),
        RoleSkillsData("老虎", lao_hu, false, false, YunChouWeiWo()),
        RoleSkillsData("SP端木静", sp_duan_mu_jing, true, false, HouLaiRen()),
        RoleSkillsData("陈安娜", chen_an_na, true, true, ZiZhengQingBai(), YiWenAnHao()),
        RoleSkillsData("哑炮", ya_pao, false, true, ShouKouRuPing(), HanHouLaoShi()),
        RoleSkillsData("金自来", jin_zi_lai, false, true, DuMing()),
        RoleSkillsData("成年小九", adult_xiao_jiu, false, false, LianXin(), ShunShiErWei()),
        RoleSkillsData("成年韩梅", adult_han_mei, true, false, LianXin(), JiangJiJiuJi()),
        RoleSkillsData("秦无命", qin_wu_ming, false, true, PinMingSanLang(), YuSiWangPo()),
        RoleSkillsData("李书云", li_shu_yun, true, false, DingLun(), ZhenLi()),
        RoleSkillsData("间谍阿芙罗拉", sp_a_fu_luo_la, true, false, YingBianZiRu(), HunShuiMoYu()),
        RoleSkillsData("SP白菲菲", sp_bai_fei_fei, true, true, TaoQu()),
        RoleSkillsData("凌素秋", ling_su_qiu, true, true, TanXuBianShi(), CunBuBuRang()),
        RoleSkillsData("小铃铛", xiao_ling_dang, true, true, XinGeLianLuo(), HouZiQieXin()),
        RoleSkillsData("陈大耳", chen_da_er, false, true, BianZeTong()),
        RoleSkillsData("边云疆", bian_yun_jiang, false, true, YouDiShenRu(), JianDiFengXing()),
        RoleSkillsData("孙守謨", sun_shou_mo, false, true, XiangJinSiSuo(), QiangYingXiaLing()),
        RoleSkillsData("王响", huo_che_si_ji, false, true, JieCheYunHuo(), WorkersAreKnowledgable()),
        RoleSkillsData("SP小九", cp_xiao_jiu, false, true, ZhuangZhiManHuai(), YiZhongRen()),
        RoleSkillsData("SP韩梅", cp_han_mei, true, true, AnCangShaJi(), BaiYueGuang()),
    )
    private val mapCache: Map<role, RoleSkillsData>
    private val pool = Channel<() -> Unit>(Channel.UNLIMITED)
    private val forbiddenRoleCache = ArrayList<RoleSkillsData>()

    init {
        mapCache = EnumMap(role::class.java)
        for (data in cache) {
            if (mapCache.put(data.role, data) != null) throw RuntimeException("重复的角色：${data.role}")
        }
        try {
            FileInputStream("forbiddenRoles.txt").use { `in` ->
                String(`in`.readAllBytes()).trim().split(",").forEach {
                    if (it.isNotEmpty()) {
                        val r = forNumber(it.toInt())!!
                        val index = cache.indexOfFirst { data -> data.role == r }
                        if (index < 0) throw RuntimeException("找不到角色：$r")
                        forbiddenRoleCache.add(cache.removeAt(index))
                    }
                }
            }
        } catch (_: FileNotFoundException) {
            // Do Nothing
        }
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            while (true) {
                val f = pool.receive()
                withContext(Dispatchers.IO) { f() }
            }
        }
    }

    fun forbidRole(roleName: String): Boolean = runBlocking {
        val (result, s) = mu.withLock {
            if (forbiddenRoleCache.any { it.name == roleName }) return@withLock true to null
            val index = cache.indexOfFirst { it.name == roleName }
            if (index < 0) return@withLock false to null
            forbiddenRoleCache.add(cache.removeAt(index))
            true to forbiddenRoleCache.joinToString(separator = ",") { it.role.number.toString() }
        }
        if (s != null) writeForbiddenRolesFile(s.toByteArray())
        result
    }

    fun releaseRole(roleName: String): Boolean = runBlocking {
        val (result, s) = mu.withLock {
            if (cache.any { it.name == roleName }) return@withLock true to null
            val index = forbiddenRoleCache.indexOfFirst { it.name == roleName }
            if (index < 0) return@withLock false to null
            cache.add(forbiddenRoleCache.removeAt(index))
            true to forbiddenRoleCache.joinToString(separator = ",") { it.role.number.toString() }
        }
        if (s != null) writeForbiddenRolesFile(s.toByteArray())
        result
    }

    /**
     * @return 长度为 `n` 的列表
     */
    fun getRandomRoles(n: Int): List<RoleSkillsData> = runBlocking {
        mu.withLock {
            val indexList = cache.indices.shuffled()
            List(n) { i -> if (i < indexList.size) cache[indexList[i]] else RoleSkillsData() }
        }
    }

    /**
     * @param except 排除的角色
     * @return 长度为 `n` 的列表
     */
    fun getRandomRoles(n: Int, except: Set<role>): List<RoleSkillsData> = runBlocking {
        mu.withLock {
            val cache = this@RoleCache.cache.filterNot { it.role in except }
            val indexList = cache.indices.shuffled()
            List(n) { i -> if (i < indexList.size) cache[indexList[i]] else RoleSkillsData() }
        }
    }

    /**
     * @param roles 返回数组的前几个角色强行指定
     * @return 长度为 `n` 的列表
     */
    fun getRandomRolesWithSpecific(n: Int, roles: List<role>): List<RoleSkillsData> {
        val roleSkillsDataList = getRandomRoles(n).toMutableList()
        var roleIndex = 0
        while (roleIndex < roles.size && roleIndex < n) {
            val index = roleSkillsDataList.indexOfFirst { it.role == roles[roleIndex] }
            if (index < 0) {
                val data = mapCache[roles[roleIndex]]
                roleSkillsDataList[roleIndex] = data ?: RoleSkillsData()
            } else {
                val temp = roleSkillsDataList[index]
                roleSkillsDataList[index] = roleSkillsDataList[roleIndex]
                roleSkillsDataList[roleIndex] = temp
            }
            roleIndex++
        }
        return roleSkillsDataList
    }

    fun getRoleName(role: role) = mapCache[role]?.name
    fun getRoleSkillsData(role: role) = mapCache[role]

    fun filterForbidRoles(roles: Collection<role>) = runBlocking {
        mu.withLock {
            roles.filter { role -> !forbiddenRoleCache.any { it.role == role } }
        }
    }

    fun randRoleName() = mapCache.values.randomOrNull()?.name

    private fun writeForbiddenRolesFile(buf: ByteArray) {
        pool.trySend {
            try {
                FileOutputStream("forbiddenRoles.txt").use { fileOutputStream -> fileOutputStream.write(buf) }
            } catch (e: IOException) {
                logger.error("write file failed", e)
            }
        }
    }
}
