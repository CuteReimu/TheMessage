package com.fengsheng.skill

import com.fengsheng.protos.Common.role

/**
 * 记录所有角色的工具类
 */
object RoleCache {
    private val cache = listOf(
        RoleSkillsData("端木静", role.duan_mu_jing, true, true, XinSiChao()),
        RoleSkillsData("金生火", role.jin_sheng_huo, false, true, JinShen()),
        RoleSkillsData("老鳖", role.lao_bie, false, true, LianLuo(), MingEr()),
        RoleSkillsData("毛不拔", role.mao_bu_ba, false, true, QiHuoKeJu()),
        RoleSkillsData("邵秀", role.shao_xiu, true, true, MianLiCangZhen()),
        RoleSkillsData("肥原龙川", role.fei_yuan_long_chuan, false, true, GuiZha()),
        RoleSkillsData("王魁", role.wang_kui, false, true, YiYaHuanYa()),
        RoleSkillsData("鄭文先", role.zheng_wen_xian, false, false, TouTian(), HuanRi()),
        RoleSkillsData("韩梅", role.han_mei, true, false, YiHuaJieMu()),
        RoleSkillsData("白菲菲", role.bai_fei_fei, true, true, LianMin(), FuHei()),
        RoleSkillsData("老汉", role.lao_han, true, true, ShiSi(), RuGui()),
        RoleSkillsData("顾小梦", role.gu_xiao_meng, true, false, JiZhi(), ChengZhi(), WeiSheng()),
        RoleSkillsData("李宁玉", role.li_ning_yu, true, false, JiuJi(), ChengFu(), YiXin()),
        RoleSkillsData("程小蝶", role.cheng_xiao_die, false, true, ZhiYin(), JingMeng()),
        RoleSkillsData("商玉", role.shang_yu, true, false, JieDaoShaRen()),
        RoleSkillsData("裴玲", role.pei_ling, true, true, JiaoJi()),
        RoleSkillsData("鬼脚", role.gui_jiao, false, true, JiSong()),
        RoleSkillsData("白小年", role.bai_xiao_nian, false, true, ZhuanJiao()),
        RoleSkillsData("连鸢", role.lian_yuan, true, false, MiaoBiQiaoBian()),
        RoleSkillsData("王田香", role.wang_tian_xiang, false, true, JinBi()),
        RoleSkillsData("玄青子", role.xuan_qing_zi, false, true, JinKouYiKai()),
        RoleSkillsData("白沧浪", role.bai_cang_lang, false, true, BoAi()),
        RoleSkillsData("小九", role.xiao_jiu, false, false, GuangFaBao()),
        RoleSkillsData("张一挺", role.zhang_yi_ting, false, true, QiangLing()),
        RoleSkillsData("吴志国", role.wu_zhi_guo, false, true, JianRen()),
        RoleSkillsData("阿芙罗拉", role.a_fu_luo_la, true, false, MiaoShou()),
        RoleSkillsData("李醒", role.li_xing, false, false, SouJi()),
        RoleSkillsData("王富贵", role.wang_fu_gui, false, true, JiangHuLing()),
        RoleSkillsData("黄济仁", role.huang_ji_ren, false, false, DuiZhengXiaYao()),
        RoleSkillsData("白昆山", role.bai_kun_shan, false, false, DuJi()),
        RoleSkillsData("SP顾小梦", role.sp_gu_xiao_meng, true, true, JiBan()),
        RoleSkillsData("SP李宁玉", role.sp_li_ning_yu, true, true, YingBian(), YouDao()),
        RoleSkillsData("玛利亚", role.ma_li_ya, true, true, CangShenJiaoTang()),
        RoleSkillsData("钱敏", role.qian_min, false, false, XianFaZhiRen()),
        RoleSkillsData("SP韩梅", role.sp_han_mei, true, true, LengXueXunLian()),
        RoleSkillsData("池镜海", role.chi_jing_hai, false, true, GuanHai(), BiFeng()),
        RoleSkillsData("秦圆圆", role.qin_yuan_yuan, true, false, ZuoYouFengYuan(), BiYiShuangFei()),
        RoleSkillsData("SP连鸢", role.sp_lian_yuan, true, true, TanQiuZhenLi()),
//        RoleSkillsData("盛老板", role.sheng_lao_ban, false, false, RuBiZhiShi(), ShenCang()),
        RoleSkillsData("SP程小蝶", role.sp_cheng_xiao_die, false, false, GongFen()),
        RoleSkillsData("高桥智子", role.gao_qiao_zhi_zi, true, true, HuoXin()),
        RoleSkillsData("简先生", role.jian_xian_sheng, false, true, CongRongYingDui()),
        RoleSkillsData("SP小九", role.sp_xiao_jiu, false, true, ChiZiZhiXin()),
        RoleSkillsData("老虎", role.lao_hu, false, false, YunChouWeiWo()),
    )
    private val mapCache = HashMap<role, RoleSkillsData>()

    init {
        for (data in cache) {
            if (mapCache.put(data.role, data) != null) throw RuntimeException("重复的角色：" + data.role)
        }
    }

    /**
     * @return 长度为 `n` 的数组
     */
    fun getRandomRoles(n: Int): Array<RoleSkillsData> {
        val indexArray = Array(cache.size) { i -> i }
        indexArray.shuffle()
        return Array(n) { i -> if (i < indexArray.size) cache[indexArray[i]] else RoleSkillsData() }
    }

    /**
     * @param roles 返回数组的前几个角色强行指定
     * @return 长度为 `n` 的数组
     */
    fun getRandomRolesWithSpecific(n: Int, roles: List<role>): Array<RoleSkillsData> {
        val roleSkillsDataArray = getRandomRoles(n)
        var roleIndex = 0
        while (roleIndex < roles.size && roleIndex < n) {
            var index = -1
            for (i in roleSkillsDataArray.indices) {
                if (roles[roleIndex] == roleSkillsDataArray[i].role) {
                    index = i
                    break
                }
            }
            if (index == -1) {
                val data = mapCache[roles[roleIndex]]
                roleSkillsDataArray[roleIndex] = data ?: RoleSkillsData()
            } else {
                val temp = roleSkillsDataArray[index]
                roleSkillsDataArray[index] = roleSkillsDataArray[roleIndex]
                roleSkillsDataArray[roleIndex] = temp
            }
            roleIndex++
        }
        return roleSkillsDataArray
    }

    fun getRoleName(role: role?): String? {
        val roleSkillsData = mapCache[role]
        return roleSkillsData?.name
    }
}