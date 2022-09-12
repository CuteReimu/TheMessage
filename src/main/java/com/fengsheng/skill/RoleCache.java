package com.fengsheng.skill;

import com.fengsheng.protos.Common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static com.fengsheng.protos.Common.role.*;

/**
 * 记录所有角色的工具类
 */
public final class RoleCache {
    private static final List<RoleSkillsData> cache = List.of(
            new RoleSkillsData("端木静", duan_mu_jing, true, new XinSiChao()),
            new RoleSkillsData("金生火", jin_sheng_huo, true, new JinShen()),
            new RoleSkillsData("老鳖", lao_bie, true, new LianLuo(), new MingEr()),
            new RoleSkillsData("毛不拔", mao_bu_ba, true, new QiHuoKeJu()),
            new RoleSkillsData("邵秀", shao_xiu, true, new MianLiCangZhen()),
            new RoleSkillsData("肥原龙川", fei_yuan_long_chuan, true, new GuiZha()),
            new RoleSkillsData("王魁", wang_kui, true, new YiYaHuanYa()),
            new RoleSkillsData("鄭文先", zheng_wen_xian, false, new TouTian(), new HuanRi()),
            new RoleSkillsData("韩梅", han_mei, false, new YiHuaJieMu()),
            new RoleSkillsData("白菲菲", bai_fei_fei, true, new LianMin(), new FuHei())
    );

    private static final Map<Common.role, RoleSkillsData> mapCache = new HashMap<>();

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
        int[] indexArray = new int[Math.max(cache.size(), n)];
        for (int i = 0; i < indexArray.length; i++)
            indexArray[i] = i;
        for (int i = 0; i < n; i++) {
            int index = random.nextInt(i, indexArray.length);
            if (i != index) {
                int temp = indexArray[i];
                indexArray[i] = indexArray[index];
                indexArray[index] = temp;
            }
        }
        for (int i = 0; i < n; i++)
            result[i] = indexArray[i] < cache.size() ? cache.get(indexArray[i]) : new RoleSkillsData();
        return result;
    }


    /**
     * @param roles 返回数组的前几个角色强行指定
     * @return 长度为 {@code n} 的数组
     */
    public static RoleSkillsData[] getRandomRolesWithSpecific(int n, Common.role... roles) {
        RoleSkillsData[] roleSkillsDataArray = getRandomRoles(n);
        for (int roleIndex = 0; roleIndex < roles.length; roleIndex++) {
            int index = -1;
            for (int i = 0; i < roleSkillsDataArray.length; i++) {
                if (roles[roleIndex] == roleSkillsDataArray[i].getRole()) {
                    index = i;
                    break;
                }
            }
            if (index == -1) {
                RoleSkillsData data = mapCache.get(roles[roleIndex]);
                roleSkillsDataArray[roleIndex] = data != null ? data : new RoleSkillsData();
            } else {
                RoleSkillsData temp = roleSkillsDataArray[index];
                roleSkillsDataArray[index] = roleSkillsDataArray[roleIndex];
                roleSkillsDataArray[roleIndex] = temp;
            }
        }
        return roleSkillsDataArray;
    }
}
