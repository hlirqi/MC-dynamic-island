package com.dynamicisland.client;

import com.dynamicisland.Config;
import com.dynamicisland.mixin.VillagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 交易锁定倒计时：附近有村民把货卖光（交易被锁）时，显示还要等多久才能补货。
 *
 * 原版规则：村民距离上次补货超过 12000 tick（半个 MC 日 ≈ 10 分钟）才能再补，且一天最多两次。
 * 这两个值原版没有公开 API，靠 VillagerAccessor 取。
 */
public class TradeWatch {

    public static String title = "";
    public static String sub = "";
    public static float progress = 0f;
    public static boolean active = false;

    private static final double RADIUS = 12.0;
    private static final long RESTOCK_INTERVAL = 12000L;
    private static final int MAX_PER_DAY = 2;

    public static void tick(Minecraft mc) {
        if (!Config.modTrade) { active = false; return; }
        LocalPlayer p = mc.player;
        Level lv = mc.level;
        if (p == null || lv == null) { active = false; return; }

        Villager best = null;
        long bestRemain = Long.MAX_VALUE;
        int bestLocked = 0;

        try {
            AABB box = new AABB(p.getX() - RADIUS, p.getY() - RADIUS, p.getZ() - RADIUS,
                    p.getX() + RADIUS, p.getY() + RADIUS, p.getZ() + RADIUS);
            for (Villager v : lv.getEntitiesOfClass(Villager.class, box)) {
                int locked = countLocked(v);
                if (locked <= 0) continue;
                long remain = restockRemain(v, lv);
                if (remain < bestRemain) { bestRemain = remain; best = v; bestLocked = locked; }
            }
        } catch (Throwable ignored) { }

        if (best == null) { active = false; return; }

        active = true;
        title = ProgressTracker.I18nS.tr("dynamicisland.trade.title");
        if (bestRemain == Long.MAX_VALUE) {
            sub = ProgressTracker.I18nS.tr("dynamicisland.trade.exhausted");
            progress = 0f;
        } else {
            double secs = bestRemain * 0.05;
            sub = bestLocked + " · " + format((int) secs);
            progress = Anim.clamp(1f - (bestRemain / (float) RESTOCK_INTERVAL), 0f, 1f);
        }
    }

    private static int countLocked(Villager v) {
        int n = 0;
        try {
            for (MerchantOffer o : v.getOffers()) {
                if (o.getUses() >= o.getMaxUses()) n++;
            }
        } catch (Throwable ignored) { }
        return n;
    }

    private static long restockRemain(Villager v, Level lv) {
        try {
            VillagerAccessor acc = (VillagerAccessor) v;
            int today = acc.dynamicisland$getNumberOfRestocksToday();
            if (today >= MAX_PER_DAY) return Long.MAX_VALUE;   // 今天补货次数用完了
            long last = acc.dynamicisland$getLastRestockGameTime();
            return Math.max(0L, last + RESTOCK_INTERVAL - lv.getGameTime());
        } catch (Throwable t) {
            return Long.MAX_VALUE;
        }
    }

    private static String format(int secs) {
        int m = secs / 60, s = secs % 60;
        return m + ":" + String.format("%02d", s);
    }

    public static void reset() {
        active = false; title = ""; sub = ""; progress = 0f;
    }
}
