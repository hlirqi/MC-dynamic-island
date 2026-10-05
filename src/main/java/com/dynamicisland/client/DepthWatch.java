package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.resources.Identifier;

/**
 * 深层环境：
 * - Y 坐标对应的矿物带（"Y=11，钻石层"），换层时提示一次
 * - 洞穴音效 / 深入地下的预警（听到 ambient.cave 或失去天空可见时触发，带冷却）
 */
public class DepthWatch {

    public static String title = "";
    public static String sub = "";
    public static float progress = 0f;
    public static boolean warn = false;

    private static float hold = 0f;
    private static String lastBand = "";
    private static boolean bandReady = false;
    private static float caveCooldown = 0f;
    private static boolean lastCave = false;
    private static float caveHold = 0f;

    /** Forge PlaySoundEvent 回调：洞穴环境音 */
    public static void onSound(String name) {
        if (!Config.modDepth || name == null) return;
        String id = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        if (id.startsWith("ambient.cave")) triggerCave(true);
    }

    public static void tick(float dt, Minecraft mc) {
        hold = Math.max(0f, hold - dt);
        caveHold = Math.max(0f, caveHold - dt);
        caveCooldown = Math.max(0f, caveCooldown - dt);
        if (!Config.modDepth) { bandReady = false; return; }

        LocalPlayer p = mc.player;
        Level lv = mc.level;
        if (p == null || lv == null) return;

        BlockPos pos = p.blockPosition();
        int y = pos.getY();

        // ---- 矿物带变化 ----
        String band = bandOf(lv, y);
        if (!bandReady) { lastBand = band; bandReady = true; return; }
        if (!band.equals(lastBand)) {
            lastBand = band;
            title = band;
            sub = ProgressTracker.I18nS.tr("dynamicisland.depth.at", y);
            progress = depthProgress(y);
            warn = y <= -40;
            hold = Config.depthHold;
        }

        // ---- 洞穴 / 地下预警 ----
        boolean cave = isCave(lv, pos);
        if (cave && !lastCave && caveCooldown <= 0f) triggerCave(false);
        lastCave = cave;
    }

    private static void triggerCave(boolean bySound) {
        if (caveCooldown > 0f && !bySound) return;
        Minecraft mc = Minecraft.getInstance();
        int y = mc.player == null ? 0 : mc.player.blockPosition().getY();
        title = ProgressTracker.I18nS.tr("dynamicisland.depth.cave");
        sub = (bySound ? "♪ " : "") + ProgressTracker.I18nS.tr("dynamicisland.depth.at", y);
        progress = 0f;
        warn = true;
        caveHold = Config.depthHold;
        hold = Config.depthHold;
        caveCooldown = 30f; // 别反复弹
    }

    private static boolean isCave(Level lv, BlockPos pos) {
        try {
            if (lv.canSeeSky(pos)) return false;
            return pos.getY() < 60;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 返回当前 Y 对应的矿物带名称（已按当前语言取好） */
    public static String bandOf(Level lv, int y) {
        try {
            Identifier dim = lv.dimension().identifier();
            if (dim.equals(Identifier.withDefaultNamespace("the_nether"))) {
                return ProgressTracker.I18nS.tr(y >= 8 && y <= 22
                        ? "dynamicisland.depth.netherite" : "dynamicisland.depth.nether");
            }
            if (dim.equals(Identifier.withDefaultNamespace("the_end"))) {
                return ProgressTracker.I18nS.tr("dynamicisland.depth.end");
            }
        } catch (Throwable ignored) { }

        if (y >= 200) return ProgressTracker.I18nS.tr("dynamicisland.depth.sky");
        if (y >= 120) return ProgressTracker.I18nS.tr("dynamicisland.depth.highland");
        if (y >= 80) return ProgressTracker.I18nS.tr("dynamicisland.depth.emerald");
        if (y >= 48) return ProgressTracker.I18nS.tr("dynamicisland.depth.coal");
        if (y >= 16) return ProgressTracker.I18nS.tr("dynamicisland.depth.iron");
        if (y >= 0) return ProgressTracker.I18nS.tr("dynamicisland.depth.deepslate");
        if (y >= -20) return ProgressTracker.I18nS.tr("dynamicisland.depth.lapis");
        if (y >= -40) return ProgressTracker.I18nS.tr("dynamicisland.depth.gold");
        if (y >= -50) return ProgressTracker.I18nS.tr("dynamicisland.depth.diamond");
        return ProgressTracker.I18nS.tr("dynamicisland.depth.deep");
    }

    private static float depthProgress(int y) {
        // 0 = 世界顶部，1 = 世界底部，用于进度条示意
        return Anim.clamp((64f + 320f - (y + 64f)) / 384f, 0f, 1f);
    }

    public static boolean active() { return Config.modDepth && (hold > 0f || caveHold > 0f); }

    public static IslandStatus status() {
        if (!active()) return null;
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.DEPTH;
        s.icon = ItemStack.EMPTY;
        s.title = title;
        s.sub = sub;
        s.progress = progress;
        s.showBar = true;
        s.accent = warn ? Config.theme.warn : Config.theme.accent;
        return s;
    }

    public static void reset() {
        hold = 0f; caveHold = 0f; caveCooldown = 0f; bandReady = false; lastBand = ""; lastCave = false;
    }
}
