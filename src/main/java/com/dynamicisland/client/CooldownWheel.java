package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 物品冷却环。
 *
 * 只画「真的在冷却」的物品：必须先用 ItemCooldowns#isOnCooldown 过滤，
 * 因为 getCooldownPercent 对没有冷却记录的物品同样返回 0.0（字节码里 else 分支 fconst_0 直接返回），
 * 若只按数值判断，整排快捷栏都会被当成"正在冷却"画成满环。
 * 在此基础上再叠一层白名单：只有末影珍珠、金苹果、药水这类本就带冷却机制的物品会出现。
 */
public class CooldownWheel {

    public static class Wheel {
        public ItemStack stack;
        public float remain; // 1 = 刚用完，0 = 冷却结束
        public Wheel(ItemStack s, float r) { stack = s; remain = r; }
    }

    private static final List<Wheel> wheels = new ArrayList<>();

    /** 已知带冷却机制的物品；模组物品若确实写入过冷却，会被下面的动态条件放行 */
    private static final Set<Item> KNOWN_COOLDOWN = new HashSet<>(Arrays.asList(
            Items.ENDER_PEARL,
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.CHORUS_FRUIT,
            Items.POTION,
            Items.SPLASH_POTION,
            Items.LINGERING_POTION,
            Items.TIPPED_ARROW,
            Items.FIREWORK_ROCKET,
            Items.GOAT_HORN,
            Items.SHIELD,
            Items.MILK_BUCKET,
            Items.HONEY_BOTTLE
    ));

    public static List<Wheel> update(LocalPlayer p, float partial) {
        wheels.clear();
        if (!Config.modCooldown || p == null) return wheels;
        try {
            ItemCooldowns cd = p.getCooldowns();
            for (int i = 0; i < 9 && wheels.size() < 4; i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (acceptable(cd, s)) {
                    wheels.add(new Wheel(s, Anim.clamp(cd.getCooldownPercent(s, partial), 0f, 1f)));
                }
            }
            ItemStack off = p.getOffhandItem();
            if (wheels.size() < 4 && acceptable(cd, off)) {
                wheels.add(new Wheel(off, Anim.clamp(cd.getCooldownPercent(off, partial), 0f, 1f)));
            }
        } catch (Throwable ignored) { }
        return wheels;
    }

    private static boolean acceptable(ItemCooldowns cd, ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        Item it = s.getItem();
        if (!cd.isOnCooldown(s)) return false;        // 关键：没在冷却就不画
        if (KNOWN_COOLDOWN.contains(it)) return true;
        return cd.getCooldownPercent(s, 0f) > 0.0005f; // 其它物品确有冷却记录才放行
    }

    public static List<Wheel> wheels() { return wheels; }
    public static boolean active() { return Config.modCooldown && !wheels.isEmpty(); }
}
