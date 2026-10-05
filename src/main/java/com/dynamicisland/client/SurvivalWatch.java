package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 生存类隐性信息：**细雪冻结进度** + **窒息警告**。两者都是原版零提示、
 * 「不知道就死」的类型 —— 细雪里站着不知道还剩几秒变冰雕，被埋了不知道还能撑多久。
 *
 * 细雪冻结：原版 `Entity.getPercentFrozen()` 就是现成的进度（0~1），
 * 每 tick +1（细雪中，上限 140 = 7 秒），每 tick −2（离开后解冻）。全部公开 API，无需 Mixin。
 * 注意 `ticksFrozen` 是同步数据，客户端读到的是服务端权威值。
 *
 * 窒息：`Entity.isInWall()` 判定的就是「眼睛所在位置卡进可窒息方块」，正是头被埋住的情形。
 * 伤害为 1 点 / 10 tick（受无敌帧限制）≈ 2.0 HP/s，据此估算剩余存活时间。
 */
public class SurvivalWatch {

    // ---------- 细雪冻结 ----------
    public static boolean freezeActive = false;
    public static String freezeSub = "";
    public static float freezeProgress = 0f;
    public static boolean freezeUrgent = false;
    public static final ItemStack FREEZE_ICON = new ItemStack(Items.POWDER_SNOW_BUCKET);

    // ---------- 窒息 ----------
    public static boolean suffocateActive = false;
    public static String suffocateSub = "";
    public static float suffocateProgress = 0f;
    public static final ItemStack SUFFOCATE_ICON = new ItemStack(Items.GRAVEL);

    /** 原版窒息伤害 ≈ 2.0 HP/s（1 点 / 10 tick） */
    private static final float SUFFOCATE_DPS = 2.0f;
    /** 「还能撑」进度环的参考时长（秒），撑过这个时长环就满 */
    private static final float SUFFOCATE_REF_SEC = 10f;
    /** 连续命中该 tick 数才判定为真窒息，避免单帧抖动导致闪烁 */
    private static final int SUFFOCATE_DEBOUNCE = 3;

    private static int suffocateStreak = 0;

    public static void tick(float dt, LocalPlayer p) {
        tickFreeze(p);
        tickSuffocate(p);
    }

    // ---------- 细雪冻结 ----------

    private static void tickFreeze(LocalPlayer p) {
        freezeActive = false; freezeUrgent = false; freezeProgress = 0f; freezeSub = "";
        if (!Config.modFreezeWarning || p == null) return;
        try {
            if (p.isDeadOrDying() || p.isCreative() || p.isSpectator()) return;

            int frozen = p.getTicksFrozen();
            if (frozen <= 0) return;                       // 没冻过也不在冻 → 不打扰

            int required = Math.max(1, p.getTicksRequiredToFreeze());
            boolean inSnow = p.isInPowderSnow && p.canFreeze();
            boolean fully = frozen >= required;

            freezeActive = true;
            freezeProgress = Anim.clamp(Math.min(frozen, required) / (float) required, 0f, 1f);
            freezeUrgent = inSnow || fully;

            if (fully) {
                freezeSub = tr("dynamicisland.freeze.frozen");
            } else if (inSnow) {
                int left = required - frozen;              // 细雪中每 tick +1
                freezeSub = tr("dynamicisland.freeze.sub", String.format("%.1fs", left / 20f));
            } else {
                int thaw = (frozen + 1) / 2;               // 离开后每 tick −2
                freezeSub = tr("dynamicisland.freeze.thaw", String.format("%.1fs", thaw / 20f));
            }
        } catch (Throwable ignored) { }
    }

    // ---------- 窒息 ----------

    private static void tickSuffocate(LocalPlayer p) {
        suffocateActive = false; suffocateProgress = 0f; suffocateSub = "";
        if (!Config.modSuffocate || p == null) return;
        try {
            if (p.isDeadOrDying() || p.isCreative() || p.isSpectator()) { suffocateStreak = 0; return; }
            if (!p.isInWall()) { suffocateStreak = 0; return; }

            if (++suffocateStreak < SUFFOCATE_DEBOUNCE) return;

            float hp = Math.max(0f, p.getHealth());
            float eta = hp / SUFFOCATE_DPS;
            suffocateActive = true;
            suffocateProgress = Anim.clamp(eta / SUFFOCATE_REF_SEC, 0f, 1f);
            suffocateSub = tr("dynamicisland.suffocate.sub", String.format("%.1fs", eta));
        } catch (Throwable ignored) { }
    }

    public static void reset() {
        freezeActive = false; freezeSub = ""; freezeProgress = 0f; freezeUrgent = false;
        suffocateActive = false; suffocateSub = ""; suffocateProgress = 0f;
        suffocateStreak = 0;
    }

    private static String tr(String key, Object... args) {
        return ProgressTracker.I18nS.tr(key, args);
    }
}
