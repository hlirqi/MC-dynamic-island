package com.dynamicisland.client;

import com.dynamicisland.Config;
import org.joml.Matrix3x2fStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 新增功能的绘制层：死亡点回溯 / 物品冷却环 / 药水效果队列 / 经验进度。
 * 全部堆在灵动岛正下方，返回一个累计高度，通知栏据此继续往下排。
 */
public class ExtrasRenderer {

    public static float render(GuiGraphicsExtractor gg, Font font, Theme th, LocalPlayer p, Level lv,
                               float cx, float topY, float scale, float alpha, float partial, long clockMs) {
        Minecraft mc = Minecraft.getInstance();
        Matrix3x2fStack ps = gg.pose();
        ps.pushMatrix();
        ps.translate(cx, topY);
        ps.scale(scale, scale);

        float used = 0f;

        // ---- 死亡点回溯 ----
        if (DeathMark.visible(p, mc.level)) {
            drawDeathMark(gg, ps, font, th, p, lv, alpha, clockMs);
            used += 26f;
        }

        // ---- 物品冷却环 ----
        List<CooldownWheel.Wheel> wheels = CooldownWheel.wheels();
        if (!wheels.isEmpty()) {
            if (used > 0f) used += 4f;
            drawWheels(gg, ps, th, wheels, alpha);
            used += 24f;
        }

        // ---- 药水效果队列 ----
        List<EffectQueue.Row> rows = EffectQueue.rows();
        if (!rows.isEmpty()) {
            if (used > 0f) used += 4f;
            used += drawEffects(gg, ps, font, th, rows, alpha, clockMs);
        }

        // ---- 经验进度 ----
        if (XpWatch.active()) {
            if (used > 0f) used += 4f;
            drawXp(gg, ps, font, th, alpha);
            used += 28f;
        }

        ps.popMatrix();
        return used * scale;
    }

    // ---------- 死亡点回溯 ----------
    private static void drawDeathMark(GuiGraphicsExtractor gg, Matrix3x2fStack ps, Font font, Theme th,
                                      LocalPlayer p, Level lv, float alpha, long clockMs) {
        float w = 132f, h = 22f, x = -w / 2f;
        float a = alpha * 0.95f;
        int bg = RenderUtil.argb(th.bg, Config.bgOpacity() * 0.92f * a);
        if (Config.shadow) RenderUtil.shadow(gg, x, 0f, w, h, h * 0.5f, 0x000000, a * 0.7f);
        RenderUtil.roundRect(gg, x, 0f, w, h, h * 0.5f, bg);

        boolean other = DeathMark.otherDimension(lv);
        int dist = DeathMark.distance(p);

        // 箭头：跨维度时只做呼吸提示，否则按视角旋转
        float pulse = 0.72f + 0.28f * (float) (Math.sin(clockMs / 380.0) * 0.5 + 0.5);
        int arrowCol = other ? RenderUtil.argb(th.textDim, a * pulse)
                : RenderUtil.argb(th.bad, a * Math.max(0.65f, pulse));
        float rot = other ? 0f : DeathMark.arrowRotation(p);
        RenderUtil.arrow(gg, x + 18f, h / 2f, 6.5f, rot, arrowCol);

        String txt = other
                ?  ProgressTracker.I18nS.tr("dynamicisland.extra.deathDim")
                :  ProgressTracker.I18nS.tr("dynamicisland.extra.death", dist);
        String line = RenderUtil.elide(font, txt, w - 46f);
        gg.text(font, line, (int) (x + 32f), (int) (h / 2f - 4f), RenderUtil.argb(th.text, a), false);
    }

    // ---------- 物品冷却环 ----------
    private static void drawWheels(GuiGraphicsExtractor gg, Matrix3x2fStack ps, Theme th, List<CooldownWheel.Wheel> wheels, float alpha) {
        int n = wheels.size();
        float step = 24f;
        float total = n * step;
        float start = -total / 2f + step / 2f;
        for (int i = 0; i < n; i++) {
            CooldownWheel.Wheel wh = wheels.get(i);
            float cxx = start + i * step;
            float r = 10f;
            RenderUtil.roundRect(gg, cxx - r, 2f, r * 2, r * 2, r, RenderUtil.argb(th.bg, Config.bgOpacity() * 0.85f * alpha));
            RenderUtil.ringTrack(gg, cxx, r + 2f, r, 2.5f, RenderUtil.argb(th.textDim, 0.28f * alpha));
            RenderUtil.ring(gg, cxx, r + 2f, r, 2.5f, Anim.clamp(wh.remain, 0f, 1f),
                    RenderUtil.argb(th.accent, 0.95f * alpha));
            // 1.20.1 没有公开的剩余 tick 查询，用圆环本身表达剩余量即可
            gg.item(wh.stack, (int) (cxx - 8f), (int) (r + 2f - 8f));
        }
    }

    // ---------- 药水效果队列 ----------
    private static float drawEffects(GuiGraphicsExtractor gg, Matrix3x2fStack ps, Font font, Theme th,
                                     List<EffectQueue.Row> rows, float alpha, long clockMs) {
        float chipH = 15f, gap = 4f;
        float maxW = 240f;
        float rowsUsed = 0f;
        float x = 0f, y = 0f;
        int lineCount = 1;

        for (int i = 0; i < rows.size(); i++) {
            EffectQueue.Row r = rows.get(i);
            String secs = formatTime(r.secs);
            String label = r.name + " " + secs;
            float w = font.width(label) + 18f;
            if (x + w > maxW && x > 0f) { x = 0f; y += chipH + gap; lineCount++; }

            // 快结束的第一个效果做呼吸闪烁
            boolean urgent = i == 0 && r.secs <= 10;
            float a = alpha;
            if (urgent) a *= 0.55f + 0.45f * (float) (Math.sin(clockMs / 160.0) * 0.5 + 0.5);

            RenderUtil.roundRect(gg, x, y, w, chipH, chipH * 0.5f,
                    RenderUtil.argb(th.bg, Config.bgOpacity() * 0.9f * alpha));
            RenderUtil.roundRect(gg, x + 3f, y + chipH / 2f - 3f, 6f, 6f, 3f,
                    RenderUtil.argb(r.color, Math.max(0.25f, a)));
            gg.text(font, label, (int) (x + 13f), (int) (y + chipH / 2f - 3.5f),
                    RenderUtil.argb(th.text, 0.92f * a), false);
            x += w + gap;
        }
        rowsUsed = lineCount * chipH + (lineCount - 1) * gap;
        return rowsUsed;
    }

    // ---------- 经验进度 ----------
    private static void drawXp(GuiGraphicsExtractor gg, Matrix3x2fStack ps, Font font, Theme th, float alpha) {
        float w = 168f, h = 26f, x = -w / 2f;
        float a = alpha;
        if (Config.shadow) RenderUtil.shadow(gg, x, 0f, w, h, h * 0.5f, 0x000000, a * 0.7f);
        RenderUtil.roundRect(gg, x, 0f, w, h, h * 0.5f, RenderUtil.argb(th.bg, Config.bgOpacity() * 0.92f * a));

        String head = Component.translatable("dynamicisland.extra.xp",
                XpWatch.level, XpWatch.level + 1, XpWatch.missing).getString();
        String headEl = RenderUtil.elide(font, head, w - 24f);
        gg.text(font, headEl, (int) (x + 12f), (int) (7f), RenderUtil.argb(th.text, 0.95f * a), false);

        RenderUtil.progressBar(gg, x + 12f, 17f, w - 24f, 4f, Anim.clamp(XpWatch.progress, 0f, 1f),
                RenderUtil.argb(th.textDim, 0.24f * a), RenderUtil.argb(th.good, 0.95f * a));
    }

    private static String formatTime(int secs) {
        int m = secs / 60, s = secs % 60;
        return secs >= 60 ? m + ":" + String.format("%02d", s) : secs + "s";
    }
}
