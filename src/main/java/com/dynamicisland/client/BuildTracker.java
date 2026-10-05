package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 放置方块时记录剩余数量。
 * 判定方式：主手是方块类物品，且堆叠数在两帧之间恰好少 1 —— 这就是一次放置。
 */
public class BuildTracker {

    private static Item lastItem = null;
    private static int lastCount = 0;
    private static float hold = 0f;

    private static String blockName = "";
    private static ItemStack shownStack = ItemStack.EMPTY;
    private static int remaining = 0;
    private static boolean low = false;

    public static void tick(float dt, LocalPlayer p) {
        hold = Math.max(0f, hold - dt);
        if (p == null) return;

        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) { lastItem = null; lastCount = 0; return; }
        if (!(held.getItem() instanceof BlockItem)) { lastItem = held.getItem(); lastCount = held.getCount(); return; }

        Item item = held.getItem();
        int count = held.getCount();
        boolean placed = (lastItem == item && count == lastCount - 1);
        lastItem = item;
        lastCount = count;
        if (!placed) return;

        // ---- 一次放置 ----
        blockName = held.getHoverName().getString();
        shownStack = held.copy();
        remaining = count;
        hold = 2.8f;
        low = remaining <= Config.buildWarn;

        if (!Config.noticeBlock) return;

        if (low && remaining > 0) {
            String sub = Component.translatable("dynamicisland.notice.blocklow.sub", remaining).getString();
            String msg = remaining <= 3
                    ? Component.translatable("dynamicisland.notice.blockcrit").getString()
                    : Component.translatable("dynamicisland.notice.blocklow").getString();
            Notifier.push(msg, sub, shownStack,
                    remaining <= 3 ? Config.theme.bad : Config.theme.warn,
                    "blk:" + item, 6);
        } else if (remaining == 0) {
            Notifier.push(Component.translatable("dynamicisland.notice.blockout").getString(),
                    blockName, shownStack, Config.theme.bad, "blkout:" + item, 8);
        }
    }

    public static boolean active() { return Config.modBuild && hold > 0f; }

    /** 生成一张临时卡片，交给胶囊展开显示 */
    public static IslandStatus status() {
        if (!active()) return null;
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.BUILD;
        s.icon = shownStack;
        s.title = blockName;
        s.sub = Component.translatable("dynamicisland.state.build", remaining).getString();
        s.progress = Anim.clamp(remaining / 64f, 0.02f, 1f);
        s.accent = low ? Config.theme.warn : Config.theme.good;
        return s;
    }

    public static void reset() {
        lastItem = null; lastCount = 0; hold = 0f; remaining = 0; low = false;
    }
}
