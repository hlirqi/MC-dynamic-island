package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 盾牌破防禁用倒计时。
 *
 * 1.20 起，被斧头命中且举盾格挡时盾牌会被禁用 5 秒（原版 `Player.disableShield`
 * 往 `Items.SHIELD` 写 100 tick 冷却）。游戏里除了物品栏那圈几乎看不见的冷却，
 * 没有任何显式提示 —— PVP 里这 5 秒基本等于敞开挨打。
 *
 * 客户端可直接读 `player.getCooldowns()`（服务端写入后同步过来），
 * 于是倒计时完全准确，不需要 Mixin。
 */
public class CombatWatch {

    public static boolean shieldActive = false;
    public static String shieldSub = "";
    public static float shieldProgress = 0f;
    /** 禁用刚结束时的短暂提示（好让玩家知道可以重新举盾了） */
    public static boolean shieldBack = false;
    public static final ItemStack SHIELD_ICON = new ItemStack(Items.SHIELD);

    /** 原版破防固定 100 tick = 5 秒 */
    private static final int SHIELD_DISABLE_TICKS = 100;
    /** 「盾牌已恢复」提示停留秒数 */
    private static final float BACK_HOLD = 1.5f;

    private static boolean wasOn = false;
    private static float backTimer = 0f;

    public static void tick(float dt, LocalPlayer p) {
        shieldActive = false; shieldBack = false; shieldSub = ""; shieldProgress = 0f;
        if (!Config.modShieldBreak || p == null) { wasOn = false; backTimer = 0f; return; }

        boolean on = false;
        float pct = 0f;
        try {
            ItemCooldowns cd = p.getCooldowns();
            on = cd.isOnCooldown(Items.SHIELD);
            if (on) pct = Anim.clamp(cd.getCooldownPercent(Items.SHIELD, 0f), 0f, 1f);
        } catch (Throwable ignored) { wasOn = false; return; }

        if (on) {
            backTimer = 0f;
            shieldActive = true;
            shieldProgress = pct;
            float secs = pct * (SHIELD_DISABLE_TICKS / 20f);
            shieldSub = tr("dynamicisland.shield.sub", String.format("%.1fs", secs));
        } else {
            // 刚解禁 → 给一个「可用了」的收尾提示
            if (wasOn) backTimer = BACK_HOLD;
            if (backTimer > 0f) {
                backTimer -= dt;
                shieldBack = true;
                shieldProgress = Anim.clamp(backTimer / BACK_HOLD, 0f, 1f);
                shieldSub = tr("dynamicisland.shield.back");
            }
        }
        wasOn = on;
    }

    public static void reset() {
        shieldActive = false; shieldBack = false; shieldSub = ""; shieldProgress = 0f;
        wasOn = false; backTimer = 0f;
    }

    private static String tr(String key, Object... args) {
        return ProgressTracker.I18nS.tr(key, args);
    }
}
