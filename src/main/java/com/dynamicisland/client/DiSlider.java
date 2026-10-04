package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** 轻量滑块：拖动 / 点击均可改值 */
public class DiSlider extends AbstractButton {
    public interface Sink { void accept(float v); }

    private final String label;
    public float value, min, max;
    public boolean intMode = false;
    private final Sink sink;

    public DiSlider(int x, int y, int w, int h, String label, float value, float min, float max, boolean intMode, Sink sink) {
        super(x, y, w, h, Component.literal(label));
        this.label = label;
        this.value = value; this.min = min; this.max = max;
        this.intMode = intMode; this.sink = sink;
    }

    private void apply(double mouseX) {
        float t = Anim.clamp((float) ((mouseX - getX()) / getWidth()), 0f, 1f);
        value = min + (max - min) * t;
        if (intMode) value = Math.round(value);
        sink.accept(value);
    }

    @Override public void onClick(double mx, double my) { apply(mx); }

    /** 滑块没有“按下即触发”的语义，值的变化由点击/拖动实时写入 */
    @Override public void onPress() { }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        apply(mx); return true;
    }

    @Override protected void renderWidget(GuiGraphics gg, int mx, int my, float pt) {
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        Theme th = Config.theme;
        float t = (value - min) / Math.max(0.0001f, max - min);
        RenderUtil.roundRect(gg.pose(), getX(), getY() + height / 2f - 3f, width, 6f, 3f,
                RenderUtil.argb(th.textDim, 0.25f));
        RenderUtil.roundRect(gg.pose(), getX(), getY() + height / 2f - 3f, Math.max(6f, width * t), 6f, 3f,
                RenderUtil.argb(th.accent, 0.95f));
        RenderUtil.roundRect(gg.pose(), getX() + width * t - 2.5f, getY() + height / 2f - 5.5f, 5f, 11f, 2.5f,
                RenderUtil.argb(th.text, isHoveredOrFocused() ? 1f : 0.85f));
        String v = intMode ? String.valueOf((int) value) : String.format("%.2f", value);
        gg.drawString(font, label, getX(), getY() + 1, RenderUtil.argb(th.textDim, 0.9f), false);
        gg.drawString(font, v, getX() + width - font.width(v), getY() + 1, RenderUtil.argb(th.text, 0.95f), false);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.literal(label));
    }
}
