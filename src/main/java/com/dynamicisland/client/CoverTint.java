package com.dynamicisland.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * 从专辑封面里提取主色调，用来给胶囊做「跟随封面」的配色（Apple Music 那种观感）。
 *
 * 纹理已经上传到 GPU，只能 glGetTexImage 读回来。开销不小，所以按 ResourceLocation 缓存，
 * 一首歌只在切歌后算一次。失败一律返回 -1，由调用方回退到皮肤强调色。
 */
public class CoverTint {

    private static ResourceLocation cachedKey = null;
    private static int cachedColor = -1;
    /** 纹理真实宽高比，用于按 cover 方式裁剪，避免方形封面被拉扁 */
    private static float cachedAspect = 1f;

    public static int of(ResourceLocation loc) {
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

    private static int compute(ResourceLocation loc) {
        try {
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(loc);
            if (tex == null) return -1;
            int id = tex.getId();
            if (id < 0) return -1;

            RenderSystem.bindTexture(id);
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w <= 0 || h <= 0 || (long) w * h > 4_000_000L) return -1;
            cachedAspect = w / (float) h;

            ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            return dominant(buf, w, h);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int dominant(ByteBuffer buf, int w, int h) {
        // 量化到 4bit/通道的直方图，取出现最多的那一桶做平均
        Map<Integer, long[]> buckets = new HashMap<>();
        int step = Math.max(1, Math.min(w, h) / 64);   // 采样，不逐像素
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int i = (y * w + x) * 4;
                int a = buf.get(i + 3) & 0xFF;
                if (a < 128) continue;
                int r = buf.get(i) & 0xFF;
                int g = buf.get(i + 1) & 0xFF;
                int b = buf.get(i + 2) & 0xFF;
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
