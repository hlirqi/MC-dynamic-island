package com.dynamicisland.client;

import com.dynamicisland.Config;
import com.dynamicisland.mixin.FishingHookAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;

/**
 * 钓鱼咬钩窗口：浮标下沉那一瞬原版只有「噗通」一声和水花，视觉上没有任何判定提示，
 * 低头看钓竿或者挂着其他界面时极易错过收杆时机。
 *
 * 检测方式是直接读 `FishingHook.biting` —— 这个字段由 `DATA_BITING` 同步数据驱动，
 * 服务端在鱼咬钩 / 鱼跑掉时都会同步过来，所以客户端读到的就是权威判定，
 * 不依赖位置抖动之类的猜测（多人模式同样有效）。
 *
 * 归属判定优先用玩家自己的 `fishing` 字段，其次扫描附近钩子并按 `getPlayerOwner()` 过滤。
 */
public class FishingWatch {

    public static boolean active = false;
    public static String sub = "";
    public static float progress = 0f;

    /** 咬钩提示停留秒数（够你看清并收杆） */
    private static final float HOLD = 1.6f;
    /** 兜底扫描半径（格），钓线最远也能到 30+ 格 */
    private static final double SCAN_R = 40.0;

    private static boolean wasBiting = false;
    private static float timer = 0f;

    public static void tick(float dt, Minecraft mc) {
        active = false; sub = ""; progress = 0f;
        if (!Config.modFishingBite) { wasBiting = false; timer = 0f; return; }
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || p.isDeadOrDying()) { wasBiting = false; timer = 0f; return; }

        boolean biting = false;
        try {
            FishingHook hook = ownHook(mc, p);
            biting = hook != null && bitingOf(hook);
        } catch (Throwable ignored) { }

        if (biting && !wasBiting) timer = HOLD;   // 咬钩上升沿 → 提示一次
        wasBiting = biting;

        if (timer <= 0f) return;
        timer -= dt;

        active = true;
        sub = ProgressTracker.I18nS.tr("dynamicisland.fishing.sub");
        progress = Anim.clamp(timer / HOLD, 0f, 1f);
    }

    /** 优先取玩家自己正在使用的钩子，取不到再扫附近并按归属过滤 */
    private static FishingHook ownHook(Minecraft mc, LocalPlayer p) {
        FishingHook mine = p.fishing;
        if (mine != null && !mine.isRemoved()) return mine;

        AABB box = new AABB(p.blockPosition()).inflate(SCAN_R);
        FishingHook best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(p, box)) {
            if (!(e instanceof FishingHook h) || !h.isAlive()) continue;
            if (h.getPlayerOwner() != p) continue;   // 只认自己的浮标
            double d = p.distanceToSqr(h);
            if (d < bestD) { bestD = d; best = h; }
        }
        return best;
    }

    private static boolean bitingOf(FishingHook hook) {
        try { return ((FishingHookAccessor) hook).dynamicisland$isBiting(); }
        catch (Throwable t) { return false; }
    }

    public static void reset() {
        active = false; sub = ""; progress = 0f;
        wasBiting = false; timer = 0f;
    }
}
