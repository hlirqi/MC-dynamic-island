package com.dynamicisland.client;

import com.dynamicisland.Config;
import com.dynamicisland.mixin.ExperienceOrbAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * 经验球消失倒计时：和掉落物是**同一套机制**（存活 6000 tick = 5 分钟），
 * 但原版对经验球一个字都不说 —— 打完一群怪退开以后，地上那摊经验球什么时候
 * 会悄无声息地消失，全靠它告诉你。
 *
 * 只统计「够不着」的经验球（超出拾取跟随范围的）：贴身那几颗马上就会被吸走，
 * 给它们倒计时没有意义，也免得每次捡经验都上台刷一次。
 *
 * 触发流程遵守两段式模型：附近出现够不着的经验球 → 先在胶囊上提示 focusHold 秒
 * （先上岛）→ 随后并入灵动焦点用圆环持续显示（再进焦点）。
 */
public class XpOrbWatch {

    public static String label = "";
    public static String sub = "";
    public static String center = "";
    public static float progress = 0f;
    public static boolean active = false;
    public static ItemStack icon = ItemStack.EMPTY;

    /** 原版经验球存活 6000 tick = 5 分钟（与掉落物同） */
    private static final int LIFE_TICKS = 6000;
    /** 扫描半径（格） */
    private static final double SCAN_R = 24.0;
    /** 近于该距离视为「马上会被捡走」，不计入倒计时（平方） */
    private static final double NEAR_R2 = 12.0 * 12.0;

    private static float islandTimer = 0f;
    private static boolean pinned = false;
    private static boolean wasActive = false;
    private static int trackedId = -1;
    private static int nearby = 0;

    public static void tick(float dt, Minecraft mc) {
        if (!Config.focusEnabled || !Config.focusXpOrb) { reset(); return; }
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { reset(); return; }

        ExperienceOrb best = null;   // 年龄最大 = 最快消失的那颗
        int count = 0;
        try {
            AABB box = new AABB(p.blockPosition()).inflate(SCAN_R);
            for (Entity e : mc.level.getEntities(p, box)) {
                if (!(e instanceof ExperienceOrb orb) || !orb.isAlive()) continue;
                if (p.distanceToSqr(orb) < NEAR_R2) continue;   // 够得着的会被吸走
                count++;
                if (best == null || ageOf(orb) > ageOf(best)) best = orb;
            }
        } catch (Throwable ignored) { }

        if (best == null) { reset(); return; }

        int age = ageOf(best);
        int left = LIFE_TICKS - age;
        if (left <= 0) { reset(); return; }

        // 从「附近没有」到「附近有」：只在这一次上台提醒，之后稳定并入焦点，
        // 避免走一步换一颗球就把胶囊刷一遍。
        if (!wasActive) {
            wasActive = true;
            islandTimer = Config.focusHold;
            pinned = false;
        }
        if (trackedId != best.getId()) trackedId = best.getId();

        active = true;
        nearby = count;
        int secs = left / 20;
        progress = Anim.clamp(left / (float) LIFE_TICKS, 0f, 1f);
        label = ProgressTracker.I18nS.tr("dynamicisland.focus.xporb");
        sub = ProgressTracker.I18nS.tr("dynamicisland.focus.xporb.sub", format(secs));
        if (count > 1) sub += " · " + ProgressTracker.I18nS.tr("dynamicisland.focus.count", count);
        center = secs > 60 ? (secs / 60) + "m" : String.valueOf(secs);
        icon = new ItemStack(Items.EXPERIENCE_BOTTLE);

        if (islandTimer > 0f) islandTimer -= dt;
    }

    /** 消失计时字段 age；accessor 拿不到时退回 Entity.tickCount 兜底 */
    private static int ageOf(ExperienceOrb orb) {
        try { return ((ExperienceOrbAccessor) orb).dynamicisland$getAge(); }
        catch (Throwable t) { return ((Entity) orb).tickCount; }
    }

    private static String format(int secs) {
        int m = secs / 60, s = secs % 60;
        return m + ":" + String.format("%02d", s);
    }

    public static boolean onIsland() { return pinned || islandTimer > 0f; }
    public static boolean pinned() { return pinned; }

    public static void bringBack() {
        if (!active) return;
        pinned = !pinned;
        if (pinned) islandTimer = 0f;
    }

    public static void reset() {
        active = false; islandTimer = 0f; pinned = false; wasActive = false;
        trackedId = -1; nearby = 0;
        label = ""; sub = ""; center = ""; progress = 0f; icon = ItemStack.EMPTY;
    }
}
