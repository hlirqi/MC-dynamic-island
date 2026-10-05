package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

/**
 * 昼夜事件与天气：
 * - 天气切换（开始下雨 / 雨停 / 雷暴）是瞬时事件，照旧在胶囊提示一次就结束。
 * - 临近关键时间点（06:00 / 12:00 / 18:00 / 00:00）会提前若干秒开始预告：
 *   先在胶囊显示几秒，随后并入灵动焦点，焦点里用圆环表示距离该时间点还剩多久。
 */
public class DayClock {

    public static String title = "";
    public static String sub = "";
    public static float progress = 0f;
    public static boolean warn = false;

    /** 焦点里显示的短文本（剩余秒数） */
    public static String center = "";

    private static float hold = 0f;
    private static float islandTimer = 0f;
    private static boolean pinned = false;
    private static boolean lastRain = false, lastThunder = false;
    private static boolean stateReady = false;
    private static long lastMilestone = -1;
    private static long targetMark = -1;

    /** 关键时间点：MC dayTime 0=06:00, 6000=12:00, 12000=18:00, 18000=00:00 */
    private static final long[] MARKS = {0L, 6000L, 12000L, 18000L};
    private static final String[] MARK_KEYS = {
            "dynamicisland.time.dawn", "dynamicisland.time.noon",
            "dynamicisland.time.dusk", "dynamicisland.time.midnight"
    };

    /** 提前多少 tick 开始预告（5~10 秒可调） */
    private static long warnTicks() { return Math.max(20L, (long) (Config.dayLeadSec * 20f)); }

    public static void tick(float dt, Minecraft mc) {
        hold = Math.max(0f, hold - dt);
        if (islandTimer > 0f) islandTimer -= dt;

        if (!Config.modDayTime) { stateReady = false; targetMark = -1; return; }

        LocalPlayer p = mc.player;
        Level lv = mc.level;
        if (p == null || lv == null) return;

        boolean rain = lv.isRaining();
        boolean thunder = lv.isThundering();
        if (!stateReady) { lastRain = rain; lastThunder = thunder; stateReady = true; return; }

        // ---- 天气切换：瞬时事件，不并入焦点 ----
        if (thunder != lastThunder) {
            fire(thunder ? "dynamicisland.weather.thunder"
                    : (rain ? "dynamicisland.weather.rain" : "dynamicisland.weather.clear"), false, 0f);
        } else if (rain != lastRain) {
            fire(rain ? "dynamicisland.weather.rain" : "dynamicisland.weather.clear", false, 0f);
        }
        lastRain = rain;
        lastThunder = thunder;

        // ---- 临近关键时间点 ----
        long clock = Metrics.dayTicks(lv);
        long day = clock % 24000L;
        long wt = warnTicks();
        long bestMark = -1L, bestRemain = Long.MAX_VALUE;
        for (long mark : MARKS) {
            long remain = mark - day;
            if (remain < 0) remain += 24000L;
            if (remain > wt) continue;
            if (remain < bestRemain) { bestRemain = remain; bestMark = mark; }
        }

        if (bestMark >= 0) {
            if (lastMilestone != bestMark) {
                lastMilestone = bestMark;
                targetMark = bestMark;
                int idx = indexOf(bestMark);
                fire(MARK_KEYS[idx], true, 1f);
                // 开启了焦点就先占胶囊几秒，再让位给焦点
                islandTimer = (Config.focusEnabled && Config.focusDay) ? Config.dayTimeHold : 0f;
                pinned = false;
            }
            // 焦点里看的是「还剩多少」，所以随倒计时递减到 0
            progress = Anim.clamp(bestRemain / (float) wt, 0f, 1f);
            sub = Math.max(0, Math.round(bestRemain / 20f)) + "s";
            center = String.valueOf(Math.max(0, Math.round(bestRemain / 20f)));
        } else {
            targetMark = -1;
            lastMilestone = -1;
            // 关键点过去之后不能再留旧数值，否则焦点/胶囊会一直显示上一次的 0
            if (hold <= 0f) { center = ""; sub = ""; progress = 0f; }
        }
    }

    private static int indexOf(long mark) {
        for (int i = 0; i < MARKS.length; i++) if (MARKS[i] == mark) return i;
        return 0;
    }

    private static void fire(String titleKey, boolean timeMode, float prog) {
        title = ProgressTracker.I18nS.tr(titleKey);
        sub = timeMode
                ? ProgressTracker.I18nS.tr("dynamicisland.time.current", Metrics.timeStr)
                : Metrics.weather;
        progress = prog;
        warn = !timeMode;
        hold = Config.dayTimeHold;
    }

    /** 此刻是否由胶囊显示（false 表示已让位给灵动焦点） */
    public static boolean onIsland() {
        if (!Config.focusEnabled || !Config.focusDay) return true; // 未启用焦点：维持原行为
        return pinned || islandTimer > 0f;
    }

    /** 是否被手动钉在胶囊上 */
    public static boolean pinned() { return pinned; }

    /** 呼出 / 收回：再按一次才退回焦点 */
    public static void bringBack() {
        if (targetMark < 0 || !Config.focusEnabled || !Config.focusDay) return;
        pinned = !pinned;
        if (pinned) islandTimer = 0f;
    }

    public static boolean active() { return Config.modDayTime && hold > 0f; }

    /** 该由灵动焦点接管显示 */
    public static boolean focusActive() {
        return Config.modDayTime && Config.focusEnabled && Config.focusDay
                && targetMark >= 0 && !onIsland();
    }

    public static IslandStatus status() {
        if (!active()) return null;
        if (Config.focusEnabled && Config.focusDay && targetMark >= 0 && !onIsland()) return null;
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.CLOCK;
        s.icon = net.minecraft.world.item.ItemStack.EMPTY;
        s.title = title;
        s.sub = sub;
        s.progress = progress;
        s.showBar = true;
        s.accent = warn ? Config.theme.warn : Config.theme.accent;
        return s;
    }

    public static void reset() {
        hold = 0f; islandTimer = 0f; pinned = false; stateReady = false;
        lastMilestone = -1; targetMark = -1; center = "";
    }
}
