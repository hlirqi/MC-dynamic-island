package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * 滞留药水 / 效果云剩余时间：AreaEffectCloud 一放下就彻底隐性 ——
 * 战斗里踩着持续伤害云，或者守着治疗云的时候，没人知道它还剩多久。
 *
 * 剩余 = 生效等待 waitTime + 持续 duration − 已存活 tickCount。
 * 三个量都是公开成员（waitTime / duration 有 getter，tickCount 来自 Entity），
 * 所以这里不需要 Mixin，读取永远可用。
 *
 * 云还没开始生效时（tickCount < waitTime）显示「x 秒后生效」，
 * 生效之后显示「剩余 x」。圆环颜色直接取云自身的药水颜色，一眼能认出是什么云。
 *
 * 两段式：附近出现效果云 → 先在胶囊上提示 focusHold 秒 → 并入灵动焦点。
 */
public class CloudWatch {

    public static String label = "";
    public static String sub = "";
    public static String center = "";
    public static float progress = 0f;
    public static boolean active = false;
    public static ItemStack icon = ItemStack.EMPTY;
    public static int accent = -1;

    /** 扫描半径（格） */
    private static final double SCAN_R = 24.0;

    private static float islandTimer = 0f;
    private static boolean pinned = false;
    private static boolean wasActive = false;
    private static int trackedId = -1;

    public static void tick(float dt, Minecraft mc) {
        if (!Config.focusEnabled || !Config.focusCloud) { reset(); return; }
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { reset(); return; }

        AreaEffectCloud best = null;   // 最快消失的那团
        try {
            AABB box = new AABB(p.blockPosition()).inflate(SCAN_R);
            for (Entity e : mc.level.getEntities(p, box)) {
                if (!(e instanceof AreaEffectCloud c) || !c.isAlive()) continue;
                int left = leftTicks(c);
                if (left <= 0) continue;
                if (best == null || left < leftTicks(best)) best = c;
            }
        } catch (Throwable ignored) { }

        if (best == null) { reset(); return; }

        int wait = Math.max(0, safeWait(best));
        int total = Math.max(1, wait + Math.max(1, safeDuration(best)));
        int elapsed = elapsedTicks(best);
        int left = Math.max(0, total - elapsed);
        if (left <= 0) { reset(); return; }

        if (!wasActive) {
            wasActive = true;
            islandTimer = Config.focusHold;
            pinned = false;
        }
        if (trackedId != best.getId()) trackedId = best.getId();

        boolean waiting = elapsed < wait;
        active = true;
        progress = Anim.clamp(left / (float) total, 0f, 1f);
        label = ProgressTracker.I18nS.tr("dynamicisland.focus.cloud");
        icon = new ItemStack(Items.LINGERING_POTION);
        int secs = left / 20;
        if (waiting) {
            // 还没生效：倒计时到「开始生效」为止
            sub = ProgressTracker.I18nS.tr("dynamicisland.focus.cloud.wait", String.format("%.1fs", left / 20f));
            center = String.valueOf(Math.max(0, Math.round(left / 20f)));
        } else {
            sub = ProgressTracker.I18nS.tr("dynamicisland.focus.cloud.sub", format(secs));
            center = secs > 60 ? (secs / 60) + "m" : String.valueOf(secs);
        }
        accent = cloudColor(best);

        if (islandTimer > 0f) islandTimer -= dt;
    }

    /** 剩余存活 tick = waitTime + duration − tickCount */
    private static int leftTicks(AreaEffectCloud c) {
        return Math.max(1, safeWait(c)) + Math.max(1, safeDuration(c)) - elapsedTicks(c);
    }

    /** waitTime / duration 均为公开 getter，读取失败时给保守默认值兜底 */
    private static int safeWait(AreaEffectCloud c) {
        try { return c.getWaitTime(); } catch (Throwable t) { return 10; }
    }

    private static int safeDuration(AreaEffectCloud c) {
        try { return c.getDuration(); } catch (Throwable t) { return 600; }
    }

    /** 已存活 tick；AreaEffectCloud 用的是 Entity.tickCount（原版死亡判定同源） */
    private static int elapsedTicks(AreaEffectCloud c) {
        try { return ((Entity) c).tickCount; } catch (Throwable t) { return 0; }
    }

    /** 云自身的药水颜色（0xRRGGBB），取不到就跟随皮肤 */
    private static int cloudColor(AreaEffectCloud c) {
        try { return c.getColor() & 0xFFFFFF; }
        catch (Throwable t) { return -1; }
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
        trackedId = -1; accent = -1;
        label = ""; sub = ""; center = ""; progress = 0f; icon = ItemStack.EMPTY;
    }
}
