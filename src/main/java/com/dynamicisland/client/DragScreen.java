package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 透明拖动层：按住胶囊拖到任意位置，ESC / 回车保存 */
public class DragScreen extends Screen {

    private boolean dragging = false;
    private final float w = 200f, h = 36f;

    protected DragScreen() { super(Component.translatable("dynamicisland.drag.hint")); }

    private float cx() { return width / 2f + Config.offsetX; }
    private float cy() { return Config.offsetY; }

    @Override public void render(GuiGraphics gg, int mx, int my, float pt) {
        Theme th = Config.theme;
        float x = cx() - w / 2f, y = cy();
        Minecraft mc = Minecraft.getInstance();
        RenderUtil.shadow(gg.pose(), x, y, w, h, h / 2f, 0x000000, 1f);
        RenderUtil.roundRect(gg.pose(), x, y, w, h, h / 2f, RenderUtil.argb(th.bg, Config.bgOpacity()));
        gg.drawCenteredString(font, "FPS " + Metrics.fps + "   ▮▮▮░ " + (int) (Metrics.memRatio * 100) + "%",
                (int) cx(), (int) (y + h / 2f - 4f), RenderUtil.argb(th.text, 1f));
        gg.drawCenteredString(font, Component.translatable("dynamicisland.drag.hint").getString(),
                width / 2, height - 24, 0xDDDDDD);
        gg.drawCenteredString(font, "偏移 X " + Config.offsetX + "  Y " + Config.offsetY,
                width / 2, height - 12, 0x999999);
    }

    @Override public void renderBackground(GuiGraphics gg) { /* 透明，不画背景 */ }

    private boolean inside(double mx, double my) {
        return mx >= cx() - w / 2f && mx <= cx() + w / 2f && my >= cy() && my <= cy() + h;
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        dragging = inside(mx, my);
        return true;
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragging) {
            Config.offsetX = (int) Anim.clamp((float) (mx - width / 2f), -width / 2f, width / 2f);
            Config.offsetY = (int) Anim.clamp((float) (my - h / 2f), 0f, height - h - 10f);
        }
        return true;
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        dragging = false;
        Config.save();
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 || key == 257) { Config.save(); onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public void onClose() { Config.save(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}
