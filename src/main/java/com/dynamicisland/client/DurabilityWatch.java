package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 装备耐久告急：剩余耐久低于阈值时先在胶囊提醒，随后并入灵动焦点，
 * 之后用圆环持续查看剩余耐久比例。
 *
 * 扫描范围：主手 / 副手 / 四件护甲。多件同时告急时取剩余最少的那件。
 */
public class DurabilityWatch {

    public static ItemStack icon = ItemStack.EMPTY;
    public static String label = "";
    public static String sub = "";
    public static float progress = 0f;
    public static boolean active = false;

    private static float islandTimer = 0f;
    private static boolean pinned = false;
    private static ItemStack tracked = ItemStack.EMPTY;

    public static void tick(float dt, LocalPlayer p) {
        if (!Config.focusEnabled || !Config.focusDurability || p == null) {
            reset();
            return;
        }

        ItemStack worst = null;
        int worstLeft = Integer.MAX_VALUE;
        try {
            for (ItemStack s : equipped(p)) {
                if (s == null || s.isEmpty() || !s.isDamageableItem()) continue;
                int left = s.getMaxDamage() - s.getDamageValue();
                if (left > Config.durabilityMax) continue;   // 还够用，不管
                if (left < worstLeft) { worstLeft = left; worst = s; }
            }
        } catch (Throwable ignored) { }

        if (worst == null) { reset(); return; }

        // 换了一件新的告急装备就重新上台提醒一次
        if (!ItemStack.isSameItem(worst, tracked)) {
            tracked = worst.copy();
            islandTimer = Config.focusHold;
            pinned = false;
        }

        active = true;
        icon = worst;
        label = worst.getHoverName().getString();
        int max = worst.getMaxDamage();
        int left = max - worst.getDamageValue();
        progress = Anim.clamp(left / (float) Math.max(1, max), 0f, 1f);
        sub = left + " / " + max;

        if (islandTimer > 0f) islandTimer -= dt;
    }

    private static List<ItemStack> equipped(LocalPlayer p) {
        List<ItemStack> l = new ArrayList<>();
        l.add(p.getMainHandItem());
        l.add(p.getOffhandItem());
        for (EquipmentSlot slot : ARMOR_SLOTS) l.add(p.getItemBySlot(slot));
        return l;
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static boolean onIsland() { return pinned || islandTimer > 0f; }

    /** 是否被手动钉在胶囊上 */
    public static boolean pinned() { return pinned; }

    /** 呼出 / 收回：再按一次才退回焦点 */
    public static void bringBack() {
        if (!active) return;
        pinned = !pinned;
        if (pinned) islandTimer = 0f;
    }

    public static void reset() {
        active = false;
        islandTimer = 0f;
        pinned = false;
        tracked = ItemStack.EMPTY;
        icon = ItemStack.EMPTY;
        label = "";
        sub = "";
        progress = 0f;
    }
}
