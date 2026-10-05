package com.dynamicisland.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * 26.x 起按键分类不再是字符串 key，而是一个 {@link KeyMapping.Category} 对象，
 * 且必须由 RegisterKeyMappingsEvent#registerCategory 注册，
 * 所以按键实例也只能等到注册事件里再建。
 */
public class KeyBinds {

    public static final String CAT_KEY = "key.categories.dynamicisland";
    private static KeyMapping.Category CAT = null;

    public static KeyMapping TOGGLE = null;
    public static KeyMapping EXPAND = null;
    public static KeyMapping THEME = null;
    public static KeyMapping PANEL = null;
    public static KeyMapping DEATH_RECALL = null;
    public static KeyMapping POS = null;
    public static KeyMapping FOCUS = null;
    public static KeyMapping FOCUS_LEFT = null;
    public static KeyMapping FOCUS_RIGHT = null;

    public static void register(RegisterKeyMappingsEvent e) {
        CAT = new KeyMapping.Category(Identifier.fromNamespaceAndPath("dynamicisland", "main"));
        e.registerCategory(CAT);

        TOGGLE = key(e, "key.dynamicisland.toggle", InputConstants.KEY_O);
        EXPAND = key(e, "key.dynamicisland.expand", InputConstants.KEY_I);
        THEME = key(e, "key.dynamicisland.theme", InputConstants.KEY_U);
        PANEL = key(e, "key.dynamicisland.panel", InputConstants.KEY_K);
        DEATH_RECALL = key(e, "key.dynamicisland.death", InputConstants.KEY_N);
        POS = key(e, "key.dynamicisland.pos", InputConstants.KEY_J);
        FOCUS = key(e, "key.dynamicisland.focus", InputConstants.KEY_B);
        FOCUS_LEFT = key(e, "key.dynamicisland.focusLeft", InputConstants.KEY_G);
        FOCUS_RIGHT = key(e, "key.dynamicisland.focusRight", InputConstants.KEY_H);
    }

    private static KeyMapping key(RegisterKeyMappingsEvent e, String name, int code) {
        KeyMapping km = new KeyMapping(name, InputConstants.Type.KEYSYM, code, CAT);
        e.register(km);
        return km;
    }

}
