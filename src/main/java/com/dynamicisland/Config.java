package com.dynamicisland;

import com.dynamicisland.client.Theme;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端配置。所有值热读，改动后调用 Config.bake() 生效（配置界面/命令会自动触发）。
 */
public class Config {

    public interface Holder { }

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.EnumValue<Theme> THEME;
    public static final ModConfigSpec.DoubleValue SCALE;
    public static final ModConfigSpec.IntValue OFFSET_X;
    public static final ModConfigSpec.IntValue OFFSET_Y;
    public static final ModConfigSpec.DoubleValue OPACITY;
    public static final ModConfigSpec.DoubleValue LIGHT_OPACITY;
    public static final ModConfigSpec.DoubleValue ANIM_SPEED;
    public static final ModConfigSpec.BooleanValue SHADOW;

    public static final ModConfigSpec.BooleanValue SHOW_FPS;
    public static final ModConfigSpec.BooleanValue SHOW_MEM;
    public static final ModConfigSpec.BooleanValue SHOW_GRAPH;
    public static final ModConfigSpec.BooleanValue SHOW_TPS;
    public static final ModConfigSpec.BooleanValue SHOW_INFO;
    public static final ModConfigSpec.EnumValue<InfoMode> INFO_MODE;
    public static final ModConfigSpec.DoubleValue INFO_ROTATE;

    public static final ModConfigSpec.BooleanValue MODULE_MINING;
    public static final ModConfigSpec.BooleanValue MODULE_USE;
    public static final ModConfigSpec.BooleanValue MODULE_SENSE;
    public static final ModConfigSpec.BooleanValue MODULE_NOTICE;
    public static final ModConfigSpec.DoubleValue NOTICE_TIME;
    public static final ModConfigSpec.BooleanValue NOTICE_FULL;
    public static final ModConfigSpec.BooleanValue NOTICE_DURABILITY;
    public static final ModConfigSpec.BooleanValue NOTICE_EFFECT;
    public static final ModConfigSpec.BooleanValue NOTICE_MENTION;
    public static final ModConfigSpec.BooleanValue NOTICE_ADVANCEMENT;
    public static final ModConfigSpec.BooleanValue NOTICE_BLOCK;
    public static final ModConfigSpec.BooleanValue HIDE_ON_DEBUG;
    public static final ModConfigSpec.BooleanValue ROUND_CORNERS;

    public static final ModConfigSpec.BooleanValue MODULE_BUILD;
    public static final ModConfigSpec.IntValue BUILD_WARN;
    public static final ModConfigSpec.BooleanValue MODULE_RETICLE;
    public static final ModConfigSpec.BooleanValue MODULE_RETICLE_BLOCK;
    public static final ModConfigSpec.BooleanValue MODULE_ATTACK;
    public static final ModConfigSpec.BooleanValue MODULE_PICKUP;
    public static final ModConfigSpec.DoubleValue PICKUP_TIME;
    public static final ModConfigSpec.BooleanValue MODULE_HOTBAR;
    public static final ModConfigSpec.DoubleValue HOTBAR_TIME;
    public static final ModConfigSpec.BooleanValue MODULE_MUSIC;
    public static final ModConfigSpec.BooleanValue MODULE_DAYTIME;
    public static final ModConfigSpec.DoubleValue DAYTIME_HOLD;
    public static final ModConfigSpec.BooleanValue MODULE_DEPTH;
    public static final ModConfigSpec.DoubleValue DEPTH_HOLD;
    public static final ModConfigSpec.IntValue BLOCK_PANEL_X;
    public static final ModConfigSpec.IntValue BLOCK_PANEL_Y;
    public static final ModConfigSpec.BooleanValue BLOCK_PANEL_COORD;
    public static final ModConfigSpec.BooleanValue MODULE_DEATH;
    public static final ModConfigSpec.BooleanValue MODULE_COOLDOWN;
    public static final ModConfigSpec.BooleanValue MODULE_EFFECTS;
    public static final ModConfigSpec.BooleanValue MODULE_XP;
    public static final ModConfigSpec.BooleanValue MODULE_BLAST;
    public static final ModConfigSpec.BooleanValue MODULE_FALL;
    public static final ModConfigSpec.BooleanValue MODULE_RIDE;
    public static final ModConfigSpec.BooleanValue MODULE_TRADE;
    public static final ModConfigSpec.DoubleValue RIDE_MIN_SPEED;

    public static final ModConfigSpec.ConfigValue<String> COLOR_ISLAND;
    public static final ModConfigSpec.ConfigValue<String> COLOR_INFO;
    public static final ModConfigSpec.ConfigValue<String> COLOR_NOTICE;
    public static final ModConfigSpec.ConfigValue<String> COLOR_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> COLOR_FOCUS;
    public static final ModConfigSpec.ConfigValue<String> COLOR_MUSIC;

    public static final ModConfigSpec.BooleanValue MODULE_TRITIUM;
    public static final ModConfigSpec.DoubleValue TRITIUM_HOLD;
    public static final ModConfigSpec.DoubleValue TRITIUM_END_WARN;
    public static final ModConfigSpec.BooleanValue TRITIUM_COVER;

    public static final ModConfigSpec.BooleanValue FOCUS_ENABLED;
    public static final ModConfigSpec.DoubleValue FOCUS_HOLD;
    public static final ModConfigSpec.BooleanValue FOCUS_RIGHT_FIRST;
    public static final ModConfigSpec.DoubleValue FOCUS_GAP;

    public static final ModConfigSpec.BooleanValue FOCUS_MUSIC;
    public static final ModConfigSpec.BooleanValue FOCUS_DAY;
    public static final ModConfigSpec.BooleanValue FOCUS_DURABILITY;
    public static final ModConfigSpec.BooleanValue FOCUS_POTION;
    public static final ModConfigSpec.BooleanValue FOCUS_HUNGER;
    public static final ModConfigSpec.BooleanValue FOCUS_AIR;
    public static final ModConfigSpec.BooleanValue FOCUS_DROP;
    public static final ModConfigSpec.BooleanValue FOCUS_MARK;
    public static final ModConfigSpec.BooleanValue FOCUS_CURE;
    public static final ModConfigSpec.BooleanValue FOCUS_XP_ORB;
    public static final ModConfigSpec.BooleanValue FOCUS_CLOUD;
    public static final ModConfigSpec.BooleanValue FOCUS_ENDERMITE;
    public static final ModConfigSpec.BooleanValue NOTICE_DROP_WARN;
    public static final ModConfigSpec.BooleanValue NOTICE_SYSTEM;
    public static final ModConfigSpec.BooleanValue NOTICE_PLAYER;
    public static final ModConfigSpec.BooleanValue NOTICE_PET;
    public static final ModConfigSpec.BooleanValue NOTICE_HISTORY;
    public static final ModConfigSpec.IntValue NOTICE_SOUND;
    public static final ModConfigSpec.DoubleValue POTION_MIN_SEC;
    public static final ModConfigSpec.IntValue DURABILITY_MAX;
    public static final ModConfigSpec.DoubleValue DAY_LEAD_SEC;

    public enum InfoMode { COORDS, BIOME, TIME, SPEED, WEATHER, PLAYTIME, ENTITIES, STRUCTURE, ROTATE }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("general");
        ENABLED = b.comment("是否启用灵动岛").define("enabled", true);
        HIDE_ON_DEBUG = b.comment("打开 F3 调试屏幕时隐藏").define("hideOnDebug", true);
        b.pop();

        b.push("appearance");
        THEME = b.comment("皮肤").defineEnum("theme", Theme.BLACK_GLASS);
        SCALE = b.comment("整体缩放").defineInRange("scale", 1.0D, 0.5D, 2.5D);
        OFFSET_X = b.comment("相对屏幕顶部的水平偏移").defineInRange("offsetX", 0, -4000, 4000);
        OFFSET_Y = b.comment("相对屏幕顶部的垂直偏移").defineInRange("offsetY", 8, -2000, 2000);
        OPACITY = b.comment("背景不透明度").defineInRange("opacity", 0.82D, 0.0D, 1.0D);
        LIGHT_OPACITY = b.comment("浅色皮肤（半透明白）专用的背景不透明度，浅色底偏实会发闷，默认更透一些")
                .defineInRange("lightOpacity", 0.55D, 0.0D, 1.0D);
        ANIM_SPEED = b.comment("动画速度倍率").defineInRange("animSpeed", 1.0D, 0.3D, 4.0D);
        SHADOW = b.comment("胶囊阴影").define("shadow", true);
        ROUND_CORNERS = b.comment("圆角渲染（关闭可提升极低端机性能）").define("roundCorners", true);
        b.pop();

        b.push("idle");
        SHOW_FPS = b.define("showFps", true);
        SHOW_MEM = b.define("showMem", true);
        SHOW_GRAPH = b.define("showGraph", true);
        SHOW_TPS = b.define("showTps", false);
        SHOW_INFO = b.define("showInfo", true);
        INFO_MODE = b.defineEnum("infoMode", InfoMode.ROTATE);
        INFO_ROTATE = b.comment("轮播模式下每条信息停留秒数").defineInRange("infoRotate", 4.0D, 1.0D, 15.0D);
        b.pop();

        b.push("modules");
        MODULE_MINING = b.define("mining", true);
        MODULE_USE = b.define("itemUse", true);
        MODULE_SENSE = b.comment("血量/饥饿/氧气/燃烧/鞘翅/传送门等状态感知").define("sense", true);
        MODULE_NOTICE = b.define("notice", true);
        NOTICE_TIME = b.comment("单条通知停留秒数").defineInRange("noticeTime", 3.5D, 1.0D, 10.0D);
        b.pop();

        b.push("notices");
        NOTICE_FULL = b.comment("背包已满").define("fullInventory", true);
        NOTICE_DURABILITY = b.comment("装备耐久告急").define("durability", true);
        NOTICE_EFFECT = b.comment("药水效果即将结束").define("effectEnding", true);
        NOTICE_MENTION = b.comment("聊天里被 @ 到").define("mention", true);
        NOTICE_ADVANCEMENT = b.comment("达成进度").define("advancement", true);
        NOTICE_BLOCK = b.comment("方块即将用尽 / 已用完").define("block", true);
        b.pop();

        b.push("extras");
        MODULE_BUILD = b.comment("放置方块后短暂显示剩余数量").define("buildCount", true);
        BUILD_WARN = b.comment("剩余方块低于该值时弹通知提醒").defineInRange("buildWarn", 8, 1, 64);
        MODULE_RETICLE = b.comment("准星感知：显示准星指向的生物 / 容器信息").define("reticle", true);
        MODULE_RETICLE_BLOCK = b.comment("方块信息弹窗：准星指向方块时，用独立小窗显示硬度与所需工具")
                .define("reticleBlock", true);
        BLOCK_PANEL_X = b.comment("方块信息弹窗的水平位置（相对屏幕中心的像素偏移）").defineInRange("blockPanelX", 150, -2000, 2000);
        BLOCK_PANEL_Y = b.comment("方块信息弹窗的垂直位置（相对屏幕上边缘的像素偏移）").defineInRange("blockPanelY", 90, -1000, 2000);
        BLOCK_PANEL_COORD = b.comment("方块信息弹窗里显示坐标行").define("blockPanelCoord", true);
        MODULE_ATTACK = b.comment("攻击充能：用小信息栏显示，不占用胶囊").define("attackCharge", true);
        MODULE_PICKUP = b.comment("拾取物品上岛显示数量").define("pickup", true);
        PICKUP_TIME = b.comment("拾取提示停留秒数").defineInRange("pickupTime", 2.0D, 0.5D, 8.0D);
        MODULE_HOTBAR = b.comment("切换快捷栏时显示物品名与耐久").define("hotbar", true);
        HOTBAR_TIME = b.comment("快捷栏提示停留秒数").defineInRange("hotbarTime", 1.6D, 0.5D, 8.0D);
        MODULE_MUSIC = b.comment("唱片 / 背景音乐播放时在胶囊显示曲名与进度").define("music", true);
        MODULE_DAYTIME = b.comment("天气切换与昼夜关键时间点提醒").define("dayTime", true);
        DAYTIME_HOLD = b.comment("天气 / 时间提醒停留秒数").defineInRange("dayTimeHold", 3.0D, 1.0D, 10.0D);
        MODULE_DEPTH = b.comment("深层环境：Y 坐标矿物带与洞穴预警").define("depth", true);
        DEPTH_HOLD = b.comment("深度提示停留秒数").defineInRange("depthHold", 2.5D, 1.0D, 8.0D);
        MODULE_DEATH = b.comment("死亡点回溯：需在控制设置里绑定按键，按一次开启追踪，再按一次关闭").define("deathRecall", true);
        MODULE_COOLDOWN = b.comment("物品冷却环").define("itemCooldown", true);
        MODULE_EFFECTS = b.comment("药水效果队列").define("effectQueue", true);
        MODULE_XP = b.comment("获得经验时显示升级进度").define("xpProgress", true);
        MODULE_BLAST = b.comment("附近 TNT / 苦力怕引爆倒计时").define("blastCountdown", true);
        MODULE_FALL = b.comment("下落时预测落地伤害、是否致死与剩余时间").define("fallPredict", true);
        MODULE_RIDE = b.comment("矿车 / 船 / 鞘翅高速时显示实时速度").define("rideSpeed", true);
        MODULE_TRADE = b.comment("村民交易锁定后的补货倒计时").define("tradeRestock", true);
        RIDE_MIN_SPEED = b.comment("载具速度低于该值(km/h)就不展开").defineInRange("rideMinSpeed", 10.0D, 0.0D, 80.0D);
        b.pop();

        b.push("colors");
        COLOR_ISLAND = b.comment("灵动岛胶囊的强调色（#RRGGBB，或 auto 跟随皮肤）").define("colorIsland", "auto");
        COLOR_INFO = b.comment("下方信息条的强调色").define("colorInfo", "auto");
        COLOR_NOTICE = b.comment("通知卡片的强调色").define("colorNotice", "auto");
        COLOR_BLOCK = b.comment("方块信息弹窗的强调色").define("colorBlock", "auto");
        COLOR_FOCUS = b.comment("灵动焦点的强调色").define("colorFocus", "auto");
        COLOR_MUSIC = b.comment("音乐联动卡片的强调色（auto 且开着封面取色时会被封面主色覆盖）")
                .define("colorMusic", "auto");
        b.pop();

        b.push("tritium");
        MODULE_TRITIUM = b.comment("与 Tritium Music 模组联动（未安装该模组时自动静默跳过）")
                .define("tritiumLink", true);
        TRITIUM_HOLD = b.comment("歌曲开始 / 收尾提醒时，详细卡片在胶囊上停留的秒数，之后并入灵动焦点")
                .defineInRange("tritiumHold", 3.0D, 1.0D, 10.0D);
        TRITIUM_END_WARN = b.comment("距离播放结束还剩多少秒时，焦点自动收回胶囊提示")
                .defineInRange("tritiumEndWarn", 5.0D, 1.0D, 30.0D);
        TRITIUM_COVER = b.comment("灵动焦点中心显示专辑封面（关闭则显示剩余时间）")
                .define("tritiumCover", true);
        b.pop();

        b.push("focus");
        FOCUS_ENABLED = b.comment("灵动焦点：胶囊左右两侧的圆形小窗，承载需要长时间驻留的信息")
                .define("focusEnabled", true);
        FOCUS_HOLD = b.comment("信息在胶囊里详细停留的秒数，到时自动过渡到灵动焦点")
                .defineInRange("focusHold", 2.5D, 1.0D, 8.0D);
        FOCUS_RIGHT_FIRST = b.comment("只有一个焦点时放在胶囊右侧（关闭则放左侧）").define("focusRightFirst", true);
        FOCUS_GAP = b.comment("灵动焦点与胶囊之间的间距").defineInRange("focusGap", 6.0D, 2.0D, 20.0D);
        FOCUS_MUSIC = b.comment("唱片 / 音乐并入灵动焦点").define("focusMusic", true);
        FOCUS_DAY = b.comment("昼夜更替与天气并入灵动焦点").define("focusDay", true);
        FOCUS_DURABILITY = b.comment("装备耐久告急并入灵动焦点").define("focusDurability", true);
        FOCUS_POTION = b.comment("长效药水效果并入灵动焦点").define("focusPotion", true);
        FOCUS_HUNGER = b.comment("饥饿值过低并入灵动焦点").define("focusHunger", true);
        FOCUS_AIR = b.comment("氧气不足并入灵动焦点（生命值不并入，始终留在胶囊告警）").define("focusAir", true);
        FOCUS_DROP = b.comment("死亡掉落物回收倒计时并入灵动焦点").define("focusDrop", true);
        FOCUS_MARK = b.comment("被骷髅 / 光谱箭标记（发光）并入灵动焦点").define("focusMark", true);
        FOCUS_CURE = b.comment("僵尸村民治愈进度并入灵动焦点").define("focusCure", true);
        FOCUS_XP_ORB = b.comment("经验球消失倒计时并入灵动焦点（只统计够不着的那些，5 分钟后永久消失）")
                .define("focusXpOrb", true);
        FOCUS_CLOUD = b.comment("滞留药水 / 效果云（AreaEffectCloud）剩余时间并入灵动焦点")
                .define("focusCloud", true);
        FOCUS_ENDERMITE = b.comment("末影螨存活时间（2 分钟后自然死亡）并入灵动焦点")
                .define("focusEndermite", true);
        NOTICE_DROP_WARN = b.comment("掉落物最后 30 秒播报").define("noticeDropWarn", true);
        NOTICE_SYSTEM = b.comment("系统告警：内存过高 / 帧率骤降 / TPS 崩溃").define("noticeSystem", true);
        NOTICE_PLAYER = b.comment("玩家进出服务器提示").define("noticePlayer", true);
        NOTICE_PET = b.comment("宠物死亡 / 拴绳断裂提示").define("noticePet", true);
        NOTICE_HISTORY = b.comment("开启通知历史回看").define("noticeHistory", true);
        NOTICE_SOUND = b.comment("通知音效等级：0=关闭 1=仅紧急出声 2=全部出声")
                .defineInRange("noticeSound", 1, 0, 2);
        POTION_MIN_SEC = b.comment("药水时长超过该秒数才并入焦点").defineInRange("potionMinSec", 120.0D, 30.0D, 600.0D);
        DURABILITY_MAX = b.comment("装备剩余耐久低于该值时提醒并并入焦点").defineInRange("durabilityMax", 10, 1, 200);
        DAY_LEAD_SEC = b.comment("昼夜关键点提前多少秒开始预告").defineInRange("dayLeadSec", 8.0D, 5.0D, 10.0D);
        b.pop();

        SPEC = b.build();
    }

    // ---------- 热缓存：避免每帧解析 ----------
    public static boolean enabled = true, hideOnDebug = true, shadow = true, roundCorners = true;
    public static boolean showFps = true, showMem = true, showGraph = true, showTps = false, showInfo = true;
    public static boolean modMining = true, modUse = true, modSense = true, modNotice = true;
    public static Theme theme = Theme.BLACK_GLASS;
    public static InfoMode infoMode = InfoMode.ROTATE;
    public static float scale = 1.0F, opacity = 0.82F, lightOpacity = 0.55F, animSpeed = 1.0F, noticeTime = 3.5F;
    public static int offsetX = 0, offsetY = 8;

    public static boolean modBuild = true, modReticle = true, modReticleBlock = true, modDeath = true;
    public static boolean modCooldown = true, modEffects = true, modXp = true;
    public static int buildWarn = 8;
    public static boolean noticeFull = true, noticeDurability = true, noticeEffect = true;
    public static boolean noticeMention = true, noticeAdvancement = true, noticeBlock = true;
    public static float infoRotate = 4.0F;
    public static boolean blockPanelCoord = true;
    public static int blockPanelX = 150, blockPanelY = 90;
    public static boolean modAttack = true, modPickup = true, modHotbar = true;
    public static boolean modMusic = true, modDayTime = true, modDepth = true;
    public static boolean modBlast = true, modFall = true, modRide = true, modTrade = true;
    public static float rideMinSpeed = 10.0F;
    public static boolean modTritium = true, tritiumCover = true;
    public static float tritiumHold = 3.0F, tritiumEndWarn = 5.0F;
    public static int colorIsland = -1, colorInfo = -1, colorNotice = -1;
    public static int colorBlock = -1, colorFocus = -1, colorMusic = -1;
    public static float pickupTime = 2.0F, hotbarTime = 1.6F, dayTimeHold = 3.0F, depthHold = 2.5F;
    public static boolean focusEnabled = true, focusRightFirst = true;
    public static float focusHold = 2.5F, focusGap = 6.0F;
    public static boolean focusMusic = true, focusDay = true, focusDurability = true;
    public static boolean focusPotion = true, focusHunger = true, focusAir = true;
    public static boolean focusDrop = true, focusMark = true, focusCure = true;
    public static boolean focusXpOrb = true, focusCloud = true, focusEndermite = true;
    public static boolean noticeDropWarn = true, noticeSystem = true;
    public static boolean noticePlayer = true, noticePet = true, noticeHistory = true;
    public static int noticeSound = 1;
    public static float potionMinSec = 120.0F, dayLeadSec = 8.0F;
    public static int durabilityMax = 10;

    public static void bake() {
        enabled = ENABLED.get();
        hideOnDebug = HIDE_ON_DEBUG.get();
        shadow = SHADOW.get();
        roundCorners = ROUND_CORNERS.get();
        showFps = SHOW_FPS.get();
        showMem = SHOW_MEM.get();
        showGraph = SHOW_GRAPH.get();
        showTps = SHOW_TPS.get();
        showInfo = SHOW_INFO.get();
        infoMode = INFO_MODE.get();
        modMining = MODULE_MINING.get();
        modUse = MODULE_USE.get();
        modSense = MODULE_SENSE.get();
        modNotice = MODULE_NOTICE.get();
        theme = THEME.get();
        modBuild = MODULE_BUILD.get();
        buildWarn = BUILD_WARN.get();
        modReticle = MODULE_RETICLE.get();
        modReticleBlock = MODULE_RETICLE_BLOCK.get();
        blockPanelX = BLOCK_PANEL_X.get();
        blockPanelY = BLOCK_PANEL_Y.get();
        modAttack = MODULE_ATTACK.get();
        modPickup = MODULE_PICKUP.get();
        pickupTime = PICKUP_TIME.get().floatValue();
        modHotbar = MODULE_HOTBAR.get();
        hotbarTime = HOTBAR_TIME.get().floatValue();
        modMusic = MODULE_MUSIC.get();
        modDayTime = MODULE_DAYTIME.get();
        dayTimeHold = DAYTIME_HOLD.get().floatValue();
        modDepth = MODULE_DEPTH.get();
        depthHold = DEPTH_HOLD.get().floatValue();
        modDeath = MODULE_DEATH.get();
        modCooldown = MODULE_COOLDOWN.get();
        modEffects = MODULE_EFFECTS.get();
        modXp = MODULE_XP.get();
        modBlast = MODULE_BLAST.get();
        modFall = MODULE_FALL.get();
        modRide = MODULE_RIDE.get();
        modTrade = MODULE_TRADE.get();
        rideMinSpeed = RIDE_MIN_SPEED.get().floatValue();
        colorIsland = parseColor(COLOR_ISLAND.get());
        colorInfo = parseColor(COLOR_INFO.get());
        colorNotice = parseColor(COLOR_NOTICE.get());
        colorBlock = parseColor(COLOR_BLOCK.get());
        colorFocus = parseColor(COLOR_FOCUS.get());
        colorMusic = parseColor(COLOR_MUSIC.get());
        modTritium = MODULE_TRITIUM.get();
        tritiumHold = TRITIUM_HOLD.get().floatValue();
        tritiumEndWarn = TRITIUM_END_WARN.get().floatValue();
        tritiumCover = TRITIUM_COVER.get();
        focusEnabled = FOCUS_ENABLED.get();
        focusHold = FOCUS_HOLD.get().floatValue();
        focusRightFirst = FOCUS_RIGHT_FIRST.get();
        focusGap = FOCUS_GAP.get().floatValue();
        focusMusic = FOCUS_MUSIC.get();
        focusDay = FOCUS_DAY.get();
        focusDurability = FOCUS_DURABILITY.get();
        focusPotion = FOCUS_POTION.get();
        focusHunger = FOCUS_HUNGER.get();
        focusAir = FOCUS_AIR.get();
        focusDrop = FOCUS_DROP.get();
        focusMark = FOCUS_MARK.get();
        focusCure = FOCUS_CURE.get();
        focusXpOrb = FOCUS_XP_ORB.get();
        focusCloud = FOCUS_CLOUD.get();
        focusEndermite = FOCUS_ENDERMITE.get();
        noticeDropWarn = NOTICE_DROP_WARN.get();
        noticeSystem = NOTICE_SYSTEM.get();
        noticePlayer = NOTICE_PLAYER.get();
        noticePet = NOTICE_PET.get();
        noticeHistory = NOTICE_HISTORY.get();
        noticeSound = NOTICE_SOUND.get();
        potionMinSec = POTION_MIN_SEC.get().floatValue();
        dayLeadSec = DAY_LEAD_SEC.get().floatValue();
        durabilityMax = DURABILITY_MAX.get();
        scale = SCALE.get().floatValue();
        opacity = OPACITY.get().floatValue();
        lightOpacity = LIGHT_OPACITY.get().floatValue();
        animSpeed = ANIM_SPEED.get().floatValue();
        noticeTime = NOTICE_TIME.get().floatValue();
        noticeFull = NOTICE_FULL.get();
        noticeDurability = NOTICE_DURABILITY.get();
        noticeEffect = NOTICE_EFFECT.get();
        noticeMention = NOTICE_MENTION.get();
        noticeAdvancement = NOTICE_ADVANCEMENT.get();
        noticeBlock = NOTICE_BLOCK.get();
        infoRotate = INFO_ROTATE.get().floatValue();
        blockPanelCoord = BLOCK_PANEL_COORD.get();
        offsetX = OFFSET_X.get();
        offsetY = OFFSET_Y.get();
    }

    /** "#RRGGBB" / "RRGGBB" / "auto" → 0xRRGGBB，-1 表示跟随皮肤 */
    public static int parseColor(String s) {
        if (s == null) return -1;
        String v = s.trim();
        if (v.isEmpty() || v.equalsIgnoreCase("auto")) return -1;
        if (v.startsWith("#")) v = v.substring(1);
        if (v.startsWith("0x") || v.startsWith("0X")) v = v.substring(2);
        try {
            return Integer.parseInt(v, 16) & 0xFFFFFF;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 0xRRGGBB / -1 → "#RRGGBB" / "auto" */
    public static String hex(int c) {
        if (c < 0) return "auto";
        return "#" + String.format("%06X", c & 0xFFFFFF);
    }

    /** 板块强调色：-1 时回退到皮肤自带强调色 */
    public static int accentOf(int own, Theme th) {
        return own < 0 ? th.accent : own;
    }

    /**
     * 当前皮肤实际使用的背景不透明度。
     * 浅色皮肤默认更透（底色发亮，太实会糊住背后的天空），可在设置面板里单独调整。
     */
    public static float bgOpacity() {
        return theme == Theme.LIGHT_FROST ? lightOpacity : opacity;
    }

    public static void save() {
        ENABLED.set(enabled);
        HIDE_ON_DEBUG.set(hideOnDebug);
        SHADOW.set(shadow);
        ROUND_CORNERS.set(roundCorners);
        SHOW_FPS.set(showFps);
        SHOW_MEM.set(showMem);
        SHOW_GRAPH.set(showGraph);
        SHOW_TPS.set(showTps);
        SHOW_INFO.set(showInfo);
        INFO_MODE.set(infoMode);
        MODULE_MINING.set(modMining);
        MODULE_USE.set(modUse);
        MODULE_SENSE.set(modSense);
        MODULE_NOTICE.set(modNotice);
        THEME.set(theme);
        MODULE_BUILD.set(modBuild);
        BUILD_WARN.set(buildWarn);
        MODULE_RETICLE.set(modReticle);
        MODULE_RETICLE_BLOCK.set(modReticleBlock);
        BLOCK_PANEL_X.set(blockPanelX);
        BLOCK_PANEL_Y.set(blockPanelY);
        MODULE_ATTACK.set(modAttack);
        MODULE_PICKUP.set(modPickup);
        PICKUP_TIME.set((double) pickupTime);
        MODULE_HOTBAR.set(modHotbar);
        HOTBAR_TIME.set((double) hotbarTime);
        MODULE_MUSIC.set(modMusic);
        MODULE_DAYTIME.set(modDayTime);
        DAYTIME_HOLD.set((double) dayTimeHold);
        MODULE_DEPTH.set(modDepth);
        DEPTH_HOLD.set((double) depthHold);
        MODULE_DEATH.set(modDeath);
        MODULE_COOLDOWN.set(modCooldown);
        MODULE_EFFECTS.set(modEffects);
        MODULE_XP.set(modXp);
        MODULE_BLAST.set(modBlast);
        MODULE_FALL.set(modFall);
        MODULE_RIDE.set(modRide);
        MODULE_TRADE.set(modTrade);
        RIDE_MIN_SPEED.set((double) rideMinSpeed);
        COLOR_ISLAND.set(hex(colorIsland));
        COLOR_INFO.set(hex(colorInfo));
        COLOR_NOTICE.set(hex(colorNotice));
        COLOR_BLOCK.set(hex(colorBlock));
        COLOR_FOCUS.set(hex(colorFocus));
        COLOR_MUSIC.set(hex(colorMusic));
        MODULE_TRITIUM.set(modTritium);
        TRITIUM_HOLD.set((double) tritiumHold);
        TRITIUM_END_WARN.set((double) tritiumEndWarn);
        TRITIUM_COVER.set(tritiumCover);
        FOCUS_ENABLED.set(focusEnabled);
        FOCUS_HOLD.set((double) focusHold);
        FOCUS_RIGHT_FIRST.set(focusRightFirst);
        FOCUS_GAP.set((double) focusGap);
        FOCUS_MUSIC.set(focusMusic);
        FOCUS_DAY.set(focusDay);
        FOCUS_DURABILITY.set(focusDurability);
        FOCUS_POTION.set(focusPotion);
        FOCUS_HUNGER.set(focusHunger);
        FOCUS_AIR.set(focusAir);
        FOCUS_DROP.set(focusDrop);
        FOCUS_MARK.set(focusMark);
        FOCUS_CURE.set(focusCure);
        FOCUS_XP_ORB.set(focusXpOrb);
        FOCUS_CLOUD.set(focusCloud);
        FOCUS_ENDERMITE.set(focusEndermite);
        NOTICE_DROP_WARN.set(noticeDropWarn);
        NOTICE_SYSTEM.set(noticeSystem);
        NOTICE_PLAYER.set(noticePlayer);
        NOTICE_PET.set(noticePet);
        NOTICE_HISTORY.set(noticeHistory);
        NOTICE_SOUND.set(noticeSound);
        POTION_MIN_SEC.set((double) potionMinSec);
        DAY_LEAD_SEC.set((double) dayLeadSec);
        DURABILITY_MAX.set(durabilityMax);
        SCALE.set((double) scale);
        OPACITY.set((double) opacity);
        LIGHT_OPACITY.set((double) lightOpacity);
        ANIM_SPEED.set((double) animSpeed);
        NOTICE_TIME.set((double) noticeTime);
        NOTICE_FULL.set(noticeFull);
        NOTICE_DURABILITY.set(noticeDurability);
        NOTICE_EFFECT.set(noticeEffect);
        NOTICE_MENTION.set(noticeMention);
        NOTICE_ADVANCEMENT.set(noticeAdvancement);
        NOTICE_BLOCK.set(noticeBlock);
        INFO_ROTATE.set((double) infoRotate);
        BLOCK_PANEL_COORD.set(blockPanelCoord);
        OFFSET_X.set(offsetX);
        OFFSET_Y.set(offsetY);
        SPEC.save();
    }
}
