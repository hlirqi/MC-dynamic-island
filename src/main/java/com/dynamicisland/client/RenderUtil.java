package com.dynamicisland.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.GameRenderer;

/** 圆角矩形 / 颜色工具，纯顶点绘制，无需贴图。 */
public class RenderUtil {

    public static int argb(int rgb, float alpha) {
        int a = (int) (Anim.clamp(alpha, 0f, 1f) * 255f) & 0xFF;
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    public static int mix(int c1, int c2, float t) {
        t = Anim.clamp(t, 0f, 1f);
        int r = (int) Anim.lerp((c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF, t);
        int g = (int) Anim.lerp((c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF, t);
        int b = (int) Anim.lerp(c1 & 0xFF, c2 & 0xFF, t);
        return (r << 16) | (g << 8) | b;
    }

    /** 凸多边形三角扇绘制的圆角矩形 */
    public static void roundRect(PoseStack ps, float x, float y, float w, float h, float r, int argb) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) * 0.5f);
        Matrix4f m = ps.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        float cx = x + w * 0.5f, cy = y + h * 0.5f;
        bb.vertex(m, cx, cy, 0f).color(argb).endVertex();

        int seg = Math.max(3, (int) (r * 0.6f) + 3);
        // 左上 180->270
        arc(bb, m, x + r, y + r, r, 180, 270, seg, argb);
        // 右上 270->360
        arc(bb, m, x + w - r, y + r, r, 270, 360, seg, argb);
        // 右下 0->90
        arc(bb, m, x + w - r, y + h - r, r, 0, 90, seg, argb);
        // 左下 90->180
        arc(bb, m, x + r, y + h - r, r, 90, 180, seg, argb);
        // 闭合到起点
        bb.vertex(m, x, y + r, 0f).color(argb).endVertex();

        BufferUploader.drawWithShader(bb.end());
        // 混合保持开启：GUI 阶段后续文字/物品渲染依赖混合状态
    }

    private static void arc(BufferBuilder bb, Matrix4f m, float cx, float cy, float r, double a0, double a1, int seg, int argb) {
        for (int i = 0; i <= seg; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * ((double) i / seg));
            bb.vertex(m, cx + (float) (Math.cos(a) * r), cy + (float) (Math.sin(a) * r), 0f).color(argb).endVertex();
        }
    }

    /**
     * 圆形贴图：把一张方形贴图裁成圆来画，专辑封面塞进圆里就不会显得方方正正。
     * UV 取纹理中心的正方形区域映射到圆，四角自然被裁掉。
     */
    public static void roundImage(PoseStack ps, ResourceLocation tex, float cx, float cy, float r, float alpha) {
        if (r <= 0.5f || tex == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShaderColor(1f, 1f, 1f, Anim.clamp(alpha, 0f, 1f));

        Matrix4f m = ps.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, cx, cy, 0f).uv(0.5f, 0.5f).endVertex();
        int seg = Math.max(16, (int) (r * 2.0f));
        for (int i = 0; i <= seg; i++) {
            double a = Math.toRadians(-90.0 + 360.0 * i / seg);
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            bb.vertex(m, cx + ca * r, cy + sa * r, 0f)
                    .uv(0.5f + ca * 0.5f, 0.5f + sa * 0.5f).endVertex();
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * 圆角矩形贴图：把一张贴图铺进圆角矩形（用于展开时用模糊封面当胶囊背景）。
     * UV 按顶点在矩形里的相对位置归一化，纹理会被拉伸铺满。
     */
    public static void roundRectImage(PoseStack ps, ResourceLocation tex, float x, float y,
                                      float w, float h, float r, float alpha) {
        roundRectImage(ps, tex, x, y, w, h, r, alpha, 0f, 0f, 1f, 1f);
    }

    /**
     * 指定 UV 区域的版本。传入的不是整张纹理，而是中心的一块（u0,v0)-(u1,v1)，
     * 这样能按纹理真实比例取样，避免方形封面被拉成胶囊的扁宽形状。
     */
    public static void roundRectImage(PoseStack ps, ResourceLocation tex, float x, float y,
                                      float w, float h, float r, float alpha,
                                      float u0, float v0, float u1, float v1) {
        if (w <= 0f || h <= 0f || tex == null) return;
        r = Math.min(r, Math.min(w, h) * 0.5f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShaderColor(1f, 1f, 1f, Anim.clamp(alpha, 0f, 1f));

        Matrix4f m = ps.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_TEX);

        float cx = x + w * 0.5f, cy = y + h * 0.5f;
        bb.vertex(m, cx, cy, 0f).uv((u0 + u1) * 0.5f, (v0 + v1) * 0.5f).endVertex();

        int seg = Math.max(3, (int) (r * 0.6f) + 3);
        arcUV(bb, m, x + r, y + r, r, 180, 270, seg, x, y, w, h, u0, v0, u1, v1);
        arcUV(bb, m, x + w - r, y + r, r, 270, 360, seg, x, y, w, h, u0, v0, u1, v1);
        arcUV(bb, m, x + w - r, y + h - r, r, 0, 90, seg, x, y, w, h, u0, v0, u1, v1);
        arcUV(bb, m, x + r, y + h - r, r, 90, 180, seg, x, y, w, h, u0, v0, u1, v1);
        bb.vertex(m, x, y + r, 0f).uv(u0, v0 + (r / h) * (v1 - v0)).endVertex();

        BufferUploader.drawWithShader(bb.end());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void arcUV(BufferBuilder bb, Matrix4f m, float acx, float acy, float r,
                              double a0, double a1, int seg, float x, float y, float w, float h,
                              float u0, float v0, float u1, float v1) {
        for (int i = 0; i <= seg; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * ((double) i / seg));
            float vx = acx + (float) (Math.cos(a) * r);
            float vy = acy + (float) (Math.sin(a) * r);
            float ru = (vx - x) / w, rv = (vy - y) / h;
            bb.vertex(m, vx, vy, 0f).uv(u0 + ru * (u1 - u0), v0 + rv * (v1 - v0)).endVertex();
        }
    }

    /** 描边圆角矩形：外层大一圈，内层覆盖 */
    public static void roundRectOutline(PoseStack ps, float x, float y, float w, float h, float r, float thickness, int argb) {
        roundRect(ps, x - thickness, y - thickness, w + thickness * 2, h + thickness * 2, r + thickness, argb);
    }

    /** 三层递减 alpha 的伪阴影 */
    public static void shadow(PoseStack ps, float x, float y, float w, float h, float r, int rgb, float strength) {
        if (strength <= 0) return;
        roundRect(ps, x - 2f, y - 1f, w + 4f, h + 4f, r + 2f, argb(rgb, 0.10f * strength));
        roundRect(ps, x - 1f, y, w + 2f, h + 2f, r + 1f, argb(rgb, 0.14f * strength));
        roundRect(ps, x, y + 1f, w, h + 1f, r, argb(rgb, 0.18f * strength));
    }

    /** 带背景的进度条 */
    public static void progressBar(PoseStack ps, float x, float y, float w, float h, float p, int bg, int fg) {
        roundRect(ps, x, y, w, h, h * 0.5f, bg);
        float fw = Math.max(h, w * Anim.clamp(p, 0f, 1f));
        roundRect(ps, x, y, fw, h, h * 0.5f, fg);
    }

    /** 帧率折线（用细长四边形逐段绘制，避免线宽兼容问题） */
    public static void graph(PoseStack ps, float x, float y, float w, float h, int[] data, int count, int color, float alpha) {
        if (count < 2) return;
        Matrix4f m = ps.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int max = 1;
        for (int i = 0; i < count; i++) max = Math.max(max, data[i]);
        max = Math.max(max, 60);
        int argb = argb(color, alpha);
        float th = 1.3f;
        for (int i = 0; i < count - 1; i++) {
            float x0 = x + w * ((float) i / (count - 1));
            float x1 = x + w * ((float) (i + 1) / (count - 1));
            float y0 = y + h - h * Anim.clamp(data[i] / (float) max, 0f, 1f);
            float y1 = y + h - h * Anim.clamp(data[i + 1] / (float) max, 0f, 1f);
            float dx = x1 - x0, dy = y1 - y0;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.0001f) continue;
            float nx = -dy / len * th * 0.5f, ny = dx / len * th * 0.5f;
            bb.vertex(m, x0 + nx, y0 + ny, 0f).color(argb).endVertex();
            bb.vertex(m, x1 + nx, y1 + ny, 0f).color(argb).endVertex();
            bb.vertex(m, x1 - nx, y1 - ny, 0f).color(argb).endVertex();
            bb.vertex(m, x0 - nx, y0 - ny, 0f).color(argb).endVertex();
        }
        BufferUploader.drawWithShader(bb.end());
        // 混合保持开启：GUI 阶段后续文字/物品渲染依赖混合状态
    }

    /** 圆环进度：冷却 CD、经验环等。角度从正上方开始顺时针。 */
    public static void ring(PoseStack ps, float cx, float cy, float r, float thickness, float p, int argb) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Matrix4f m = ps.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        emitArcBand(bb, m, cx, cy, r, thickness, p, argb);
        BufferUploader.drawWithShader(bb.end());
    }

    private static void emitArcBand(BufferBuilder bb, Matrix4f m, float cx, float cy, float r, float th, float sweep, int argb) {
        sweep = Anim.clamp(sweep, 0f, 1f);
        if (sweep <= 0.0005f) return;
        float ri = Math.max(0.4f, r - th);
        int seg = (int) (48 * sweep) + 4;
        for (int i = 0; i < seg; i++) {
            float a0 = (float) Math.toRadians(-90f + 360f * sweep * (i / (float) seg));
            float a1 = (float) Math.toRadians(-90f + 360f * sweep * ((i + 1) / (float) seg));
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            bb.vertex(m, cx + c0 * ri, cy + s0 * ri, 0f).color(argb).endVertex();
            bb.vertex(m, cx + c1 * ri, cy + s1 * ri, 0f).color(argb).endVertex();
            bb.vertex(m, cx + c1 * r, cy + s1 * r, 0f).color(argb).endVertex();
            bb.vertex(m, cx + c0 * r, cy + s0 * r, 0f).color(argb).endVertex();
        }
    }

    /** 圆环的底环（整圈），陪同 ring() 使用 */
    public static void ringTrack(PoseStack ps, float cx, float cy, float r, float thickness, int argb) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Matrix4f m = ps.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float ri = Math.max(0.4f, r - thickness);
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.toRadians(-90f + 360f * (i / (float) seg));
            double a1 = Math.toRadians(-90f + 360f * ((i + 1) / (float) seg));
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            bb.vertex(m, cx + c0 * ri, cy + s0 * ri, 0f).color(argb).endVertex();
            bb.vertex(m, cx + c1 * ri, cy + s1 * ri, 0f).color(argb).endVertex();
            bb.vertex(m, cx + c1 * r, cy + s1 * r, 0f).color(argb).endVertex();
            bb.vertex(m, cx + c0 * r, cy + s0 * r, 0f).color(argb).endVertex();
        }
        BufferUploader.drawWithShader(bb.end());
    }

    /** 指向目标的三角形箭头，rot=0 指向正上方，正值顺时针旋转 */
    public static void arrow(PoseStack ps, float cx, float cy, float size, float rot, int argb) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Matrix4f m = ps.last().pose();
        float co = (float) Math.cos(rot), si = (float) Math.sin(rot);
        float[] px = {0f, -size * 0.62f, size * 0.62f};
        float[] py = {-size, size * 0.62f, size * 0.62f};
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 3; i++) {
            float x = cx + px[i] * co - py[i] * si;
            float y = cy + px[i] * si + py[i] * co;
            bb.vertex(m, x, y, 0f).color(argb).endVertex();
        }
        BufferUploader.drawWithShader(bb.end());
    }

    public static int fpsColor(int fps, Theme t) {
        if (fps >= 100) return t.good;
        if (fps >= 45) return t.accent;
        if (fps >= 25) return t.warn;
        return t.bad;
    }

    /** 超出给定像素宽度就用省略号截断，避免 UI 里两段文字互相压 */
    public static String elide(Font font, String s, float maxWidth) {
        if (s == null || s.isEmpty()) return "";
        if (font.width(s) <= maxWidth) return s;
        String ell = "…";
        int target = (int) Math.max(1f, maxWidth - font.width(ell));
        if (target < 4) return ell;
        return font.plainSubstrByWidth(s, target) + ell;
    }

    public static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
    }
}
