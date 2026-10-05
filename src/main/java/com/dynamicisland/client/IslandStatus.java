package com.dynamicisland.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 某一帧胶囊要呈现的内容快照 */
public class IslandStatus {
    public enum Kind { IDLE, MINING, USE, PORTAL, SLEEP, DEAD, FLY, ALERT, COOLDOWN,
        BUILD, RETICLE, PICKUP, HOTBAR, MUSIC, CLOCK, DEPTH, FOCUS }

    public Kind kind = Kind.IDLE;
    public String title = "";
    public String sub = "";
    public float progress = 0f;
    public ItemStack icon = ItemStack.EMPTY;
    public ResourceLocation cover = null;  // 专辑封面（Tritium 联动用，优先于 icon）
    public int accent = -1;      // -1 表示用主题强调色
    /** >=0 时覆盖卡片里「时间」那行文字的颜色（自定义定时器用事件自己的颜色） */
    public int textColor = -1;
    public boolean pulse = false; // 心跳/闪烁动画
    public boolean valid = true;
    public boolean showBar = true; // 准星看方块这类没有进度的卡片可以关掉进度条

    public static IslandStatus idle(String title, String sub) {
        IslandStatus s = new IslandStatus();
        s.kind = Kind.IDLE;
        s.title = title;
        s.sub = sub;
        return s;
    }

    public IslandStatus withAccent(int c) { this.accent = c; return this; }
    public IslandStatus pulse() { this.pulse = true; return this; }
}
