package com.fengsheng.skill;

import com.fengsheng.protos.Common;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.fengsheng.protos.Common.role.*;

/**
 * 记录所有角色的工具类
 */
public final class RoleCache {
    private static final List<RoleSkillsData> cache = List.of(
            new RoleSkillsData("端木静", duan_mu_jing, true, true, new XinSiChao()),
            new RoleSkillsData("金生火", jin_sheng_huo, false, true, new JinShen()),
            new RoleSkillsData("老鳖", lao_bie, false, true, new LianLuo(), new MingEr()),
            new RoleSkillsData("毛不拔", mao_bu_ba, false, true, new QiHuoKeJu()),
            new RoleSkillsData("邵秀", shao_xiu, true, true, new MianLiCangZhen()),
            new RoleSkillsData("肥原龙川", fei_yuan_long_chuan, false, true, new GuiZha()),
            new RoleSkillsData("王魁", wang_kui, false, true, new YiYaHuanYa()),
            new RoleSkillsData("鄭文先", zheng_wen_xian, false, false, new TouTian(), new HuanRi()),
            new RoleSkillsData("韩梅", han_mei, true, false, new YiHuaJieMu()),
            new RoleSkillsData("白菲菲", bai_fei_fei, true, true, new LianMin(), new FuHei()),
            new RoleSkillsData("老汉", lao_han, true, true, new ShiSi(), new RuGui()),
            new RoleSkillsData("顾小梦", gu_xiao_meng, true, false, new JiZhi(), new ChengZhi(), new WeiSheng()),
            new RoleSkillsData("李宁玉", li_ning_yu, true, false, new JiuJi(), new ChengFu(), new YiXin()),
            new RoleSkillsData("程小蝶", cheng_xiao_die, false, true, new ZhiYin(), new JingMeng()),
            new RoleSkillsData("商玉", shang_yu, true, false, new JieDaoShaRen()),
            new RoleSkillsData("裴玲", pei_ling, true, true, new JiaoJi()),
            new RoleSkillsData("鬼脚", gui_jiao, false, true, new JiSong()),
            new RoleSkillsData("白小年", bai_xiao_nian, false, true, new ZhuanJiao()),
            new RoleSkillsData("连鸢", lian_yuan, true, false, new MiaoBiQiaoBian()),
            new RoleSkillsData("王田香", wang_tian_xiang, false, true, new JinBi()),
            new RoleSkillsData("玄青子", xuan_qing_zi, false, true, new JinKouYiKai()),
            new RoleSkillsData("白沧浪", bai_cang_lang, false, true, new BoAi()),
            new RoleSkillsData("小九", xiao_jiu, false, false, new GuangFaBao()),
            new RoleSkillsData("张一挺", zhang_yi_ting, false, true, new QiangLing()),
            new RoleSkillsData("吴志国", wu_zhi_guo, false, true, new JianRen()),
            new RoleSkillsData("阿芙罗拉", a_fu_luo_la, true, false, new MiaoShou()),
            new RoleSkillsData("李醒", li_xing, false, false, new SouJi()),
            new RoleSkillsData("王富贵", wang_fu_gui, false, true, new JiangHuLing()),
            new RoleSkillsData("黄济仁", huang_ji_ren, false, false, new DuiZhengXiaYao()),
            new RoleSkillsData("白昆山", bai_kun_shan, false, false, new DuJi())
    );

    private static final EnumMap<Common.role, RoleSkillsData> mapCache = new EnumMap<>(Common.role.class);

    static {
        for (RoleSkillsData data : cache) {
            if (mapCache.put(data.getRole(), data) != null)
                throw new RuntimeException("重复的角色：" + data.getRole());
        }
    }

    private RoleCache() {

    }

    /**
     * @return 长度为 {@code n} 的数组
     */
    public static RoleSkillsData[] getRandomRoles(int n) {
        RoleSkillsData[] result = new RoleSkillsData[n];
        Random random = ThreadLocalRandom.current();
        Integer[] indexArray = new Integer[cache.size()];
        for (int i = 0; i < indexArray.length; i++)
            indexArray[i] = i;
        Collections.shuffle(Arrays.asList(indexArray), random);
        for (int i = 0; i < n; i++)
            result[i] = i < indexArray.length ? cache.get(indexArray[i]) : new RoleSkillsData();
        return result;
    }


    /**
     * @param roles 返回数组的前几个角色强行指定
     * @return 长度为 {@code n} 的数组
     */
    public static RoleSkillsData[] getRandomRolesWithSpecific(int n, List<Common.role> roles) {
        RoleSkillsData[] roleSkillsDataArray = getRandomRoles(n);
        for (int roleIndex = 0; roleIndex < roles.size() && roleIndex < n; roleIndex++) {
            int index = -1;
            for (int i = 0; i < roleSkillsDataArray.length; i++) {
                if (roles.get(roleIndex) == roleSkillsDataArray[i].getRole()) {
                    index = i;
                    break;
                }
            }
            if (index == -1) {
                RoleSkillsData data = mapCache.get(roles.get(roleIndex));
                roleSkillsDataArray[roleIndex] = data != null ? data : new RoleSkillsData();
            } else {
                RoleSkillsData temp = roleSkillsDataArray[index];
                roleSkillsDataArray[index] = roleSkillsDataArray[roleIndex];
                roleSkillsDataArray[roleIndex] = temp;
            }
        }
        return roleSkillsDataArray;
    }

    public static String getRoleName(Common.role role) {
        RoleSkillsData roleSkillsData = mapCache.get(role);
        return roleSkillsData == null ? null : roleSkillsData.getName();
    }
}
