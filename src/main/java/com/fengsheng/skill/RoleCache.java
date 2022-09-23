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
            new RoleSkillsData("白小年", bai_xiao_nian, false, true, new ZhuanJiao())
    );

    private static final EnumMap<Common.role, RoleSkillsData> mapCache = new EnumMap<>(Common.role.class);

    static {
        for (RoleSkillsData data : cache)
            mapCache.put(data.getRole(), data);
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
        for (int roleIndex = 0; roleIndex < roles.size(); roleIndex++) {
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

    public static String randName() {
        return cache.get(ThreadLocalRandom.current().nextInt(cache.size())).getName();
    }
}
