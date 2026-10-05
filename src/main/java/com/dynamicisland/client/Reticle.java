package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 准星感知。分两条出口：
 * - 生物 / 容器 → 展开灵动岛显示卡片（开关：reticle）
 * - 方块        → 独立的小弹窗（开关：reticleBlock，位置可在设置里自定义，见 BlockPanel）
 */
public class Reticle {

    public enum Target { NONE, ENTITY, CONTAINER }

    public static Target type = Target.NONE;
    public static String title = "";
    public static String sub = "";
    public static ItemStack icon = ItemStack.EMPTY;
    public static boolean warn = false;
    public static float ratio = 0f;
    public static boolean useBar = false;

    private static float hold = 0f;
    private static float entityHold = 0f;
    private static String lastKey = "";
    private static boolean toolWarn = false;

    public static void tick(float dt, Minecraft mc) {
        hold = Math.max(0f, hold - dt);
        entityHold = Math.max(0f, entityHold - dt);
        BlockPanel.tick(dt);

        LocalPlayer p = mc.player;
        Level lv = mc.level;
        if (p == null || lv == null) return;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) return;

        if (hit instanceof EntityHitResult ehr) {
            if (Config.modReticle) updateEntity(p, ehr.getEntity());
            return;
        }

        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (p.position().distanceTo(Vec3.atCenterOf(pos)) > 6.5D) return;

        BlockState st = lv.getBlockState(pos);
        if (st == null || st.isAir()) return;

        // 容器走灵动岛卡片，普通方块走独立弹窗
        BlockEntity be = lv.getBlockEntity(pos);
        if (be instanceof Container && Config.modReticle) {
            updateContainer(be, st, pos);
            return;
        }
        if (Config.modReticleBlock) updateBlockPanel(p, st, lv, pos);
    }

    // ---------- 生物 ----------
    private static void updateEntity(LocalPlayer p, Entity e) {
        if (e == null || e.isInvisibleTo(p)) return;
        String key = "e" + e.getId();
        if (!key.equals(lastKey)) { lastKey = key; hold = 2.4f; }
        entityHold = 0.35f; // 盯着就一直显示，移开 0.35s 后收起

        title = e.getName().getString();
        double dist = p.distanceTo(e);
        type = Target.ENTITY;
        icon = ItemStack.EMPTY;
        warn = false;

        if (e instanceof LivingEntity le) {
            float max = Math.max(1f, le.getMaxHealth());
            ratio = Anim.clamp(le.getHealth() / max, 0f, 1f);
            useBar = true;
            String hp = Component.translatable("dynamicisland.state.hp",
                    Math.round(le.getHealth() * 10) / 10f, Math.round(max * 10) / 10f).getString();
            sub = String.format("%.1fm", dist) + "  ·  " + hp;
            warn = ratio <= 0.3f;
        } else {
            useBar = false;
            ratio = 0f;
            sub = String.format("%.1fm", dist);
        }
    }

    // ---------- 容器 ----------
    private static void updateContainer(BlockEntity be, BlockState st, BlockPos pos) {
        Container box = (Container) be;
        int size = box.getContainerSize();
        int used = 0;
        for (int i = 0; i < size; i++) if (!box.getItem(i).isEmpty()) used++;

        String key = "c" + pos;
        if (!key.equals(lastKey)) { lastKey = key; hold = 2.6f; }
        entityHold = 0.3f;

        type = Target.CONTAINER;
        title = st.getBlock().getName().getString();
        sub = Component.translatable("dynamicisland.reticle.container", used, size).getString();
        icon = ItemStack.EMPTY;
        ratio = size <= 0 ? 0f : used / (float) size;
        useBar = true;
        warn = ratio >= 1f;
    }

    // ---------- 方块 → 独立弹窗 ----------
    private static void updateBlockPanel(LocalPlayer p, BlockState st, Level lv, BlockPos pos) {
        float hardness;
        try { hardness = st.getDestroySpeed(lv, pos); } catch (Throwable ignored) { hardness = -1f; }

        String hardText = hardness < 0
                ? ProgressTracker.I18nS.tr("dynamicisland.tool.unbreakable")
                : Component.translatable("dynamicisland.tool.hardness", hardness).getString();

        toolWarn = false;
        String toolText = toolHint(p, st, p.getMainHandItem(), lv, pos);

        BlockPanel.show(st, st.getBlock().getName().getString(), hardText, toolText, toolWarn,
                stackOf(st), pos.getX(), pos.getY(), pos.getZ());
    }

    private static ItemStack stackOf(BlockState st) {
        try {
            net.minecraft.world.item.Item it = st.getBlock().asItem();
            return it == null ? ItemStack.EMPTY : new ItemStack(it);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** 返回"需要铁镐"这类提示；当前工具够用或方块不要求工具时返回空串 */
    private static String toolHint(LocalPlayer p, BlockState st, ItemStack held, Level lv, BlockPos pos) {
        try {
            if (!st.requiresCorrectToolForDrops()) return "";
            boolean ok;
            try { ok = held.isCorrectToolForDrops(st); }
            catch (Throwable t) { ok = p.hasCorrectToolForDrops(st, lv, pos); }
            if (ok) return Component.translatable("dynamicisland.tool.ok").getString();

            String tl = toolName(st);
            String tier = tierName(st);
            toolWarn = true;
            return Component.translatable("dynamicisland.tool.need", tier.isEmpty() ? tl : tier + tl).getString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String toolName(BlockState st) {
        try {
            if (st.is(BlockTags.MINEABLE_WITH_PICKAXE)) return ProgressTracker.I18nS.tr("dynamicisland.tool.pickaxe");
            if (st.is(BlockTags.MINEABLE_WITH_AXE)) return ProgressTracker.I18nS.tr("dynamicisland.tool.axe");
            if (st.is(BlockTags.MINEABLE_WITH_SHOVEL)) return ProgressTracker.I18nS.tr("dynamicisland.tool.shovel");
            if (st.is(BlockTags.MINEABLE_WITH_HOE)) return ProgressTracker.I18nS.tr("dynamicisland.tool.hoe");
        } catch (Throwable ignored) { }
        return "";
    }

    private static String tierName(BlockState st) {
        try {
            if (st.is(BlockTags.NEEDS_DIAMOND_TOOL)) return ProgressTracker.I18nS.tr("dynamicisland.tier.diamond");
            if (st.is(BlockTags.NEEDS_IRON_TOOL)) return ProgressTracker.I18nS.tr("dynamicisland.tier.iron");
            if (st.is(BlockTags.NEEDS_STONE_TOOL)) return ProgressTracker.I18nS.tr("dynamicisland.tier.stone");
        } catch (Throwable ignored) { }
        return "";
    }

    public static boolean active() { return Config.modReticle && type != Target.NONE && hold > 0f && entityHold > 0f; }

    public static IslandStatus status() {
        if (!active()) return null;
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.RETICLE;
        s.icon = icon;
        s.title = title;
        s.sub = sub;
        s.progress = useBar ? ratio : 0f;
        s.showBar = useBar;
        s.accent = warn ? Config.theme.bad : Config.theme.accent;
        return s;
    }

}
