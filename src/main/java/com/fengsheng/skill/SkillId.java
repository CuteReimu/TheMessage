package com.fengsheng.skill;

public enum SkillId {
    LIAN_LUO("联络"), MING_ER("明饵"), XIN_SI_CHAO("新思潮"), MIAN_LI_CANG_ZHEN("绵里藏针"),
    QI_HUO_KE_JU("奇货可居"), JIN_SHEN("谨慎"), GUI_ZHA("诡诈"), YI_YA_HUAN_YA("以牙还牙"),
    TOU_TIAN("偷天"), HUAN_RI("换日"), YI_HUA_JIE_MU("移花接木"), LIAN_MIN("怜悯"),
    FU_HEI("腹黑"), SHI_SI("视死"), RU_GUI("如归"), JI_ZHI("集智"), CHENG_ZHI("承志"),
    WEI_SHENG("尾声"), JIU_JI("就计"), CHENG_FU("城府"), YI_XIN("遗信"), ZHI_YIN("知音"),
    JING_MENG("惊梦"), JIE_DAO_SHA_REN("借刀杀人"), JIAO_JI("交际"), JI_SONG("急送"), ZHUAN_JIAO("转交"),
    MIAO_BI_QIAO_BIAN("妙笔巧辩"), JIN_BI("禁闭"), BEI_JIN_BI("被禁闭");

    private final String name;

    SkillId(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
