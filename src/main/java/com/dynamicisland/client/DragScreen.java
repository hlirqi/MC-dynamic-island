package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 透明拖动层：按住胶囊拖到任意位置，ESC / 回车保存 */
public class DragScreen extends Screen {

    private boolean dragging = false;
    private final float w = 200f, h = 36f;

    protected DragScreen() { super(Component.translatable("dynamicisland.drag.hint")); }

    private float cx() { return width / 2f + Config.offsetX; }
    private float cy() { return Config.offsetY; }

    @Override public void extractRenderState(GuiGraphicsExtractor gg, int mx, int my, float pt) {
        Theme th = Config.theme;
        float x = cx() - w / 2f, y = cy();
        RenderUtil.shadow(gg, x, y, w, h, h / 2f, 0x000000, 1f);
        RenderUtil.roundRect(gg, x, y, w, h, h / 2f, RenderUtil.argb(th.bg, Config.bgOpacity()));
        gg.centeredText(font, "FPS " + Metrics.fps + "   ▮▮▮░ " + (int) (Metrics.memRatio * 100) + "%",
                (int) cx(), (int) (y + h / 2f - 4f), RenderUtil.argb(th.text, 1f));
        gg.centeredText(font, Component.translatable("dynamicisland.drag.hint").getString(),
                width / 2, height - 24, 0xDDDDDD);
        gg.centeredText(font, "偏移 X " + Config.offsetX + "  Y " + Config.offsetY,
                width / 2, height - 12, 0x999999);
    }

    /** 透明：不画任何背景 */
    @Override public void extractBackground(GuiGraphicsExtractor gg, int mx, int my, float pt) { }

    private boolean inside(double mx, double my) {
        return mx >= cx() - w / 2f && mx <= cx() + w / 2f && my >= cy() && my <= cy() + h;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        dragging = inside(event.x(), event.y());
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            Config.offsetX = (int) Anim.clamp((float) (event.x() - width / 2f), -width / 2f, width / 2f);
            Config.offsetY = (int) Anim.clamp((float) (event.y() - h / 2f), 0f, height - h - 10f);
        }
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        Config.save();
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.input();
        if (key == 256 || key == 257) { Config.save(); onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override public void onClose() { Config.save(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}
