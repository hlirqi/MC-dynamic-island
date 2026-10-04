package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 爆炸物倒计时：附近有点燃的 TNT 或正在充能的苦力怕时，胶囊红色脉动倒数，给玩家反应时间。
 *
 * TNT 引信默认 80 tick（4 秒）；苦力怕充能到满约 1.5 秒。
 * 同时有多个威胁时取最快引爆的那个。
 */
public class BlastWatch {

    public static String title = "";
    public static String sub = "";
    public static float progress = 0f;
    public static boolean active = false;
    /** 玩家是否处在爆炸致死范围内（粗略按距离估） */
    public static boolean lethal = false;

    private static final double RADIUS = 14.0;
    private static final float TNT_FUSE_SEC = 4.0f;
    private static final float CREEPER_SEC = 1.5f;

    public static void tick(Minecraft mc) {
        if (!Config.modBlast) { active = false; return; }
        LocalPlayer p = mc.player;
        Level lv = mc.level;
        if (p == null || lv == null) { active = false; return; }

        float bestLeft = Float.MAX_VALUE;
        int bestKind = -1;      // 0=TNT 1=苦力怕
        double bestDist = Double.MAX_VALUE;

        try {
            AABB box = new AABB(p.getX() - RADIUS, p.getY() - RADIUS, p.getZ() - RADIUS,
                    p.getX() + RADIUS, p.getY() + RADIUS, p.getZ() + RADIUS);
            for (Entity e : lv.getEntities(p, box)) {
                double dist = e.distanceTo(p);
                if (e instanceof PrimedTnt tnt) {
                    float left = tnt.getFuse() / 20f;
                    if (left < bestLeft) { bestLeft = left; bestKind = 0; bestDist = dist; }
                } else if (e instanceof Creeper c) {
                    if (c.getSwellDir() <= 0) continue;             // 没在充能
                    float swell = Anim.clamp(c.getSwelling(0f), 0f, 1f);
                    float left = (1f - swell) * CREEPER_SEC;
                    if (left < bestLeft) { bestLeft = left; bestKind = 1; bestDist = dist; }
                }
            }
        } catch (Throwable ignored) { }

        if (bestKind < 0) { active = false; return; }

        active = true;
        title = bestKind == 0
                ? ProgressTracker.I18nS.tr("dynamicisland.blast.tnt")
                : ProgressTracker.I18nS.tr("dynamicisland.blast.creeper");
        sub = String.format("%.1fs", Math.max(0f, bestLeft));
        progress = Anim.clamp(bestLeft / (bestKind == 0 ? TNT_FUSE_SEC : CREEPER_SEC), 0f, 1f);
        // TNT 致死半径约 7 格，粗略估一下够用
        lethal = bestDist < 7.0;
    }

    public static void reset() {
        active = false; title = ""; sub = ""; progress = 0f; lethal = false;
    }
}
