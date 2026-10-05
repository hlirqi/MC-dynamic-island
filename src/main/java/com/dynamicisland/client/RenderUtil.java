package com.dynamicisland.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * 圆角矩形 / 圆环 / 折线等自绘图形。
 *
 * Minecraft 26.x 的 GUI 已经改成「提取（extract）→ 提交状态 → 统一渲染」的模型：
 * 不允许在渲染回调里直接 gl 画东西，必须构造 {@link GuiElementRenderState} 交给
 * {@link GuiGraphicsExtractor#submitGuiElementRenderState(GuiElementRenderState)}。
 * 所以这里把原来基于 PoseStack + BufferBuilder 的立即绘制，整体改成「拼顶点 → 提交状态」。
 *
 * 三角形扇（圆角矩形 / 圆形贴图 / 箭头）与三角形带（圆环 / 折线）都不是 QUADS，
 * 需要用自定义管线（见 {@link #registerPipelines}）；NeoForge 会为 connectedPrimitives
 * 管线逐个 flush，因此每个扇/带都能正确独立绘制。
 *
 * 注意：同一图层内，元素会先按管线排序再绘制。想保证「后画的压住先画的」，
 * 跨管线的地方要显式调 {@link #layer(GuiGraphicsExtractor)} 开一个新图层。
 */
public class RenderUtil {

    // ---------------- 自定义渲染管线 ----------------

    public static RenderPipeline FAN_COLOR = null;   // 位置+颜色，三角形扇（圆角矩形/圆片/箭头）
    public static RenderPipeline FAN_TEX = null;     // 位置+贴图+颜色，三角形扇（圆形/圆角贴图）
    public static RenderPipeline STRIP_COLOR = null; // 位置+颜色，三角形带（圆环/折线）

    public static void registerPipelines(RegisterRenderPipelinesEvent e) {
        // 必须关掉背面剔除：GUI 管线默认 cull=true，而扇形/带形的绕序（y 轴朝下的屏幕坐标下是逆时针）
        // 会被判成背面，结果是图形整片消失、只剩文字
        FAN_COLOR = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("dynamicisland", "pipeline/gui_fan_color"))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_FAN)
                .withCull(false)
                .build();
        FAN_TEX = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("dynamicisland", "pipeline/gui_fan_tex"))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_FAN)
                .withCull(false)
                .build();
        STRIP_COLOR = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("dynamicisland", "pipeline/gui_strip_color"))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
                .withCull(false)
                .build();
        e.registerPipeline(FAN_COLOR);
        e.registerPipeline(FAN_TEX);
        e.registerPipeline(STRIP_COLOR);
    }

    /** 开一个新图层：保证本图层的内容整体压在之前图层之上 */
    public static void layer(GuiGraphicsExtractor gg) {
        gg.nextStratum();
    }

    // ---------------- 颜色工具 ----------------

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

    // ---------------- 状态提交 ----------------

    /** 一段顶点（扇/带/四边形）构成的 GUI 元素 */
    private static final class ShapeState implements GuiElementRenderState {
        private final RenderPipeline pipeline;
        private final TextureSetup textureSetup;
        private final Matrix3x2fc pose;
        private final float[] xs, ys;
        private final float[] us, vs;   // 无贴图时为 null
        private final int color;
        private final ScreenRectangle scissorArea;
        private final ScreenRectangle bounds;

        ShapeState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
                   float[] xs, float[] ys, float[] us, float[] vs, int color, ScreenRectangle scissorArea) {
            this.pipeline = pipeline;
            this.textureSetup = textureSetup;
            this.pose = pose;
            this.xs = xs;
            this.ys = ys;
            this.us = us;
            this.vs = vs;
            this.color = color;
            this.scissorArea = scissorArea;
            this.bounds = buildBounds(xs, ys, pose, scissorArea);
        }

        private static ScreenRectangle buildBounds(float[] xs, float[] ys, Matrix3x2fc pose, ScreenRectangle scissor) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int i = 0; i < xs.length; i++) {
                minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]);
                minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]);
            }
            ScreenRectangle b = new ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY),
                    (int) Math.ceil(maxX - minX), (int) Math.ceil(maxY - minY)).transformMaxBounds(pose);
            return scissor == null ? b : scissor.intersection(b);
        }

        @Override public void buildVertices(VertexConsumer vc) {
            for (int i = 0; i < xs.length; i++) {
                VertexConsumer v = vc.addVertexWith2DPose(pose, xs[i], ys[i]);
                if (us != null) v.setUv(us[i], vs[i]);
                v.setColor(color);
            }
        }

        @Override public RenderPipeline pipeline() { return pipeline; }
        @Override public TextureSetup textureSetup() { return textureSetup; }
        @Override public ScreenRectangle scissorArea() { return scissorArea; }
        @Override public ScreenRectangle bounds() { return bounds; }
    }

    private static void submit(GuiGraphicsExtractor gg, RenderPipeline pipeline, TextureSetup setup,
                               float[] xs, float[] ys, float[] us, float[] vs, int color) {
        if (pipeline == null || xs == null || xs.length == 0) return;
        gg.submitGuiElementRenderState(new ShapeState(pipeline, setup == null ? TextureSetup.noTexture() : setup,
                new Matrix3x2f(gg.pose()), xs, ys, us, vs, color, gg.peekScissorStack()));
    }

    /** 把一张已注册的纹理包装成 TextureSetup；没上传完就返回 null（调用方跳过本次绘制） */
    private static TextureSetup setupOf(Identifier tex) {
        if (tex == null) return null;
        try {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(tex);
            if (t == null) return null;
            var view = t.getTextureView();
            if (view == null) return null;      // 还没上传到 GPU
            return TextureSetup.singleTexture(view, t.getSampler());
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 纹理是否已就绪（替代旧版 getId() >= 0 的判断） */
    public static boolean textureReady(Identifier tex) {
        if (tex == null) return false;
        try {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(tex);
            return t != null && t.getTextureView() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ---------------- 圆角矩形（三角形扇） ----------------

    public static void roundRect(GuiGraphicsExtractor gg, float x, float y, float w, float h, float r, int argb) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) * 0.5f);
        int seg = Math.max(3, (int) (r * 0.6f) + 3);
        int n = 1 + 4 * (seg + 1) + 1;
        float[] xs = new float[n], ys = new float[n];
        int i = 0;
        xs[i] = x + w * 0.5f; ys[i] = y + h * 0.5f; i++;
        i = arcInto(xs, ys, i, x + r, y + r, r, 180, 270, seg);
        i = arcInto(xs, ys, i, x + w - r, y + r, r, 270, 360, seg);
        i = arcInto(xs, ys, i, x + w - r, y + h - r, r, 0, 90, seg);
        i = arcInto(xs, ys, i, x + r, y + h - r, r, 90, 180, seg);
        xs[i] = x; ys[i] = y + r;
        submit(gg, FAN_COLOR, null, xs, ys, null, null, argb);
    }

    private static int arcInto(float[] xs, float[] ys, int at, float cx, float cy, float r,
                               double a0, double a1, int seg) {
        for (int i = 0; i <= seg; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * ((double) i / seg));
            xs[at] = cx + (float) (Math.cos(a) * r);
            ys[at] = cy + (float) (Math.sin(a) * r);
            at++;
        }
        return at;
    }

    public static void roundRectOutline(GuiGraphicsExtractor gg, float x, float y, float w, float h, float r, float thickness, int argb) {
        roundRect(gg, x - thickness, y - thickness, w + thickness * 2, h + thickness * 2, r + thickness, argb);
    }

    /** 三层递减 alpha 的伪阴影 */
    public static void shadow(GuiGraphicsExtractor gg, float x, float y, float w, float h, float r, int rgb, float strength) {
        if (strength <= 0) return;
        roundRect(gg, x - 2f, y - 1f, w + 4f, h + 4f, r + 2f, argb(rgb, 0.10f * strength));
        roundRect(gg, x - 1f, y, w + 2f, h + 2f, r + 1f, argb(rgb, 0.14f * strength));
        roundRect(gg, x, y + 1f, w, h + 1f, r, argb(rgb, 0.18f * strength));
    }

    /** 带背景的进度条 */
    public static void progressBar(GuiGraphicsExtractor gg, float x, float y, float w, float h, float p, int bg, int fg) {
        roundRect(gg, x, y, w, h, h * 0.5f, bg);
        float fw = Math.max(h, w * Anim.clamp(p, 0f, 1f));
        roundRect(gg, x, y, fw, h, h * 0.5f, fg);
    }

    /** 指向目标的三角形箭头，rot=0 指向正上方，正值顺时针旋转 */
    public static void arrow(GuiGraphicsExtractor gg, float cx, float cy, float size, float rot, int argb) {
        float co = (float) Math.cos(rot), si = (float) Math.sin(rot);
        float[] px = {0f, -size * 0.62f, size * 0.62f};
        float[] py = {-size, size * 0.62f, size * 0.62f};
        float[] xs = new float[3], ys = new float[3];
        for (int i = 0; i < 3; i++) {
            xs[i] = cx + px[i] * co - py[i] * si;
            ys[i] = cy + px[i] * si + py[i] * co;
        }
        submit(gg, FAN_COLOR, null, xs, ys, null, null, argb);
    }

    // ---------------- 帧率折线（QUADS） ----------------

    public static void graph(GuiGraphicsExtractor gg, float x, float y, float w, float h, int[] data, int count, int color, float alpha) {
        if (count < 2) return;
        int max = 1;
        for (int i = 0; i < count; i++) max = Math.max(max, data[i]);
        max = Math.max(max, 60);
        int argb = argb(color, alpha);
        float th = 1.3f;
        // 整条折线用一个三角形带画完：每段 4 个顶点 (p0,p1,p2,p3) 正好拼出该段的四边形
        int segs = count - 1;
        float[] xs = new float[4 * segs], ys = new float[4 * segs];
        int k = 0;
        for (int i = 0; i < segs; i++) {
            float x0 = x + w * ((float) i / segs);
            float x1 = x + w * ((float) (i + 1) / segs);
            float y0 = y + h - h * Anim.clamp(data[i] / (float) max, 0f, 1f);
            float y1 = y + h - h * Anim.clamp(data[i + 1] / (float) max, 0f, 1f);
            float dx = x1 - x0, dy = y1 - y0;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.0001f) { dx = 1f; dy = 0f; len = 1f; }
            float nx = -dy / len * th * 0.5f, ny = dx / len * th * 0.5f;
            xs[k] = x0 + nx; ys[k] = y0 + ny; k++;
            xs[k] = x1 + nx; ys[k] = y1 + ny; k++;
            xs[k] = x0 - nx; ys[k] = y0 - ny; k++;
            xs[k] = x1 - nx; ys[k] = y1 - ny; k++;
        }
        submit(gg, STRIP_COLOR, null, xs, ys, null, null, argb);
    }

    // ---------------- 圆环（三角形带） ----------------

    /** 圆环进度：冷却 CD、经验环等。角度从正上方开始顺时针。 */
    public static void ring(GuiGraphicsExtractor gg, float cx, float cy, float r, float thickness, float p, int argb) {
        ringBand(gg, cx, cy, r, thickness, Anim.clamp(p, 0f, 1f), argb);
    }

    /** 圆环的底环（整圈），陪同 ring() 使用 */
    public static void ringTrack(GuiGraphicsExtractor gg, float cx, float cy, float r, float thickness, int argb) {
        ringBand(gg, cx, cy, r, thickness, 1f, argb);
    }

    private static void ringBand(GuiGraphicsExtractor gg, float cx, float cy, float r, float thickness, float sweep, int argb) {
        if (sweep <= 0.0005f || r <= 0.5f) return;
        float ri = Math.max(0.4f, r - thickness);
        int seg = Math.max(4, (int) (48 * sweep) + 2);
        float[] xs = new float[2 * seg + 2], ys = new float[2 * seg + 2];
        int k = 0;
        for (int i = 0; i <= seg; i++) {
            float a = (float) Math.toRadians(-90f + 360f * sweep * ((float) i / seg));
            float c = (float) Math.cos(a), sn = (float) Math.sin(a);
            xs[k] = cx + c * ri; ys[k] = cy + sn * ri; k++;
            xs[k] = cx + c * r;  ys[k] = cy + sn * r;  k++;
        }
        submit(gg, STRIP_COLOR, null, xs, ys, null, null, argb);
    }

    // ---------------- 圆形 / 圆角矩形贴图 ----------------

    /** 圆形贴图：把一张方形贴图裁成圆来画（专辑封面塞进焦点里不会显得方方正正） */
    public static void roundImage(GuiGraphicsExtractor gg, Identifier tex, float cx, float cy, float r, float alpha) {
        if (r <= 0.5f || tex == null) return;
        TextureSetup setup = setupOf(tex);
        if (setup == null) return;
        int seg = Math.max(16, (int) (r * 2.0f));
        float[] xs = new float[seg + 2], ys = new float[seg + 2], us = new float[seg + 2], vs = new float[seg + 2];
        xs[0] = cx; ys[0] = cy; us[0] = 0.5f; vs[0] = 0.5f;
        for (int i = 0; i <= seg; i++) {
            double a = Math.toRadians(-90.0 + 360.0 * i / seg);
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            xs[i + 1] = cx + ca * r; ys[i + 1] = cy + sa * r;
            us[i + 1] = 0.5f + ca * 0.5f; vs[i + 1] = 0.5f + sa * 0.5f;
        }
        submit(gg, FAN_TEX, setup, xs, ys, us, vs, ARGB.white(Anim.clamp(alpha, 0f, 1f)));
    }

    public static void roundRectImage(GuiGraphicsExtractor gg, Identifier tex, float x, float y,
                                      float w, float h, float r, float alpha) {
        roundRectImage(gg, tex, x, y, w, h, r, alpha, 0f, 0f, 1f, 1f);
    }

    /** 指定 UV 区域的版本：按封面真实比例取中心一块，避免方形封面被拉成胶囊的扁宽形状 */
    public static void roundRectImage(GuiGraphicsExtractor gg, Identifier tex, float x, float y, float w, float h,
                                      float r, float alpha, float u0, float v0, float u1, float v1) {
        if (w <= 0f || h <= 0f || tex == null) return;
        TextureSetup setup = setupOf(tex);
        if (setup == null) return;
        r = Math.min(r, Math.min(w, h) * 0.5f);
        int seg = Math.max(3, (int) (r * 0.6f) + 3);
        int n = 1 + 4 * (seg + 1) + 1;
        float[] xs = new float[n], ys = new float[n], us = new float[n], vs = new float[n];
        int i = 0;
        xs[i] = x + w * 0.5f; ys[i] = y + h * 0.5f;
        us[i] = (u0 + u1) * 0.5f; vs[i] = (v0 + v1) * 0.5f; i++;
        i = arcUVInto(xs, ys, us, vs, i, x + r, y + r, r, 180, 270, seg, x, y, w, h, u0, v0, u1, v1);
        i = arcUVInto(xs, ys, us, vs, i, x + w - r, y + r, r, 270, 360, seg, x, y, w, h, u0, v0, u1, v1);
        i = arcUVInto(xs, ys, us, vs, i, x + w - r, y + h - r, r, 0, 90, seg, x, y, w, h, u0, v0, u1, v1);
        i = arcUVInto(xs, ys, us, vs, i, x + r, y + h - r, r, 90, 180, seg, x, y, w, h, u0, v0, u1, v1);
        xs[i] = x; ys[i] = y + r;
        us[i] = u0; vs[i] = v0 + (r / h) * (v1 - v0);
        submit(gg, FAN_TEX, setup, xs, ys, us, vs, ARGB.white(Anim.clamp(alpha, 0f, 1f)));
    }

    private static int arcUVInto(float[] xs, float[] ys, float[] us, float[] vs, int at, float acx, float acy, float r,
                                 double a0, double a1, int seg, float x, float y, float w, float h,
                                 float u0, float v0, float u1, float v1) {
        for (int i = 0; i <= seg; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * ((double) i / seg));
            float vx = acx + (float) (Math.cos(a) * r);
            float vy = acy + (float) (Math.sin(a) * r);
            xs[at] = vx; ys[at] = vy;
            us[at] = u0 + ((vx - x) / w) * (u1 - u0);
            vs[at] = v0 + ((vy - y) / h) * (v1 - v0);
            at++;
        }
        return at;
    }

    // ---------------- 杂项 ----------------

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
