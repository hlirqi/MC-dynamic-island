package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * 被标记：骷髅的普通箭不会标记，但流浪者的迟缓箭、光谱箭会让玩家发光暴露位置。
 * 原版只给一层轮廓，不告诉你还剩多久 —— 多人 PVP 里这是生死信息。
 */
public class MarkWatch {

    public static String label = "";
    public static String sub = "";
    public static String center = "";
    public static float progress = 0f;
    public static boolean active = false;

    private static float islandTimer = 0f;
    private static boolean pinned = false;
    private static boolean wasActive = false;
    private static int totalTicks = 1;

    public static void tick(float dt, LocalPlayer p) {
        if (!Config.focusEnabled || !Config.focusMark || p == null) { reset(); return; }

        MobEffectInstance glow = null;
        try { glow = p.getEffect(MobEffects.GLOWING); } catch (Throwable ignored) { }
        if (glow == null || glow.getDuration() <= 0) { reset(); return; }

        int left = glow.getDuration();
        if (!wasActive) {
            // 刚被标记：上台提醒一次，并重新记录总时长
            totalTicks = Math.max(1, left);
            islandTimer = Config.focusHold;
            pinned = false;
        } else if (left > totalTicks) {
            totalTicks = left;   // 又被补了一发，重新计时
        }
        wasActive = true;

        active = true;
        progress = Anim.clamp(left / (float) totalTicks, 0f, 1f);
        label = ProgressTracker.I18nS.tr("dynamicisland.focus.mark");
        int secs = left / 20;
        sub = ProgressTracker.I18nS.tr("dynamicisland.focus.mark.sub", secs);
        center = String.valueOf(secs);

        if (islandTimer > 0f) islandTimer -= dt;
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
        totalTicks = 1; label = ""; sub = ""; center = ""; progress = 0f;
    }
}
