package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 滞留药水 / 效果云剩余时间：AreaEffectCloud 一放下就彻底隐性 ——
 * 战斗里踩着持续伤害云，或者守着治疗云的时候，没人知道它还剩多久。
 *
 * <p><b>26.x 的关键坑</b>：云的 {@code duration} 是纯服务端字段，<b>从来没有同步给客户端</b>
 * （同步的只有 DATA_RADIUS / DATA_WAITING / DATA_PARTICLE）。
 * 所以客户端读 {@code getDuration()} 永远拿到默认值 -1，一读就当成「不限时」，所有云都会显示 ∞。
 *
 * <p>改用<b>半径反推</b>：原版自己就是用 {@code radiusPerTick = -radius / duration}
 * 让云的半径线性缩到 0 的（见 ThrownLingeringPotion），而半径是同步字段，
 * 于是「每 tick 缩多少」能就地量出来 →
 * <pre>剩余 tick ≈ 当前半径 / 每 tick 衰减量</pre>
 * 起始半径也一并记下，进度就是「当前半径 / 起始半径」，天然线性。
 * 半径根本不缩的云（{@code /summon} 出来的、RadiusPerTick=0 的）才真的是不限时，显示 ∞。
 *
 * <p>waitTime 同样没同步，只有一个布尔 {@code isWaiting()}；
 * 所以等待期先用「上一次观测到的等待时长」做预测，第一次见面时按原版药水的 10 tick 兜底。
 */
public class CloudWatch {

    public static String label = "";
    public static String sub = "";
    public static String center = "";
    public static float progress = 0f;
    public static boolean active = false;
    public static ItemStack icon = ItemStack.EMPTY;
    public static int accent = -1;

    /** 扫描半径（格） */
    private static final double SCAN_R = 24.0;

    private static float islandTimer = 0f;
    private static boolean pinned = false;
    private static boolean wasActive = false;
    private static int trackedId = -1;

    /** 每团云的本地观测（key 按身份比较，因为实体是按对象扫描到的） */
    private static final Map<AreaEffectCloud, Sample> SAMPLES = new IdentityHashMap<>();

    /** 上次实际观测到的等待时长：等待期客户端读不到 waitTime，用这个预测下一团 */
    private static int observedWait = -1;

    private static final class Sample {
        float maxRadius = -1f;   // 生效后见过的最大半径（≈ 起始半径，用于算进度）
        float prevRadius = -1f;  // 上一次采样到的半径
        int prevTick = -1;       // 上一次采样的 tickCount
        float decay = -1f;       // 每 tick 的半径衰减量，>0 表示正在缩小
    }

    public static void tick(float dt, Minecraft mc) {
        if (!Config.focusEnabled || !Config.focusCloud) { reset(); return; }
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { reset(); return; }

        SAMPLES.keySet().removeIf(c -> !c.isAlive());   // 云没了就把观测丢掉，避免堆积

        AreaEffectCloud best = null;    // 最快消失的那团
        int bestLeft = Integer.MAX_VALUE;
        try {
            AABB box = new AABB(p.blockPosition()).inflate(SCAN_R);
            for (Entity e : mc.level.getEntities(p, box)) {
                if (!(e instanceof AreaEffectCloud c) || !c.isAlive()) continue;
                int left = observeAndLeft(c);
                if (left <= 0) continue;
                if (left < bestLeft) { bestLeft = left; best = c; }
            }
        } catch (Throwable ignored) { }

        if (best == null) { reset(); return; }

        Sample s = SAMPLES.get(best);
        int elapsed = elapsedTicks(best);
        boolean waiting = best.isWaiting();
        if (!waiting && s != null && s.prevTick >= 0) observedWait = Math.max(1, elapsed);

        if (!wasActive) {
            wasActive = true;
            islandTimer = Config.focusHold;
            pinned = false;
        }
        if (trackedId != best.getId()) trackedId = best.getId();

        active = true;
        label = ProgressTracker.I18nS.tr("dynamicisland.focus.cloud");
        icon = new ItemStack(Items.LINGERING_POTION);

        if (waiting) {
            // 等待生效：客户端没有 waitTime，只能按上次观测值预测（首次按原版药水的 10 tick）
            int est = estimatedWait();
            int toStart = Math.max(0, est - elapsed);
            progress = Anim.clamp(elapsed / (float) Math.max(1, est), 0f, 1f);
            sub = ProgressTracker.I18nS.tr("dynamicisland.focus.cloud.wait", String.format("%.1fs", toStart / 20f));
            center = String.valueOf((int) Math.ceil(toStart / 20f));
        } else if (s != null && s.decay > 1e-6f && s.maxRadius > 0f) {
            // 半径在缩：剩余 = 半径 / 衰减速率（正是原版 duration 的语义），进度 = 当前半径 / 起始半径
            int left = Math.max(0, (int) (best.getRadius() / s.decay));
            progress = Anim.clamp(best.getRadius() / s.maxRadius, 0f, 1f);
            int secs = (int) Math.ceil(left / 20f);
            sub = ProgressTracker.I18nS.tr("dynamicisland.focus.cloud.sub", format(secs));
            center = secs > 60 ? ((secs + 59) / 60) + "m" : String.valueOf(secs);
        } else {
            // 半径不缩 = 不会自己消失（duration=-1 / RadiusPerTick=0），别假装有倒计时
            progress = 1f;
            sub = ProgressTracker.I18nS.tr("dynamicisland.focus.cloud.sub", "∞");
            center = "∞";
        }
        accent = cloudColor(best);

        if (islandTimer > 0f) islandTimer -= dt;
    }

    /**
     * 采样半径、更新衰减速率，并返回这团云的预估剩余 tick（用于挑「最快消失」的那团）。
     * 半径是同步字段，衰减速率就地量出来，不需要 Mixin。
     */
    private static int observeAndLeft(AreaEffectCloud c) {
        Sample s = SAMPLES.computeIfAbsent(c, k -> new Sample());
        int now = elapsedTicks(c);

        if (c.isWaiting()) {
            // 等待期半径不动，重新开始采样（生效那一刻的半径才是起始半径）
            s.maxRadius = -1f;
            s.prevRadius = -1f;
            s.prevTick = now;
            s.decay = -1f;
            return Math.max(1, estimatedWait() - now);
        }

        float r = c.getRadius();
        if (s.maxRadius < 0f) {
            s.maxRadius = r;
            s.prevRadius = r;
            s.prevTick = now;
        } else {
            if (r > s.maxRadius) s.maxRadius = r;
            int dTicks = now - s.prevTick;
            if (dTicks > 0 && r < s.prevRadius - 1e-5f) {
                float d = (s.prevRadius - r) / dTicks;
                // 轻度平滑，避免同步抖动把剩余时间打飞
                s.decay = s.decay < 0f ? d : s.decay * 0.5f + d * 0.5f;
            }
            s.prevRadius = r;
            s.prevTick = now;
        }

        if (s.decay > 1e-6f) return Math.max(1, (int) (r / s.decay));
        return Integer.MAX_VALUE / 4;
    }

    /** 等待时长：优先用上次实测值，首次见面按原版滞留药水的 10 tick */
    private static int estimatedWait() {
        return observedWait > 0 ? observedWait : 10;
    }

    /** 已存活 tick；AreaEffectCloud 用的是 Entity.tickCount（原版死亡判定同源） */
    private static int elapsedTicks(AreaEffectCloud c) {
        try { return ((Entity) c).tickCount; } catch (Throwable t) { return 0; }
    }

    /** 云自身的药水颜色（0xRRGGBB），取不到就跟随皮肤 */
    private static int cloudColor(AreaEffectCloud c) {
        try {
            // 26.x 起云不再直接给颜色，粒子参数里带的是它自己的药水色
            if (c.getParticle() instanceof net.minecraft.core.particles.ColorParticleOption cp) {
                int r = (int) (cp.getRed() * 255f) & 0xFF;
                int g = (int) (cp.getGreen() * 255f) & 0xFF;
                int b = (int) (cp.getBlue() * 255f) & 0xFF;
                return (r << 16) | (g << 8) | b;
            }
        } catch (Throwable t) { return -1; }
        return -1;
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
        trackedId = -1; accent = -1;
        label = ""; sub = ""; center = ""; progress = 0f; icon = ItemStack.EMPTY;
    }
}
