package com.dynamicisland.client;

import com.dynamicisland.Config;
import com.dynamicisland.mixin.EndermiteAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * 末影螨存活时间：原版固定 2 分钟（2400 tick）后自然死亡，界面上毫无提示 ——
 * 在末影珍珠刷怪 / 珍珠传送翻车的时候，它到底还能撑多久完全是隐性信息。
 *
 * 存活进度 = life / 2400（life 是原版自增字段，读不到时退回 Entity.tickCount 兜底）。
 *
 * 两段式：附近出现末影螨 → 先在胶囊上提示 focusHold 秒 → 并入灵动焦点持续倒数。
 */
public class EndermiteWatch {

    public static String label = "";
    public static String sub = "";
    public static String center = "";
    public static float progress = 0f;
    public static boolean active = false;
    public static ItemStack icon = ItemStack.EMPTY;

    /** 原版末影螨寿命 2400 tick = 2 分钟（与 MAX_LIFE 一致） */
    private static final int LIFE_TICKS = 2400;
    /** 扫描半径（格） */
    private static final double SCAN_R = 16.0;

    private static float islandTimer = 0f;
    private static boolean pinned = false;
    private static boolean wasActive = false;
    private static int trackedId = -1;
    private static int nearby = 0;

    public static void tick(float dt, Minecraft mc) {
        if (!Config.focusEnabled || !Config.focusEndermite) { reset(); return; }
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { reset(); return; }

        Endermite best = null;   // 活得最久 = 最快到期的那只
        int count = 0;
        try {
            AABB box = new AABB(p.blockPosition()).inflate(SCAN_R);
            for (Entity e : mc.level.getEntities(p, box)) {
                if (!(e instanceof Endermite mite) || !mite.isAlive()) continue;
                count++;
                if (best == null || lifeOf(mite) > lifeOf(best)) best = mite;
            }
        } catch (Throwable ignored) { }

        if (best == null) { reset(); return; }

        int life = lifeOf(best);
        int left = LIFE_TICKS - life;
        if (left <= 0) { reset(); return; }

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
        label = ProgressTracker.I18nS.tr("dynamicisland.focus.endermite");
        sub = ProgressTracker.I18nS.tr("dynamicisland.focus.endermite.sub", format(secs));
        if (count > 1) sub += " · " + ProgressTracker.I18nS.tr("dynamicisland.focus.count", count);
        center = secs > 60 ? (secs / 60) + "m" : String.valueOf(secs);
        icon = new ItemStack(Items.ENDERMITE_SPAWN_EGG);

        if (islandTimer > 0f) islandTimer -= dt;
    }

    /** 自增存活字段 life；accessor 拿不到时退回 Entity.tickCount 兜底 */
    private static int lifeOf(Endermite mite) {
        try { return ((EndermiteAccessor) mite).dynamicisland$getLife(); }
        catch (Throwable t) { return ((Entity) mite).tickCount; }
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
