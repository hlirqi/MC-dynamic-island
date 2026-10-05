package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.player.LocalPlayer;

/** 经验进度：只在真正拿到经验的时候短暂出现，附魔/修理时能看清还差多少。 */
public class XpWatch {

    private static int lastTotal = -1;
    private static float hold = 0f;

    public static int level = 0;
    public static int need = 0;
    public static int missing = 0;
    public static float progress = 0f;
    public static int gained = 0;

    public static void tick(float dt, LocalPlayer p) {
        hold = Math.max(0f, hold - dt);
        if (p == null) return;

        int total = p.totalExperience;
        if (lastTotal < 0) { lastTotal = total; return; }

        if (total > lastTotal) {
            gained = total - lastTotal;
            level = p.experienceLevel;
            progress = p.experienceProgress;
            need = safeNeed(p);
            missing = Math.max(0, (int) Math.ceil((1f - progress) * Math.max(1, need)));
            hold = Config.modXp ? 3.2f : 0f;
        }
        lastTotal = total;
    }

    private static int safeNeed(LocalPlayer p) {
        try { return p.getXpNeededForNextLevel(); } catch (Throwable t) { return 0; }
    }

    public static boolean active() { return Config.modXp && hold > 0f; }

    public static void reset() { lastTotal = -1; hold = 0f; gained = 0; }
}
