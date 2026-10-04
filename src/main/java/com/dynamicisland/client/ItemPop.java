package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * 物品弹出卡片，覆盖两种场景：
 * 1) 拾取物品：显示图标 + 名字 + 数量，同一物品在显示期内继续拾取会累加数量并续期
 * 2) 切换快捷栏：滚轮换物品时短暂显示物品名与耐久
 *
 * 拾取的判定基于「背包里该物品的持有总量增加」——这样在背包里挪动格子不会误触发。
 */
public class ItemPop {

    private static ItemStack stack = ItemStack.EMPTY;
    private static String name = "";
    private static int count = 0;
    private static float hold = 0f;
    private static boolean pickupMode = false;

    // 上一帧背包里每种物品的总数，用于判定"真的捡到了东西"
    private static final Map<Item, Integer> totals = new HashMap<>();
    private static boolean totalsReady = false;
    private static int lastSelected = -1;

    public static void tick(float dt, LocalPlayer p) {
        hold = Math.max(0f, hold - dt);
        if (p == null) return;

        scanPickup(p);
        scanHotbar(p);
    }

    // ---------- 拾取 ----------
    private static void scanPickup(LocalPlayer p) {
        if (!Config.modPickup) { totalsReady = false; return; }

        Map<Item, Integer> now = new HashMap<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            now.merge(s.getItem(), s.getCount(), Integer::sum);
        }

        if (!totalsReady) { totals.clear(); totals.putAll(now); totalsReady = true; return; }

        for (Map.Entry<Item, Integer> en : now.entrySet()) {
            int before = totals.getOrDefault(en.getKey(), 0);
            int gained = en.getValue() - before;
            if (gained > 0) {
                // 找到该物品的一个代表堆栈（尽量取数量最多的，图标才准确）
                ItemStack picked = ItemStack.EMPTY;
                int best = -1;
                for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                    ItemStack s = p.getInventory().getItem(i);
                    if (!s.isEmpty() && s.getItem() == en.getKey() && s.getCount() > best) { best = s.getCount(); picked = s; }
                }
                onPickup(picked, gained);
            }
        }
        totals.clear();
        totals.putAll(now);
    }

    private static void onPickup(ItemStack picked, int gained) {
        if (picked.isEmpty()) return;
        boolean same = pickupMode && hold > 0f && stack.getItem() == picked.getItem();
        stack = picked.copy();
        name = stack.getHoverName().getString();
        count = same ? count + gained : gained;
        hold = Config.pickupTime;
        pickupMode = true;
    }

    // ---------- 快捷栏切换 ----------
    private static void scanHotbar(LocalPlayer p) {
        int sel = p.getInventory().selected;
        boolean changed = lastSelected >= 0 && sel != lastSelected;
        lastSelected = sel;
        if (!changed || !Config.modHotbar) return;

        ItemStack s = p.getInventory().getSelected();
        if (s.isEmpty() || s.getItem() == Items.AIR) return;
        // 拾取卡片正在显示时不打断它
        if (pickupMode && hold > 0f) return;

        stack = s.copy();
        name = stack.getHoverName().getString();
        count = s.getCount();
        hold = Config.hotbarTime;
        pickupMode = false;
    }

    public static boolean active() { return hold > 0f && !stack.isEmpty(); }

    public static IslandStatus status() {
        if (!active()) return null;
        IslandStatus s = new IslandStatus();
        s.kind = pickupMode ? IslandStatus.Kind.PICKUP : IslandStatus.Kind.HOTBAR;
        s.icon = stack;
        s.title = name;

        if (pickupMode) {
            s.sub = count > 1 ? "×" + count : "×1";
            float cap = Math.max(1f, stack.getMaxStackSize());
            s.progress = Anim.clamp(count / cap, 0.08f, 1f);
            s.accent = Config.theme.good;
            s.showBar = true;
        } else {
            int max = stack.getMaxDamage();
            if (max > 0) {
                int left = max - stack.getDamageValue();
                s.sub = left + " / " + max;
                s.progress = Anim.clamp(left / (float) max, 0f, 1f);
                s.accent = (left / (float) max) < 0.15f ? Config.theme.bad : Config.theme.accent;
            } else {
                s.sub = Component.translatable("dynamicisland.state.hotbar.count", count).getString();
                s.progress = 0f;
                s.accent = Config.theme.accent;
            }
            s.showBar = max > 0;
        }
        return s;
    }

    public static void reset() {
        stack = ItemStack.EMPTY; count = 0; hold = 0f; pickupMode = false;
        totals.clear(); totalsReady = false; lastSelected = -1;
    }
}
