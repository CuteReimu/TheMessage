package com.fengsheng.skill

enum class SkillId(private val cName: String) {
    LIAN_LUO("联络"), MING_ER("明饵"), XIN_SI_CHAO("新思潮"), MIAN_LI_CANG_ZHEN("绵里藏针"), QI_HUO_KE_JU("奇货可居"),
    JIN_SHEN("谨慎"), GUI_ZHA("诡诈"), YI_YA_HUAN_YA("以牙还牙"), TOU_TIAN("偷天"), HUAN_RI("换日"),
    YI_HUA_JIE_MU("移花接木"), LIAN_MIN("怜悯"), FU_HEI("腹黑"), SHI_SI("视死"), RU_GUI("如归"),
    JI_ZHI("集智"), CHENG_ZHI("承志"), WEI_SHENG("尾声"), JIU_JI("就计"), JIU_JI2("就计2"), CHENG_FU("城府"), YI_XIN("遗信"),
    ZHI_YIN("知音"), JING_MENG("惊梦"), JIE_DAO_SHA_REN("借刀杀人"), JIAO_JI("交际"), JI_SONG("急送"),
    ZHUAN_JIAO("转交"), MIAO_BI_QIAO_BIAN("妙笔巧辩"), JIN_BI("禁闭"), INVALID("被无效"), JIN_KOU_YI_KAI("金口一开"),
    JI_BAN("羁绊"), YING_BIAN("应变"), YOU_DAO("诱导"), BO_AI("博爱"), GUANG_FA_BAO("广发报"),
    QIANG_LING("强令"), JIAN_REN("坚韧"), MIAO_SHOU("妙手"), SOU_JI("搜辑"), JIANG_HU_LING("江湖令"),
    JIANG_HU_LING2("江湖令2"), DUI_ZHENG_XIA_YAO("对症下药"), DU_JI("毒计"), CANG_SHEN_JIAO_TANG("藏身教堂"),
    XIAN_FA_ZHI_REN("先发制人"), LENG_XUE_XUN_LIAN("冷血训练"), GUAN_HAI("观海"), BI_FENG("避风"), ZUO_YOU_FENG_YUAN("左右逢源"),
    BI_YI_SHUANG_FEI("比翼双飞"), TAN_QIU_ZHEN_LI("探求真理"), RU_BI_ZHI_SHI("如臂指使"), SHEN_CANG("深藏"), GONG_FEN("共焚"),
    HUO_XIN("惑心"), CONG_RONG_YING_DUI("从容应对"), CHI_ZI_ZHI_XIN("赤子之心"), YUN_CHOU_WEI_WO("运筹帷幄"),
    HOU_LAI_REN("后来人"), ZI_ZHENG_QING_BAI("自证清白"), SHOU_KOU_RU_PING("守口如瓶"), DU_MING("赌命"), LIAN_LUO2("联络"),
    SHUN_SHI_ER_WEI("顺势而为"), MI_XIN("密信"), JIANG_JI_JIU_JI("将计就计"), HAN_HOU_LAO_SHI("憨厚老实"),
    MIAO_SHOU_KUAI_JI("妙手快记"), YU_SI_WANG_PO("鱼死网破");


    override fun toString(): String {
        return cName
    }
}