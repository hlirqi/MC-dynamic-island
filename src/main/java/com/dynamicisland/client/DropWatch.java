package com.dynamicisland.client;

import com.dynamicisland.Config;
import com.dynamicisland.mixin.ItemEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

/**
 * 死亡掉落物回收倒计时：掉落的物品 5 分钟后永久消失，原版对此零提示。
 *
 * 只认「玩家死后掉出来的那批」：死亡瞬间记录位置，之后几秒把掉出来的物品实体 ID
 * 登记下来，只跟踪这一批。普通掉落物（挖矿、怪物掉落、丢弃）一律不算，
 * 所以玩家没死过时绝不会触发焦点和通知。
 */
public class DropWatch {

    public static String label = "";
    public static String sub = "";
    public static String center = "";
    public static float progress = 0f;
    public static boolean active = false;
    public static ItemStack icon = ItemStack.EMPTY;

    /** 原版掉落物存活 6000 tick = 5 分钟 */
    private static final int LIFE_TICKS = 6000;
    /** 玩家回到死亡点多近就自动收起倒计时（8 格，平方） */
    private static final double ARRIVE_R2 = 64.0;

    private static float islandTimer = 0f;
    private static boolean pinned = false;
    private static boolean warned = false;
    private static int trackedId = -1;
    private static int lastSec = -1;
    /** 最后一次死亡位置，只认这一带的东西 */
    private static BlockPos deathAt = null;
    /** 登记下来的死亡掉落物品实体 ID，只跟踪这一批 */
    private static final Set<Integer> deathDrops = new HashSet<>();
    /** 死亡后还有几 tick 用来登记掉落物（约 3 秒窗口） */
    private static int captureTicks = 0;

    public static void tick(float dt, Minecraft mc) {
        if (!Config.focusEnabled || !Config.focusDrop) { deathAt = null; deathDrops.clear(); reset(); return; }
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { reset(); return; }
        // 玩家没死过就绝不触发：普通掉落物不算死亡掉落
        if (deathAt == null) { reset(); return; }

        // 玩家（活着）回到死亡点附近 → 已经赶到掉落物旁，回收倒计时使命完成，自动收起。
        // 死亡当刻玩家就在死亡点上，但那时 isDeadOrDying() 为 true，所以不会误取消。
        if (!p.isDeadOrDying() && nearDeath(p, deathAt)) {
            deathAt = null; deathDrops.clear(); reset(); return;
        }

        // 死亡后头几秒把掉出来的物品登记下来，只认这一批
        if (captureTicks > 0) {
            captureTicks--;
            captureDeathDrops(mc, p);
        }

        ItemEntity tracked = findTracked(mc, p);
        if (tracked == null) {
            // 登记窗口内没扫到掉落物是正常的（物品可能晚几 tick 才刷到客户端），
            // 此时不能立刻清掉 deathAt，否则功能还没起来就被自己掐死了。
            // 窗口跑完仍然没有，才是真没有死亡掉落。
            if (captureTicks <= 0) { deathAt = null; deathDrops.clear(); reset(); }
            return;
        }

        int age = ageOf(tracked);
        int left = LIFE_TICKS - age;
        if (left <= 0) { deathAt = null; deathDrops.clear(); reset(); return; }

        if (trackedId != tracked.getId()) {
            trackedId = tracked.getId();
            warned = false;
            islandTimer = Config.focusHold;
            pinned = false;
        }

        active = true;
        int secs = left / 20;
        progress = Anim.clamp(left / (float) LIFE_TICKS, 0f, 1f);
        label = ProgressTracker.I18nS.tr("dynamicisland.focus.drop");
        sub = ProgressTracker.I18nS.tr("dynamicisland.focus.drop.sub", format(secs));
        center = secs > 60 ? (secs / 60) + "m" : String.valueOf(secs);
        icon = tracked.getItem();

        // 最后 30 秒交给通知播报一次
        if (!warned && secs <= 30) {
            warned = true;
            if (Config.noticeDropWarn) {
                Notifier.push(ProgressTracker.I18nS.tr("dynamicisland.notice.dropping"),
                        ProgressTracker.I18nS.tr("dynamicisland.notice.dropping.sub", secs),
                        tracked.getItem(), Config.theme.bad, "drop:" + tracked.getId(), 20);
            }
            islandTimer = Math.max(islandTimer, Config.focusHold);
        }
        lastSec = secs;

        if (islandTimer > 0f) islandTimer -= dt;
    }

    /** 死亡时由外部打点，之后只认这个位置附近、刚掉出来的那批掉落物 */
    public static void markDeath(BlockPos pos) {
        deathAt = pos;
        deathDrops.clear();
        captureTicks = 100;  // 5 秒窗口，足够物品刷到客户端并被登记
        trackedId = -1;
        warned = false;
        islandTimer = Config.focusHold;
        pinned = false;
    }

    private static void captureDeathDrops(Minecraft mc, LocalPlayer p) {
        try {
            AABB box = new AABB(deathAt).inflate(48.0);
            for (Entity e : mc.level.getEntities(p, box)) {
                if (e instanceof ItemEntity ie && ie.isAlive() && ageOf(ie) < 100) {
                    deathDrops.add(ie.getId());
                }
            }
        } catch (Throwable ignored) { }
    }

    private static ItemEntity findTracked(Minecraft mc, LocalPlayer p) {
        if (deathAt == null) return null;
        try {
            AABB box = new AABB(deathAt).inflate(48.0);
            ItemEntity best = null;
            for (Entity e : mc.level.getEntities(p, box)) {
                if (!(e instanceof ItemEntity ie)) continue;
                if (!ie.isAlive()) continue;
                // 已经跟住的优先，避免旁边掉落物抢焦点
                if (ie.getId() == trackedId) return ie;
                // 只认死亡掉落，怪物掉落、普通丢弃一律不算
                if (!deathDrops.contains(ie.getId())) continue;
                if (best == null || ageOf(ie) < ageOf(best)) best = ie;
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 消失计时字段 age；accessor 拿不到时退回 Entity.tickCount 兜底 */
    private static int ageOf(ItemEntity ie) {
        try { return ((ItemEntityAccessor) ie).dynamicisland$getAge(); }
        catch (Throwable t) { return ie.tickCount; }
    }

    /** 玩家是否已走到死亡点附近（手动算距离，避免依赖 BlockPos 的距离 API） */
    private static boolean nearDeath(LocalPlayer p, BlockPos at) {
        double dx = p.getX() - (at.getX() + 0.5);
        double dy = p.getY() - (at.getY() + 0.5);
        double dz = p.getZ() - (at.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz <= ARRIVE_R2;
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
        trackedId = -1; warned = false; lastSec = -1;
        label = ""; sub = ""; center = ""; progress = 0f; icon = ItemStack.EMPTY;
    }
}
