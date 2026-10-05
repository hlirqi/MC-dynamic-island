package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 长效药水：拿到时长超过阈值的效果时先在胶囊提醒，随后并入灵动焦点，
 * 用圆环实时看剩余时长；快失效时再提醒一次。
 *
 * 锁定策略：一旦选定某个效果就一直跟随它，直到效果消失，避免多个 buff 之间来回跳。
 */
public class PotionWatch {

    public static String label = "";
    public static String sub = "";
    public static String center = "";
    public static float progress = 0f;
    public static int accent = -1;
    public static boolean active = false;

    private static float islandTimer = 0f;
    private static boolean pinned = false;
    private static Holder<MobEffect> locked = null;
    private static int totalTicks = 1;
    private static boolean warnedExpiring = false;

    private static final int EXPIRE_WARN_TICKS = 10 * 20; // 剩余 10 秒时再提醒一次

    public static void tick(float dt, LocalPlayer p) {
        if (!Config.focusEnabled || !Config.focusPotion || p == null) {
            reset();
            return;
        }

        MobEffectInstance cand = null;
        try {
            int minTicks = (int) (Config.potionMinSec * 20f);
            for (MobEffectInstance e : p.getActiveEffects()) {
                // 已锁定的优先跟随（即使剩余时长已低于阈值，也继续显示完）
                if (locked != null && e.getEffect() == locked) { cand = e; break; }
                if (e.getDuration() > minTicks && (cand == null || e.getDuration() > cand.getDuration())) cand = e;
            }
        } catch (Throwable ignored) { }

        if (cand == null) { reset(); return; }

        if (locked != cand.getEffect()) {
            locked = cand.getEffect();
            totalTicks = Math.max(1, cand.getDuration());
            warnedExpiring = false;
            islandTimer = Config.focusHold;   // 刚拿到长效效果 → 上台提醒
            pinned = false;
        }

        active = true;
        label = name(cand);
        int left = cand.getDuration();
        progress = Anim.clamp(left / (float) totalTicks, 0f, 1f);
        sub = format(left / 20);
        int secs = left / 20;
        center = secs >= 60 ? (secs / 60) + "m" : String.valueOf(secs);
        try { accent = cand.getEffect().value().getColor(); } catch (Throwable t) { accent = -1; }

        // 即将失效 → 再上台提醒一次
        if (!warnedExpiring && left <= EXPIRE_WARN_TICKS) {
            warnedExpiring = true;
            islandTimer = Config.focusHold;
        }

        if (islandTimer > 0f) islandTimer -= dt;
    }

    private static String name(MobEffectInstance e) {
        try {
            String n = e.getEffect().value().getDisplayName().getString();
            if (e.getAmplifier() > 0) n += " " + roman(e.getAmplifier() + 1);
            return n;
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String roman(int i) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return i >= 0 && i < r.length ? r[i] : String.valueOf(i);
    }

    private static String format(int secs) {
        int m = secs / 60, s = secs % 60;
        return m + ":" + String.format("%02d", s);
    }

    public static boolean onIsland() { return pinned || islandTimer > 0f; }

    /** 是否被手动钉在胶囊上 */
    public static boolean pinned() { return pinned; }

    /** 呼出 / 收回：再按一次才退回焦点 */
    public static void bringBack() {
        if (!active) return;
        pinned = !pinned;
        if (pinned) islandTimer = 0f;
    }

    public static void reset() {
        active = false;
        islandTimer = 0f;
        pinned = false;
        locked = null;
        warnedExpiring = false;
        label = "";
        sub = "";
        center = "";
        progress = 0f;
        accent = -1;
    }
}
