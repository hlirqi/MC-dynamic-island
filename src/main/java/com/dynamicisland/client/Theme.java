package com.dynamicisland.client;

/** 四套皮肤。颜色均为 0xRRGGBB，渲染时再乘上不透明度。 */
public enum Theme {
    BLACK_GLASS("blackglass", 0x0B0B0E, 0x2A2A31, 0xFFFFFF, 0x9A9AA6, 0x5AC8FA, 0x30D158, 0xFFD60A, 0xFF453A, true),
    LIGHT_FROST("lightfrost", 0xF2F2F7, 0xC9C9D2, 0x1C1C1E, 0x6B6B73, 0x0A84FF, 0x34A853, 0xF5A623, 0xE8453C, false),
    VANILLA("vanilla", 0x1B0D0D, 0x4A2C2C, 0xF0F0F0, 0xA89090, 0xFFAA00, 0x55FF55, 0xFFFF55, 0xFF5555, true),
    NEON("neon", 0x12071F, 0xB14CFF, 0xEAFBFF, 0x8FD8FF, 0x00F0FF, 0x39FF88, 0xFFE14D, 0xFF2E88, true);

    public final String id;
    public final int bg, border, text, textDim, accent, good, warn, bad;
    public final boolean dark;

    Theme(String id, int bg, int border, int text, int textDim, int accent, int good, int warn, int bad, boolean dark) {
        this.id = id; this.bg = bg; this.border = border; this.text = text; this.textDim = textDim;
        this.accent = accent; this.good = good; this.warn = warn; this.bad = bad; this.dark = dark;
    }

    public static Theme byId(String id) {
        for (Theme t : values()) if (t.id.equalsIgnoreCase(id)) return t;
        return BLACK_GLASS;
    }

    public static Theme next(Theme t) {
        Theme[] v = values();
        return v[(t.ordinal() + 1) % v.length];
    }

    public String translationKey() { return "dynamicisland.theme." + id; }
}
