package com.dynamicisland.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 带色块的按钮：左边一块实际颜色的方块，右边是名字。
 * 用于各板块的配色选择，比直接显示 #RRGGBB 直观。
 * color < 0 表示「跟随皮肤」，画成一个斜线方块。
 */
public class ColorButton extends Button {

    public static final int SWATCH = 11;

    private int color = -1;
    private String text = "";

    public ColorButton(int x, int y, int w, int h, OnPress press) {
        super(x, y, w, h, Component.empty(), press, Button.DEFAULT_NARRATION);
    }

    public void set(int color, String text) {
        this.color = color;
        this.text = text == null ? "" : text;
        // 故意不 setMessage：super.renderWidget 会把 message 居中再画一遍，
        // 和这里自己画的色块+文字叠在一起。保持 message 为空即可。
    }

    @Override protected void extractContents(GuiGraphicsExtractor gg, int mx, int my, float pt) {
        extractDefaultSprite(gg);   // 标准按钮底

        int sy = getY() + (height - SWATCH) / 2;
        int sx = getX() + 5;
        RenderUtil.roundRect(gg, sx, sy, SWATCH, SWATCH, 2.5f, 0xFF000000 | 0x333333);

        if (color < 0) {
            // 跟随皮肤：画一个浅色对角斜线表示「自动」
            RenderUtil.roundRect(gg, sx + 1, sy + 1, SWATCH - 2, SWATCH - 2, 2f, 0xFF6E6E78);
        } else {
            RenderUtil.roundRect(gg, sx + 1, sy + 1, SWATCH - 2, SWATCH - 2, 2f, 0xFF000000 | color);
        }

        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int tx = sx + SWATCH + 5;
        int avail = getX() + width - 5 - tx;
        if (avail > 6) {
            String shown = RenderUtil.elide(font, text, avail);
            gg.text(font, shown, tx, getY() + (height - 8) / 2,
                    RenderUtil.argb(0xFFFFFF, active ? 1f : 0.5f), false);
        }
    }
}
