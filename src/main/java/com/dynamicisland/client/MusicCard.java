package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Locale;

/**
 * 唱片 / 背景音乐：胶囊里显示曲名 + 进度，像 iOS 的播放控件。
 *
 * 唱片：扫描附近的唱片机（半径 16 格），时长用 RecordItem#getLengthInTicks，进度精确到秒。
 * 背景音乐：由 Forge 的 PlaySoundEvent 在声音播放时打进来（见 ClientEvents #onSound）。
 */
public class MusicCard {

    public static String title = "";
    public static String sub = "";
    public static float progress = 0f;
    public static ItemStack icon = ItemStack.EMPTY;
    public static boolean playing = false;
    public static boolean isDisc = false;

    private static long startedAt = 0L;
    private static float durationSec = 180f;
    private static float grace = 0f;
    private static float scanAccum = 0f;
    private static String lastKey = "";
    private static String discPosKey = "";

    /** 还剩下多少秒继续占用胶囊；归零后交给灵动焦点驻留 */
    private static float islandTimer = 0f;
    /** 手动呼出后钉在胶囊上，直到再按一次才退回焦点 */
    private static boolean pinned = false;

    /** 此刻是否应该由胶囊详细显示（钉住 > 上台倒计时） */
    public static boolean onIsland() {
        if (!Config.focusEnabled) return true; // 未启用焦点：维持原本一直占胶囊的行为
        return pinned || islandTimer > 0f;
    }

    /** 是否被手动钉在胶囊上 */
    public static boolean pinned() { return pinned; }

    /** 呼出 / 收回：再按一次才退回焦点 */
    public static void bringBack() {
        if (!Config.focusEnabled || !playing) return;
        pinned = !pinned;
        if (pinned) islandTimer = 0f;
    }

    /** Forge PlaySoundEvent 回调：音乐 / 唱片开始播放 */
    public static void onSound(String name) {
        if (!Config.modMusic || name == null) return;
        String id = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;

        if (id.startsWith("music_disc.")) {
            String disc = id.substring("music_disc.".length());
            start(discTitle(disc), disc, true, discItem(disc));
            return;
        }
        if (id.startsWith("music.")) {
            start(beautify(id.substring("music.".length())), id, false, ItemStack.EMPTY);
        }
    }

    private static void start(String name, String key, boolean disc, ItemStack stack) {
        if (key.equals(lastKey) && playing) return;
        lastKey = key;
        if (disc) discPosKey = key;
        title = name;
        icon = stack;
        isDisc = disc;
        startedAt = System.currentTimeMillis();
        durationSec = disc ? discDuration(stack) : 180f;
        playing = true;
        grace = 0f;
        islandTimer = Config.focusEnabled ? Config.focusHold : 0f;
        pinned = false;
    }

    private static ItemStack discItem(String disc) {
        try {
            ItemStack s = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    new ResourceLocation("minecraft", "music_disc_" + disc.toLowerCase(Locale.ROOT))));
            return s.isEmpty() ? ItemStack.EMPTY : s;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private static String discTitle(String disc) {
        try {
            ItemStack s = discItem(disc);
            if (!s.isEmpty()) return s.getHoverName().getString();
        } catch (Throwable ignored) { }
        return disc.toUpperCase(Locale.ROOT);
    }

    private static float discDuration(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof RecordItem r) {
            try { return Math.max(1f, r.getLengthInTicks() / 20f); } catch (Throwable ignored) { }
        }
        return 180f;
    }

    /** 兜底时长表：拿不到 RecordItem 时按原版唱片长度估算（秒） */
    private static float fallbackDuration(String disc) {
        switch (disc.toLowerCase(Locale.ROOT)) {
            case "13": return 178f;
            case "cat": return 185f;
            case "blocks": return 345f;
            case "chirp": return 185f;
            case "far": return 174f;
            case "mall": return 197f;
            case "mellohi": return 96f;
            case "stal": return 150f;
            case "strad": return 188f;
            case "ward": return 251f;
            case "11": return 71f;
            case "wait": return 238f;
            case "pigstep": return 148f;
            case "otherside": return 193f;
            case "relic": return 218f;
            case "5": return 178f;
            case "precipice": return 299f;
            case "creator": return 178f;
            default: return 180f;
        }
    }

    public static void tick(float dt, Minecraft mc) {
        if (!Config.modMusic) { playing = false; return; }
        LocalPlayer p = mc.player;
        Level lv = mc.level;
        if (p == null || lv == null) { playing = false; return; }

        // ---- 唱片机：真实停止检测（每 0.5 秒扫一次周围区块） ----
        scanAccum += dt;
        boolean scanNow = scanAccum >= 0.5f;
        if (scanNow) scanAccum = 0f;
        boolean jukeboxPlaying = false;
        if (scanNow) {
            try {
                BlockPos c = p.blockPosition();
                int cx = c.getX() >> 4, cz = c.getZ() >> 4;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        var chunk = lv.getChunk(cx + dx, cz + dz);
                        if (chunk == null) continue;
                        for (BlockEntity be : chunk.getBlockEntities().values()) {
                            if (!(be instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity jb)) continue;
                            if (be.getBlockPos().distSqr(c) > 16 * 16) continue; // 太远听不见
                            if (!jb.isRecordPlaying()) continue;
                            jukeboxPlaying = true;
                            ItemStack rec = jb.getItem(0);
                            String key = "jb:" + be.getBlockPos();
                            discPosKey = key;
                            if (!key.equals(lastKey)) {
                                String nm = rec.isEmpty() ? "?" : rec.getHoverName().getString();
                                start(nm, key, true, rec.isEmpty() ? ItemStack.EMPTY : rec);
                                durationSec = rec.isEmpty() ? 180f : discDuration(rec);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) { }
        } else if (isDisc && discPosKey.equals(lastKey)) {
            jukeboxPlaying = true; // 两次扫描之间保持显示，避免闪烁
        }

        if (isDisc && !jukeboxPlaying) {
            grace += dt;
            if (grace > 1.2f) { playing = false; lastKey = ""; }
        } else {
            grace = 0f;
        }

        if (!playing) return;

        if (islandTimer > 0f) islandTimer -= dt; // 到点后自动让位给灵动焦点

        float elapsed = (System.currentTimeMillis() - startedAt) / 1000f;
        progress = Anim.clamp(elapsed / Math.max(1f, durationSec), 0f, 1f);
        int left = (int) Math.max(0f, durationSec - elapsed);
        sub = isDisc
                ? format(left)
                : "♪ " + format(left);

        if (elapsed > durationSec + 1f) { playing = false; lastKey = ""; }
    }

    private static String format(int secs) {
        int m = secs / 60, s = secs % 60;
        return m + ":" + String.format("%02d", s);
    }

    /** music.overworld.meadow → overworld meadow（设置里可再加翻译） */
    private static String beautify(String path) {
        String s = path.replace('_', ' ').trim();
        if (s.isEmpty()) return path;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static IslandStatus status() {
        if (!Config.modMusic || !playing) return null;
        if (!onIsland()) return null; // 已过渡到灵动焦点，把胶囊让出来
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.MUSIC;
        s.icon = icon;
        s.title = "♪ " + title;
        s.sub = sub;
        s.progress = progress;
        s.accent = Config.theme.accent;
        s.showBar = true;
        return s;
    }

    public static void reset() { playing = false; lastKey = ""; startedAt = 0L; progress = 0f; islandTimer = 0f; pinned = false; }
}
