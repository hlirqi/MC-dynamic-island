package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 死亡点回溯：记下最近一次死亡的坐标。
 * 默认关闭，需要在「控制」里绑定按键后手动按一次开启追踪，再按一次关闭。
 */
public class DeathMark {

    private static Vec3 pos = null;
    private static String dim = "";
    private static String coords = "";
    private static Vec3 lastAlive = null;
    private static boolean counted = false;
    public static boolean armed = false;

    public static void tick(LocalPlayer p, Level lv) {
        if (p == null || lv == null) return;

        if (p.getHealth() > 0f && !p.isDeadOrDying()) {
            lastAlive = p.position();
            counted = false;
            return;
        }
        // 死亡瞬间：用最后一次活着的位置当作死亡点
        if (!counted && lastAlive != null) {
            pos = lastAlive;
            dim = lv.dimension().identifier().toString();
            coords = (int) pos.x + " " + (int) pos.y + " " + (int) pos.z;
            counted = true;
            lastAlive = null;
        }
    }

    /** 按键回调：开启或关闭追踪 */
    public static boolean toggle(LocalPlayer p, Level lv) {
        if (p == null || lv == null) return false;
        if (pos == null) {
            Notifier.push(ProgressTracker.I18nS.tr("dynamicisland.extra.deathNone"),
                    ProgressTracker.I18nS.tr("dynamicisland.extra.deathNone.sub"),
                    net.minecraft.world.item.ItemStack.EMPTY, Config.theme.warn, "death:none", 0);
            return false;
        }
        armed = !armed;
        if (armed) {
            Notifier.push(ProgressTracker.I18nS.tr("dynamicisland.extra.deathArm"),
                    coords, net.minecraft.world.item.ItemStack.EMPTY, Config.theme.accent, "death:arm", 0);
        }
        return armed;
    }

    public static void clear() { pos = null; dim = ""; coords = ""; armed = false; counted = false; }

    public static boolean hasPoint() { return pos != null; }

    /** 是否已到达死亡点（到达后自动收起标记） */
    private static void checkArrive(LocalPlayer p) {
        if (p == null || pos == null) return;
        if (p.position().distanceTo(pos) < 6f) {
            armed = false;
            Notifier.push(ProgressTracker.I18nS.tr("dynamicisland.extra.deathArrive"),
                    coords, net.minecraft.world.item.ItemStack.EMPTY, Config.theme.good, "death:arrive", 0);
        }
    }

    public static boolean visible(LocalPlayer p, Level lv) {
        if (!Config.modDeath || !armed || pos == null || p == null || lv == null) return false;
        if (!dim.equals(lv.dimension().identifier().toString())) return true; // 跨维度时提示"不在此维度"
        checkArrive(p);
        return armed;
    }

    public static int distance(LocalPlayer p) {
        if (pos == null || p == null) return 0;
        return (int) Math.round(p.position().distanceTo(pos));
    }

    public static String coordsText() { return coords; }

    public static boolean otherDimension(Level lv) {
        return pos != null && lv != null && !dim.equals(lv.dimension().identifier().toString());
    }

    /**
     * 目标相对屏幕正上方的旋转弧度。rot=0 表示正前方。
     * MC 朝向约定：yaw 0=南(+Z)，顺时针增大；由此推导屏幕左右，故最终取负号。
     */
    public static float arrowRotation(LocalPlayer p) {
        if (pos == null || p == null) return 0f;
        double dx = pos.x - p.getX();
        double dz = pos.z - p.getZ();
        double bearing = Math.atan2(dx, dz);
        double rel = bearing + Math.toRadians(p.getYRot());
        while (rel > Math.PI) rel -= Math.PI * 2;
        while (rel < -Math.PI) rel += Math.PI * 2;
        return (float) -rel;
    }

    public static Component noPointText() {
        return Component.translatable("dynamicisland.extra.deathNone");
    }
}
