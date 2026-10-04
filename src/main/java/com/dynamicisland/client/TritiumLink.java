package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Tritium Music（modId: tritium_music）联动适配层。
 *
 * 该模组没有正式 API 包，能用的是 tritium.music.core 下的 public 静态类 —— 属于「事实接口」，
 * 作者并未承诺兼容。因此这里**全程反射**：编译期不引用它的任何类，
 * 未安装或类名变更时静默降级，绝不影响本模组启动。
 *
 * 进度没有回调，只能自己在客户端 tick 里轮询（官方接入指南也是这么建议的）。
 */
public class TritiumLink {

    private static final String MOD_ID = "tritium_music";
    private static final String CLOUD_MUSIC = "tritium.music.core.CloudMusic";

    // 反射出来的入口
    private static boolean checked = false;
    private static boolean loaded = false;
    private static Field fCurrentlyPlaying, fPlayer;
    private static Method mLoadCover;

    private static final Map<String, Method> methodCache = new HashMap<>();

    // 当前歌曲
    public static String title = "";
    public static String artist = "";
    public static String sub = "";
    public static float progress = 0f;
    public static boolean playing = false;
    public static ResourceLocation cover = null;
    /** 模组自带的模糊封面，展开时拿它当胶囊背景 */
    public static ResourceLocation blurred = null;
    /** 从封面提出来的主色调，-1 表示还没取到 */
    public static int tint = -1;

    // 上台 / 焦点 / 收尾提醒
    private static float islandTimer = 0f;
    private static boolean pinned = false;
    private static String lastId = "";
    private static boolean warnedEnd = false;

    // ---------- 可用性 ----------

    /** 目标模组已加载，且我们能拿到入口类 */
    public static boolean available() {
        if (!checked) init();
        return loaded;
    }

    private static void init() {
        checked = true;
        loaded = false;
        try {
            if (!ModList.get().isLoaded(MOD_ID)) return;
            Class<?> cloud = Class.forName(CLOUD_MUSIC);
            fCurrentlyPlaying = cloud.getField("currentlyPlaying");
            fPlayer = cloud.getField("player");
            // loadMusicCover 的参数是 Music 类，我们不引用它，只能按名字找
            for (Method m : cloud.getMethods()) {
                if (m.getName().equals("loadMusicCover") && m.getParameterCount() == 1) {
                    mLoadCover = m;
                    break;
                }
            }
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
        }
    }

    // ---------- 轮询 ----------

    public static void tick(float dt, Minecraft mc) {
        if (!Config.modTritium || !available()) {
            playing = false;
            islandTimer = 0f;
            return;
        }
        try {
            Object music = fCurrentlyPlaying.get(null);
            Object player = fPlayer.get(null);
            if (music == null || player == null) { playing = false; return; }

            Boolean pausing = bool(player, "isPausing");
            Boolean isPlaying = bool(player, "isPlaying");
            boolean on = !(pausing != null && pausing) && (isPlaying == null || isPlaying);
            if (!on) { playing = false; return; }

            Object idObj = invoke(music, "getId");
            String id = idObj == null ? String.valueOf(music.hashCode()) : String.valueOf(idObj);

            // 优先用插值进度，进度条才不会一格一格地跳（官方文档：interpolated 是给渲染平滑用的）
            Float curBox = fltBox(player, "getCurrentTimeMillisInterpolated");
            if (curBox == null) curBox = fltBox(player, "getCurrentTimeMillis");
            float cur = curBox == null ? 0f : curBox;
            float total = flt(player, "getTotalTimeMillis", 0f);
            if (total <= 0f) {
                Float t2 = fltBox(music, "getDuration");       // 退回到歌曲自身时长（毫秒）
                total = t2 == null ? 0f : t2;
            }

            if (!id.equals(lastId)) {
                lastId = id;
                warnedEnd = false;
                islandTimer = Config.focusEnabled ? Config.tritiumHold : 0f;
                pinned = false;
                title = str(music, "getMainTitle", str(music, "getName", ""));
                artist = str(music, "getArtistsName", "");
                cover = null;
                blurred = null;
                tint = -1;
                CoverTint.invalidate();
                // 封面未必已加载，主动触发一次
                if (mLoadCover != null) {
                    try { mLoadCover.invoke(null, music); } catch (Throwable ignored) { }
                }
            }

            playing = true;
            progress = total > 0f ? Anim.clamp(cur / total, 0f, 1f) : 0f;
            float remainSec = Math.max(0f, (total - cur) / 1000f);
            sub = format((int) remainSec);
            if (cover == null) {
                cover = coverOf(music);
                blurred = coverOf(music, "getBlurredCoverLocation");
            }
            // 封面真正上传完才能取色，所以这里等 ready 再算一次
            if (tint < 0 && coverReady()) tint = CoverTint.of(cover);

            // 快放完时把焦点内容收回胶囊提示一次
            if (!warnedEnd && remainSec > 0f && remainSec <= Config.tritiumEndWarn) {
                warnedEnd = true;
                islandTimer = Config.focusEnabled ? Config.tritiumHold : 0f;
            }

            if (islandTimer > 0f) islandTimer -= dt;
        } catch (Throwable t) {
            playing = false;
        }
    }

    // ---------- 对外状态 ----------

    public static boolean onIsland() {
        if (!Config.focusEnabled) return true;   // 未启用焦点时维持一直显示，与内置音乐行为一致
        return pinned || islandTimer > 0f;
    }

    public static boolean focusActive() {
        return Config.modTritium && Config.focusEnabled && playing && !onIsland();
    }

    /** 是否被手动钉在胶囊上 */
    public static boolean pinned() { return pinned; }

    /** 呼出 / 收回：再按一次才退回焦点 */
    public static void bringBack() {
        if (!playing || !Config.focusEnabled) return;
        pinned = !pinned;
        if (pinned) islandTimer = 0f;
    }

    public static void reset() {
        playing = false; islandTimer = 0f; pinned = false; lastId = ""; warnedEnd = false;
        title = ""; artist = ""; sub = ""; progress = 0f; cover = null;
        blurred = null; tint = -1; CoverTint.invalidate();
    }

    // ---------- 封面 ----------

    private static ResourceLocation coverOf(Object music) {
        return coverOf(music, "getCoverLocation");
    }

    private static ResourceLocation coverOf(Object music, String method) {
        try {
            Object handle = invoke(music, method);
            if (handle == null) return null;
            String ns = str(handle, "namespace", null);
            String path = str(handle, "path", null);
            if (ns == null || path == null) return null;
            return new ResourceLocation(ns, path);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 模糊封面是否已就绪 */
    public static boolean blurredReady() {
        if (blurred == null) return false;
        try {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(blurred);
            return t != null && t.getId() >= 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 纹理是否真的加载好了 —— 没就绪就别画，免得糊一个缺失纹理格子在圆里。
     * 异步加载需要几帧，所以刚切歌时这里会是 false，随后自动变 true。
     */
    public static boolean coverReady() {
        if (cover == null) return false;
        try {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(cover);
            if (t == null) return false;
            return t.getId() >= 0;   // -1 表示还没上传到 GPU
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------- 反射小工具 ----------

    private static Method method(Object obj, String name) {
        if (obj == null) return null;
        String key = obj.getClass().getName() + "#" + name;
        Method m = methodCache.get(key);
        if (m == null) {
            try {
                m = obj.getClass().getMethod(name);
                methodCache.put(key, m);
            } catch (Throwable t) {
                return null;
            }
        }
        return m;
    }

    private static Object invoke(Object obj, String name) {
        try {
            Method m = method(obj, name);
            return m == null ? null : m.invoke(obj);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String str(Object obj, String name, String fallback) {
        Object v = invoke(obj, name);
        if (v instanceof String s && !s.isEmpty()) return s;
        return fallback;
    }

    private static Boolean bool(Object obj, String name) {
        Object v = invoke(obj, name);
        return v instanceof Boolean b ? b : null;
    }

    private static float flt(Object obj, String name, float fallback) {
        Float f = fltBox(obj, name);
        return f == null ? fallback : f;
    }

    private static Float fltBox(Object obj, String name) {
        Object v = invoke(obj, name);
        if (v instanceof Number n) return n.floatValue();
        return null;
    }

    private static String format(int secs) {
        int m = secs / 60, s = secs % 60;
        return m + ":" + String.format("%02d", s);
    }
}
