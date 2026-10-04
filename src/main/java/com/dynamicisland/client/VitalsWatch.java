package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 生命体征：饥饿过低 / 氧气不足时先在胶囊提醒，随后并入灵动焦点持续查看。
 *
 * 注意：生命值刻意**不**并入焦点，始终留在胶囊的告警里 —— 血量信息太重要，不该被折叠起来。
 */
public class VitalsWatch {

    public static class Vital {
        public boolean active = false;
        public float progress = 0f;
        public String label = "";
        public String sub = "";
        public String center = "";
        public ItemStack icon = ItemStack.EMPTY;
        public int accent = -1;
        float islandTimer = 0f;
        boolean pinned = false;
        boolean prev = false;

        void off() {
            active = false; islandTimer = 0f; prev = false; pinned = false;
            progress = 0f; label = ""; sub = ""; center = "";
            icon = ItemStack.EMPTY; accent = -1;
        }
    }

    public static final Vital hunger = new Vital();
    public static final Vital air = new Vital();

    private static final int HUNGER_LOW = 6;
    private static final float AIR_RATIO = 0.5f;

    public static void tick(float dt, LocalPlayer p) {
        tickHunger(dt, p);
        tickAir(dt, p);
    }

    private static void tickHunger(float dt, LocalPlayer p) {
        if (!Config.focusEnabled || !Config.focusHunger || p == null) { hunger.off(); return; }
        int food;
        try { food = p.getFoodData().getFoodLevel(); } catch (Throwable t) { hunger.off(); return; }

        boolean on = food <= HUNGER_LOW;
        if (on && !hunger.prev) hunger.islandTimer = Config.focusHold; // 刚跌破阈值 → 上台提醒
        hunger.prev = on;
        if (!on) { hunger.off(); return; }

        hunger.active = true;
        hunger.progress = Anim.clamp(food / 20f, 0f, 1f);
        hunger.label = tr("dynamicisland.state.hungry");
        hunger.sub = food + " / 20";
        hunger.center = String.valueOf(food);
        hunger.accent = -1;
        if (hunger.islandTimer > 0f) hunger.islandTimer -= dt;
    }

    private static void tickAir(float dt, LocalPlayer p) {
        if (!Config.focusEnabled || !Config.focusAir || p == null) { air.off(); return; }
        int a, max;
        try { a = p.getAirSupply(); max = p.getMaxAirSupply(); } catch (Throwable t) { air.off(); return; }

        boolean on = a < max * AIR_RATIO;
        if (on && !air.prev) air.islandTimer = Config.focusHold; // 刚缺氧 → 上台提醒
        air.prev = on;
        if (!on) { air.off(); return; }

        air.active = true;
        air.progress = Anim.clamp(a / (float) Math.max(1, max), 0f, 1f);
        air.label = tr("dynamicisland.state.breath");
        air.sub = String.format("%.1fs", a / 20f);
        air.center = String.valueOf(Math.max(0, Math.round(a / 20f)));
        air.accent = 0x40A9FF;
        if (air.islandTimer > 0f) air.islandTimer -= dt;
    }

    public static boolean hungerOnIsland() { return hunger.pinned || hunger.islandTimer > 0f; }
    public static boolean airOnIsland() { return air.pinned || air.islandTimer > 0f; }

    /** 是否被手动钉在胶囊上 */
    public static boolean hungerPinned() { return hunger.pinned; }
    public static boolean airPinned() { return air.pinned; }

    /** 呼出 / 收回：再按一次才退回焦点 */
    public static void bringBackHunger() {
        if (!hunger.active) return;
        hunger.pinned = !hunger.pinned;
        if (hunger.pinned) hunger.islandTimer = 0f;
    }

    public static void bringBackAir() {
        if (!air.active) return;
        air.pinned = !air.pinned;
        if (air.pinned) air.islandTimer = 0f;
    }

    public static void reset() { hunger.off(); air.off(); }

    private static String tr(String key) {
        try { return Component.translatable(key).getString(); } catch (Throwable t) { return key; }
    }
}
