package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.phys.AABB;

/**
 * 僵尸村民治愈进度：3~5 分钟，原版只有一堆粒子，没有任何进度提示。
 *
 * 原版在 startConverting 时会给僵尸村民上一个「力量」效果，时长恰好等于总治愈时长，
 * 且与治愈倒计时同步递减、对客户端可见 —— 优先读它的剩余时长。
 * 但某些情况（客户端效果未同步等）读不到，此时退回本地计时，保证时间一直在走、始终可见。
 *
 * 注：getConversionProgress() 是 private，且语义是「扫描附近特殊方块算加速倍率」，不是进度。
 */
public class CureWatch {

    public static String label = "";
    public static String sub = "";
    public static String center = "";
    public static float progress = 0f;
    public static boolean active = false;

    private static float islandTimer = 0f;
    private static boolean pinned = false;
    private static int targetId = -1;
    private static int elapsed = 0;
    private static int totalTicks = 0;
    /** 读不到力量效果时的兜底总时长：原版治愈 3~5 分钟，取最小 3 分钟 */
    private static final int FALLBACK_TICKS = 3600;

    private static final double RADIUS = 16.0;

    public static void tick(float dt, Minecraft mc) {
        if (!Config.focusEnabled || !Config.focusCure) { reset(); return; }
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { reset(); return; }

        ZombieVillager target = null;
        try {
            AABB box = new AABB(p.blockPosition()).inflate(RADIUS);
            for (Entity e : mc.level.getEntities(p, box)) {
                if (e instanceof ZombieVillager zv && zv.isConverting()) { target = zv; break; }
            }
        } catch (Throwable ignored) { }

        if (target == null) { reset(); return; }

        int eff = strengthTicks(target);   // 力量效果剩余时长（准确）

        if (targetId != target.getId()) {
            targetId = target.getId();
            elapsed = 0;
            totalTicks = eff > 0 ? eff : FALLBACK_TICKS;
            islandTimer = Config.focusHold;
            pinned = false;
        }
        elapsed++;

        int remaining;
        if (eff > 0) {
            remaining = eff;
            // 用真实值校准总时长（效果可能晚一两 tick 才同步到客户端）
            totalTicks = Math.max(totalTicks, eff + Math.max(0, elapsed - 1));
        } else {
            // 效果读不到 → 退回本地计时，保证倒计时仍在走、不会整块消失
            remaining = Math.max(0, totalTicks - elapsed);
        }
        if (remaining <= 0) { reset(); return; }

        active = true;
        progress = Anim.clamp(1f - remaining / (float) Math.max(1, totalTicks), 0f, 1f);
        label = ProgressTracker.I18nS.tr("dynamicisland.focus.cure");
        int secs = remaining / 20;
        sub = ProgressTracker.I18nS.tr("dynamicisland.focus.cure.sub", format(secs));
        center = secs > 60 ? (secs / 60) + "m" : String.valueOf(secs);

        if (islandTimer > 0f) islandTimer -= dt;
    }

    /** 力量效果剩余时长 = 剩余治愈 tick（客户端可见，随治愈同步递减） */
    private static int strengthTicks(ZombieVillager zv) {
        try {
            MobEffectInstance e = zv.getEffect(MobEffects.DAMAGE_BOOST);
            return e == null ? 0 : Math.max(0, e.getDuration());
        } catch (Throwable t) { return 0; }
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
        active = false; islandTimer = 0f; pinned = false;
        targetId = -1; elapsed = 0; totalTicks = 0;
        label = ""; sub = ""; center = ""; progress = 0f;
    }
}
