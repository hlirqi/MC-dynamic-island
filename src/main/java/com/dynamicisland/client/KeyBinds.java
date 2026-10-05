package com.dynamicisland.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

public class KeyBinds {
    public static final String CAT = "key.categories.dynamicisland";

    public static final KeyMapping TOGGLE = new KeyMapping("key.dynamicisland.toggle",
            InputConstants.Type.KEYSYM, InputConstants.KEY_O, CAT);
    public static final KeyMapping EXPAND = new KeyMapping("key.dynamicisland.expand",
            InputConstants.Type.KEYSYM, InputConstants.KEY_I, CAT);
    public static final KeyMapping THEME = new KeyMapping("key.dynamicisland.theme",
            InputConstants.Type.KEYSYM, InputConstants.KEY_U, CAT);
    public static final KeyMapping PANEL = new KeyMapping("key.dynamicisland.panel",
            InputConstants.Type.KEYSYM, InputConstants.KEY_K, CAT);
    public static final KeyMapping DEATH_RECALL = new KeyMapping("key.dynamicisland.death",
            InputConstants.Type.KEYSYM, InputConstants.KEY_N, CAT);
    public static final KeyMapping POS = new KeyMapping("key.dynamicisland.pos",
            InputConstants.Type.KEYSYM, InputConstants.KEY_J, CAT);
    public static final KeyMapping FOCUS = new KeyMapping("key.dynamicisland.focus",
            InputConstants.Type.KEYSYM, InputConstants.KEY_B, CAT);
    public static final KeyMapping FOCUS_LEFT = new KeyMapping("key.dynamicisland.focusLeft",
            InputConstants.Type.KEYSYM, InputConstants.KEY_G, CAT);
    public static final KeyMapping FOCUS_RIGHT = new KeyMapping("key.dynamicisland.focusRight",
            InputConstants.Type.KEYSYM, InputConstants.KEY_H, CAT);
    public static final KeyMapping CENTER = new KeyMapping("key.dynamicisland.center",
            InputConstants.Type.KEYSYM, InputConstants.KEY_M, CAT);

    public static void register(RegisterKeyMappingsEvent e) {
        e.register(TOGGLE);
        e.register(EXPAND);
        e.register(THEME);
        e.register(PANEL);
        e.register(DEATH_RECALL);
        e.register(POS);
        e.register(FOCUS);
        e.register(FOCUS_LEFT);
        e.register(FOCUS_RIGHT);
        e.register(CENTER);
    }
}
