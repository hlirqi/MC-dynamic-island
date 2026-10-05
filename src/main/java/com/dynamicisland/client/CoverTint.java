package com.dynamicisland.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 从专辑封面里提取主色调，用来给胶囊做「跟随封面」的配色（Apple Music 那种观感）。
 *
 * 26.x 起纹理统一由 GPU 管（glGetTexImage 那套回读已经不存在），
 * 只有自己创建的 {@link DynamicTexture} 还留着 CPU 端像素可以算。
 * 命中不了就返回 -1，由调用方回退到皮肤强调色 —— 功能降级，不影响其它部分。
 *
 * 开销不小，所以按 Identifier 缓存，一首歌只在切歌后算一次。
 */
public class CoverTint {

    private static Identifier cachedKey = null;
    private static int cachedColor = -1;
    /** 纹理真实宽高比，用于按 cover 方式裁剪，避免方形封面被拉扁 */
    private static float cachedAspect = 1f;

    public static int of(Identifier loc) {
        if (loc == null) return -1;
        if (loc.equals(cachedKey)) return cachedColor;
        int c = compute(loc);
        cachedKey = loc;
        cachedColor = c;
        return c;
    }

    public static void invalidate() {
        cachedKey = null;
        cachedColor = -1;
        cachedAspect = 1f;
    }

    /** 上次取色那张纹理的宽高比（宽/高），没取到时按 1 处理 */
    public static float aspect() { return cachedAspect > 0f ? cachedAspect : 1f; }

    private static int compute(Identifier loc) {
        try {
            if (!(Minecraft.getInstance().getTextureManager().getTexture(loc) instanceof DynamicTexture dt)) return -1;
            NativeImage img = dt.getPixels();
            if (img == null) return -1;
            int w = img.getWidth(), h = img.getHeight();
            if (w <= 0 || h <= 0 || (long) w * h > 4_000_000L) return -1;
            cachedAspect = w / (float) h;
            return dominant(img, w, h);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int dominant(NativeImage img, int w, int h) {
        // 量化到 4bit/通道的直方图，取出现最多的那一桶做平均
        Map<Integer, long[]> buckets = new HashMap<>();
        int step = Math.max(1, Math.min(w, h) / 64);   // 采样，不逐像素
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int argb = img.getPixel(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a < 128) continue;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                if (max < 26) continue;      // 太黑，压不住背景
                if (min > 232) continue;     // 太白，会糊成一片
                int key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                long[] acc = buckets.computeIfAbsent(key, k -> new long[4]);
                acc[0]++; acc[1] += r; acc[2] += g; acc[3] += b;
            }
        }
        long best = 0;
        long[] bestAcc = null;
        for (long[] acc : buckets.values()) {
            if (acc[0] > best) { best = acc[0]; bestAcc = acc; }
        }
        if (bestAcc == null) return -1;
        int r = (int) (bestAcc[1] / bestAcc[0]);
        int g = (int) (bestAcc[2] / bestAcc[0]);
        int b = (int) (bestAcc[3] / bestAcc[0]);
        return boost(r, g, b);
    }

    /** 提亮提饱和一点，免得从深色封面上挑出来的颜色发灰没精神 */
    private static int boost(int r, int g, int b) {
        float max = Math.max(r, Math.max(g, b));
        if (max < 1f) return -1;
        float k = Math.min(1.45f, 210f / max);
        r = (int) Math.min(255, r * k);
        g = (int) Math.min(255, g * k);
        b = (int) Math.min(255, b * k);
        return (r << 16) | (g << 8) | b;
    }
}
