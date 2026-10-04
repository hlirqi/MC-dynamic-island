package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 摔落伤害预测：下落途中实时推算落地会掉几颗心、会不会致死、还剩几秒落地。
 *
 * 落地时间按原版物理逐 tick 迭代（重力 0.08、空气阻力 0.98），所以数字是可信的，不是估算。
 * 背包里有水桶且时间还够时，会提示来得及放水。落点是水则直接判安全。
 */
public class FallWatch {

    public static String title = "";
    public static String sub = "";
    public static float progress = 0f;
    public static boolean active = false;
    public static boolean lethal = false;

    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    private static final int MAX_SCAN = 96;
    private static final float SAFE_FALL = 3.0f;
    private static final double BUCKET_MIN_SEC = 0.35;

    public static void tick(Minecraft mc) {
        if (!Config.modFall) { active = false; return; }
        LocalPlayer p = mc.player;
        Level lv = mc.level;
        if (p == null || lv == null) { active = false; return; }

        // 只在实际下落（且不是鞘翅滑翔）时介入
        if (p.onGround() || p.isFallFlying() || p.isInWater()) { active = false; return; }
        double vy = p.getDeltaMovement().y;
        if (vy >= 0) { active = false; return; }

        BlockPos feet = p.blockPosition();
        int minY = lv.getMinBuildHeight();
        int startY = feet.getY() - 1;
        if (startY <= minY) { active = false; return; }

        // ---- 找落点 ----
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos(feet.getX(), startY, feet.getZ());
        double landY = Double.NEGATIVE_INFINITY;
        boolean water = false;
        try {
            for (int y = startY; y > minY && (startY - y) < MAX_SCAN; y--) {
                mp.setY(y);
                BlockState st = lv.getBlockState(mp);
                if (!st.getFluidState().isEmpty()) { water = true; landY = y + 1; break; }
                VoxelShape shape = st.getCollisionShape(lv, mp);
                if (shape.isEmpty()) continue;      // 花草之类站不住，继续往下找
                landY = y + 1;
                break;
            }
        } catch (Throwable ignored) { }

        if (landY == Double.NEGATIVE_INFINITY) { active = false; return; }

        double remaining = Math.max(0.0, p.getY() - landY);
        double total = p.fallDistance + remaining;
        if (!water && total <= SAFE_FALL) { active = false; return; }

        // ---- 落地伤害 ----
        int damage = water ? 0 : Math.max(0, (int) Math.ceil(total - SAFE_FALL));
        int hearts = (int) Math.ceil(damage / 2.0);
        float hp = p.getHealth();
        lethal = !water && damage >= hp;

        // ---- 落地还剩几秒（逐 tick 迭代） ----
        double y = p.getY();
        double v = vy;
        int ticks = 0;
        while (y > landY && ticks < 600) {
            v = (v - GRAVITY) * DRAG;
            y += v;
            ticks++;
        }
        double secs = ticks / 20.0;

        // ---- 水桶救命提示 ----
        boolean hasBucket = false;
        try {
            for (ItemStack s : p.getInventory().items) {
                if (s != null && s.is(Items.WATER_BUCKET)) { hasBucket = true; break; }
            }
        } catch (Throwable ignored) { }

        active = true;
        title = lethal
                ? ProgressTracker.I18nS.tr("dynamicisland.fall.lethal")
                : ProgressTracker.I18nS.tr("dynamicisland.fall.title");
        if (water) {
            sub = ProgressTracker.I18nS.tr("dynamicisland.fall.water");
        } else {
            sub = ProgressTracker.I18nS.tr("dynamicisland.fall.hearts", hearts)
                    + " · " + String.format("%.1fs", secs);
            if (hasBucket && secs >= BUCKET_MIN_SEC) {
                sub += " · " + ProgressTracker.I18nS.tr("dynamicisland.fall.bucket");
            }
        }
        // 进度条表示这次坠落会吃掉多少血，满格即致死
        progress = Anim.clamp(damage / Math.max(1f, p.getMaxHealth()), 0f, 1f);
    }

    public static void reset() {
        active = false; title = ""; sub = ""; progress = 0f; lethal = false;
    }
}
