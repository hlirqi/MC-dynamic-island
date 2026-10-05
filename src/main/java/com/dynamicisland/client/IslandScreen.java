package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * 灵动岛设置面板。
 *
 * 按「显示区域」分成四个标签页：
 * 灵动岛主框（胶囊本体 + 外观与布局）/ 信息条 / 通知 / 方块信息显示。
 * 每个页面内部再用分区标题分组，并支持滚轮滚动 —— 后续继续加功能不用再担心塞不下。
 */
public class IslandScreen extends Screen {

    private static final int TAB_ISLAND = 0;
    private static final int TAB_INFOBAR = 1;
    private static final int TAB_BLOCK = 2;
    private static final int TAB_NOTICE = 3;
    private static final int TAB_FOCUS = 4;
    private static final int TAB_TRITIUM = 5;

    private static final String[] TAB_KEY = {
            "dynamicisland.tab.island", "dynamicisland.tab.infobar", "dynamicisland.tab.block",
            "dynamicisland.tab.notice", "dynamicisland.tab.focus", "dynamicisland.tab.tritium"
    };

    private static final int ROW_H = 24;
    private static final int BTN_H = 20;

    /** 调色板：首个 -1 表示跟随皮肤，其后为常用色，点击循环切换 */
    private static final int[] PALETTE = {
            -1, 0xFFFFFF, 0xFF453A, 0xFF9F0A, 0xFFD60A, 0x30D158, 0x40C8E0,
            0x0A84FF, 0x5E5CE6, 0xBF5AF2, 0xFF375F, 0xA2845E, 0x8E8E93
    };

    private final Screen parent;
    private int tab = TAB_ISLAND;

    private int scroll = 0;
    private int contentHeight = 0;
    private int cursor = 0;
    private int contentX = 0, contentW = 300;
    private int viewTop = 54, viewBottom = 300;

    private final List<Line> lines = new ArrayList<>();
    private final List<AbstractWidget> tabButtons = new ArrayList<>();

    private static class Line {
        int y; String text; boolean section;
        Line(int y, String text, boolean section) { this.y = y; this.text = text; this.section = section; }
    }

    public IslandScreen(Screen parent) {
        super(Component.translatable("dynamicisland.screen.title"));
        this.parent = parent;
    }

    // ================= 生命周期 =================

    @Override protected void init() {
        Config.bake();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        tabButtons.clear();
        lines.clear();
        cursor = 0;

        contentW = Math.min(324, Math.max(236, width - 24));
        contentX = (width - contentW) / 2;
        viewTop = 54;
        viewBottom = Math.max(viewTop + 48, height - 24);

        addTabs();
        switch (tab) {
            case TAB_INFOBAR: buildInfoBar(); break;
            case TAB_BLOCK:   buildBlock();   break;
            case TAB_NOTICE:  buildNotice();  break;
            case TAB_FOCUS:   buildFocus();   break;
            case TAB_TRITIUM: buildTritium(); break;
            default:          buildIsland();  break;
        }
        contentHeight = cursor;

        clampScroll();
        shift(-scroll);
        refreshInteractivity();
    }

    // ================= 页面：灵动岛主框（胶囊本体 + 外观布局） =================

    private void buildIsland() {
        section("dynamicisland.section.general");
        toggle(col0(), row(), "dynamicisland.opt.enabled", () -> Config.enabled, v -> {
            Config.enabled = v; IslandRenderer.INSTANCE.visible = v;
        });
        toggle(col1(), topAt(), "dynamicisland.opt.hideF3", () -> Config.hideOnDebug, v -> Config.hideOnDebug = v);
        colorCycle(col0(), row(), "dynamicisland.color.island", () -> Config.colorIsland,
                v -> Config.colorIsland = v);

        section("dynamicisland.section.theme");
        cycleTheme(col0(), row(), colW());
        toggle(col1(), topAt(), "dynamicisland.opt.shadow", () -> Config.shadow, v -> Config.shadow = v);
        slider(col0(), row(), colW(), "dynamicisland.opt.scale", Config.scale, 0.5f, 2.0f, false, v -> Config.scale = v);
        slider(col1(), topAt(), colW(), "dynamicisland.opt.opacity", Config.opacity, 0f, 1f, false, v -> Config.opacity = v);
        slider(col0(), row(), colW(), "dynamicisland.opt.lightOpacity", Config.lightOpacity, 0f, 1f, false,
                v -> Config.lightOpacity = v);
        slider(col1(), topAt(), colW(), "dynamicisland.opt.animSpeed", Config.animSpeed, 0.3f, 3f, false,
                v -> Config.animSpeed = v);
        toggleWide(row(), "dynamicisland.opt.roundCorners", () -> Config.roundCorners, v -> Config.roundCorners = v);

        section("dynamicisland.section.position");
        slider(col0(), row(), colW(), "dynamicisland.opt.offsetX", Config.offsetX, -500f, 500f, true,
                v -> Config.offsetX = (int) v);
        slider(col1(), topAt(), colW(), "dynamicisland.opt.offsetY", Config.offsetY, -40f, 120f, true,
                v -> Config.offsetY = (int) v);
        action(col0(), row(), "dynamicisland.screen.reset", () -> {
            Config.offsetX = 0; Config.offsetY = 8; Config.save();
        });
        addRenderableWidget(new Button.Builder(Component.translatable("dynamicisland.drag.hint"),
                        b -> Minecraft.getInstance().gui.setScreen(new DragScreen()))
                .bounds(col1(), topAt(), colW(), BTN_H).build());

        section("dynamicisland.section.perf");
        toggle(col0(), row(), "dynamicisland.opt.showFps", () -> Config.showFps, v -> Config.showFps = v);
        toggle(col1(), topAt(), "dynamicisland.opt.showMem", () -> Config.showMem, v -> Config.showMem = v);
        toggle(col0(), row(), "dynamicisland.opt.showGraph", () -> Config.showGraph, v -> Config.showGraph = v);
        toggle(col1(), topAt(), "dynamicisland.opt.showTps", () -> Config.showTps, v -> Config.showTps = v);

        section("dynamicisland.section.state");
        toggle(col0(), row(), "dynamicisland.opt.mining", () -> Config.modMining, v -> Config.modMining = v);
        toggle(col1(), topAt(), "dynamicisland.opt.use", () -> Config.modUse, v -> Config.modUse = v);
        toggle(col0(), row(), "dynamicisland.opt.sense", () -> Config.modSense, v -> Config.modSense = v);
        toggle(col1(), topAt(), "dynamicisland.opt.buildCount", () -> Config.modBuild, v -> Config.modBuild = v);

        section("dynamicisland.section.pop");
        toggle(col0(), row(), "dynamicisland.opt.pickup", () -> Config.modPickup, v -> Config.modPickup = v);
        toggle(col1(), topAt(), "dynamicisland.opt.hotbar", () -> Config.modHotbar, v -> Config.modHotbar = v);
        slider(col0(), row(), colW(), "dynamicisland.opt.pickupTime", Config.pickupTime, 0.5f, 8f, false,
                v -> Config.pickupTime = v);
        slider(col1(), topAt(), colW(), "dynamicisland.opt.hotbarTime", Config.hotbarTime, 0.5f, 8f, false,
                v -> Config.hotbarTime = v);

        section("dynamicisland.section.env");
        toggle(col0(), row(), "dynamicisland.opt.music", () -> Config.modMusic, v -> Config.modMusic = v);
        toggle(col1(), topAt(), "dynamicisland.opt.dayTime", () -> Config.modDayTime, v -> Config.modDayTime = v);
        slider(col0(), row(), colW(), "dynamicisland.opt.dayTimeHold", Config.dayTimeHold, 1f, 10f, false,
                v -> Config.dayTimeHold = v);
        slider(col1(), topAt(), colW(), "dynamicisland.opt.depthHold", Config.depthHold, 1f, 8f, false,
                v -> Config.depthHold = v);
        toggleWide(row(), "dynamicisland.opt.depth", () -> Config.modDepth, v -> Config.modDepth = v);

        section("dynamicisland.section.reticle");
        toggleWide(row(), "dynamicisland.opt.reticle", () -> Config.modReticle, v -> Config.modReticle = v);
        note("dynamicisland.note.reticleJump");

        section("dynamicisland.section.extras");
        toggle(col0(), row(), "dynamicisland.opt.deathRecall", () -> Config.modDeath,
                v -> { Config.modDeath = v; DeathMark.armed &= v; });
        toggle(col1(), topAt(), "dynamicisland.opt.itemCooldown", () -> Config.modCooldown, v -> Config.modCooldown = v);
        toggle(col0(), row(), "dynamicisland.opt.effectQueue", () -> Config.modEffects, v -> Config.modEffects = v);
        toggle(col1(), topAt(), "dynamicisland.opt.xpProgress", () -> Config.modXp, v -> Config.modXp = v);
        note("dynamicisland.note.extras");

        section("dynamicisland.section.danger");
        toggle(col0(), row(), "dynamicisland.opt.blastCountdown", () -> Config.modBlast,
                v -> Config.modBlast = v);
        toggle(col1(), topAt(), "dynamicisland.opt.fallPredict", () -> Config.modFall,
                v -> Config.modFall = v);
        toggle(col0(), row(), "dynamicisland.opt.rideSpeed", () -> Config.modRide,
                v -> Config.modRide = v);
        toggle(col1(), topAt(), "dynamicisland.opt.tradeRestock", () -> Config.modTrade,
                v -> Config.modTrade = v);
        slider(col0(), row(), contentW, "dynamicisland.opt.rideMinSpeed", Config.rideMinSpeed, 0f, 80f, false,
                v -> Config.rideMinSpeed = v);
        note("dynamicisland.note.danger");
    }

    // ================= 页面：信息条 =================

    private void buildInfoBar() {
        section("dynamicisland.section.infobar");
        toggle(col0(), row(), "dynamicisland.opt.showInfo", () -> Config.showInfo, v -> Config.showInfo = v);
        cycleInfo(col1(), topAt(), colW());
        toggle(col0(), row(), "dynamicisland.opt.attackCharge", () -> Config.modAttack, v -> Config.modAttack = v);
        slider(col1(), topAt(), colW(), "dynamicisland.opt.infoRotate", Config.infoRotate, 1f, 15f, false,
                v -> Config.infoRotate = v);
        colorCycle(col0(), row(), "dynamicisland.color.info", () -> Config.colorInfo,
                v -> Config.colorInfo = v);
        note("dynamicisland.note.infobar");
    }

    // ================= 页面：通知 =================

    private void buildNotice() {
        section("dynamicisland.section.notice");
        toggle(col0(), row(), "dynamicisland.opt.notice", () -> Config.modNotice, v -> Config.modNotice = v);
        slider(col1(), topAt(), colW(), "dynamicisland.opt.noticeTime", Config.noticeTime, 1f, 10f, false,
                v -> Config.noticeTime = v);
        colorCycle(col0(), row(), "dynamicisland.color.notice", () -> Config.colorNotice,
                v -> Config.colorNotice = v);

        section("dynamicisland.section.types");
        toggle(col0(), row(), "dynamicisland.opt.noticeFull", () -> Config.noticeFull, v -> Config.noticeFull = v);
        toggle(col1(), topAt(), "dynamicisland.opt.noticeDurability", () -> Config.noticeDurability, v -> Config.noticeDurability = v);
        toggle(col0(), row(), "dynamicisland.opt.noticeEffect", () -> Config.noticeEffect, v -> Config.noticeEffect = v);
        toggle(col1(), topAt(), "dynamicisland.opt.noticeMention", () -> Config.noticeMention, v -> Config.noticeMention = v);
        toggle(col0(), row(), "dynamicisland.opt.noticeAdvancement", () -> Config.noticeAdvancement, v -> Config.noticeAdvancement = v);
        toggle(col1(), topAt(), "dynamicisland.opt.noticeBlock", () -> Config.noticeBlock, v -> Config.noticeBlock = v);
        slider(col0(), row(), colW(), "dynamicisland.opt.buildWarn", Config.buildWarn, 1f, 48f, true,
                v -> Config.buildWarn = (int) v);
        addRenderableWidget(new Button.Builder(Component.translatable("dynamicisland.screen.clearNotice"),
                        b -> { Notifier.clear(); }).bounds(col1(), topAt(), colW(), BTN_H).build());
        note("dynamicisland.note.notice");

        section("dynamicisland.section.noticeNew");
        toggle(col0(), row(), "dynamicisland.opt.noticeDropWarn", () -> Config.noticeDropWarn,
                v -> Config.noticeDropWarn = v);
        toggle(col1(), topAt(), "dynamicisland.opt.noticeSystem", () -> Config.noticeSystem,
                v -> Config.noticeSystem = v);
        toggle(col0(), row(), "dynamicisland.opt.noticePlayer", () -> Config.noticePlayer,
                v -> Config.noticePlayer = v);
        toggle(col1(), topAt(), "dynamicisland.opt.noticePet", () -> Config.noticePet,
                v -> Config.noticePet = v);
        toggle(col0(), row(), "dynamicisland.opt.noticeHistory", () -> Config.noticeHistory,
                v -> Config.noticeHistory = v);
        cycleSound(col1(), topAt());
        note("dynamicisland.note.noticeNew");
    }

    // ================= 页面：方块信息 =================

    private void buildBlock() {
        section("dynamicisland.section.block");
        toggle(col0(), row(), "dynamicisland.opt.reticleBlock", () -> Config.modReticleBlock, v -> Config.modReticleBlock = v);
        toggle(col1(), topAt(), "dynamicisland.opt.blockPanelCoord", () -> Config.blockPanelCoord, v -> Config.blockPanelCoord = v);
        slider(col0(), row(), colW(), "dynamicisland.opt.blockPanelX", Config.blockPanelX, -600f, 600f, true,
                v -> Config.blockPanelX = (int) v);
        slider(col1(), topAt(), colW(), "dynamicisland.opt.blockPanelY", Config.blockPanelY, 0f, 600f, true,
                v -> Config.blockPanelY = (int) v);
        action(col0(), row(), "dynamicisland.screen.reset", () -> {
            Config.blockPanelX = 150; Config.blockPanelY = 90; Config.save();
        });
        action(col1(), topAt(), "dynamicisland.screen.center", () -> { Config.blockPanelX = 0; Config.save(); });
        colorCycle(col0(), row(), "dynamicisland.color.block", () -> Config.colorBlock,
                v -> Config.colorBlock = v);
        note("dynamicisland.note.block");
    }

    // ================= 页面：Tritium Music 联动 =================

    private void buildTritium() {
        section("dynamicisland.section.tritium");
        toggle(col0(), row(), "dynamicisland.opt.tritiumLink", () -> Config.modTritium,
                v -> Config.modTritium = v);
        toggle(col1(), topAt(), "dynamicisland.opt.tritiumCover", () -> Config.tritiumCover,
                v -> Config.tritiumCover = v);
        slider(col0(), row(), colW(), "dynamicisland.opt.tritiumHold", Config.tritiumHold, 1f, 10f, false,
                v -> Config.tritiumHold = v);
        slider(col1(), topAt(), colW(), "dynamicisland.opt.tritiumEndWarn", Config.tritiumEndWarn, 1f, 30f, false,
                v -> Config.tritiumEndWarn = v);
        colorCycle(col0(), row(), "dynamicisland.color.music", () -> Config.colorMusic,
                v -> Config.colorMusic = v);
        noteRaw(tritiumStateLine());
        note("dynamicisland.note.tritium");
    }

    private String tritiumStateLine() {
        if (!Config.modTritium) return tr("dynamicisland.note.tritiumOff");
        boolean ok;
        try { ok = TritiumLink.available(); } catch (Throwable t) { ok = false; }
        return ok ? tr("dynamicisland.note.tritiumOn") : tr("dynamicisland.note.tritiumMissing");
    }

    // ================= 页面：灵动焦点 =================

    private void buildFocus() {
        section("dynamicisland.section.focus");
        toggle(col0(), row(), "dynamicisland.opt.focusEnabled", () -> Config.focusEnabled, v -> {
            Config.focusEnabled = v;
            if (!v) FocusOrb.reset();
        });
        slider(col1(), topAt(), colW(), "dynamicisland.opt.focusHold", Config.focusHold, 1f, 8f, false,
                v -> Config.focusHold = v);
        toggle(col0(), row(), "dynamicisland.opt.focusRightFirst", () -> Config.focusRightFirst,
                v -> Config.focusRightFirst = v);
        slider(col1(), topAt(), colW(), "dynamicisland.opt.focusGap", Config.focusGap, 2f, 20f, false,
                v -> Config.focusGap = v);
        colorCycle(col0(), row(), "dynamicisland.color.focus", () -> Config.colorFocus,
                v -> Config.colorFocus = v);
        note("dynamicisland.note.focus");

        section("dynamicisland.section.focusSources");
        toggle(col0(), row(), "dynamicisland.opt.focusMusic", () -> Config.focusMusic,
                v -> Config.focusMusic = v);
        toggle(col1(), topAt(), "dynamicisland.opt.focusDay", () -> Config.focusDay,
                v -> Config.focusDay = v);
        toggle(col0(), row(), "dynamicisland.opt.focusDurability", () -> Config.focusDurability,
                v -> Config.focusDurability = v);
        toggle(col1(), topAt(), "dynamicisland.opt.focusPotion", () -> Config.focusPotion,
                v -> Config.focusPotion = v);
        toggle(col0(), row(), "dynamicisland.opt.focusHunger", () -> Config.focusHunger,
                v -> Config.focusHunger = v);
        toggle(col1(), topAt(), "dynamicisland.opt.focusAir", () -> Config.focusAir,
                v -> Config.focusAir = v);
        toggle(col0(), row(), "dynamicisland.opt.focusDrop", () -> Config.focusDrop,
                v -> Config.focusDrop = v);
        toggle(col1(), topAt(), "dynamicisland.opt.focusMark", () -> Config.focusMark,
                v -> Config.focusMark = v);
        toggle(col0(), row(), "dynamicisland.opt.focusCure", () -> Config.focusCure,
                v -> Config.focusCure = v);
        toggle(col1(), topAt(), "dynamicisland.opt.focusXpOrb", () -> Config.focusXpOrb,
                v -> Config.focusXpOrb = v);
        toggle(col0(), row(), "dynamicisland.opt.focusCloud", () -> Config.focusCloud,
                v -> Config.focusCloud = v);
        toggle(col1(), topAt(), "dynamicisland.opt.focusEndermite", () -> Config.focusEndermite,
                v -> Config.focusEndermite = v);
        note("dynamicisland.note.focusSources");

        section("dynamicisland.section.focusThresh");
        slider(col0(), row(), colW(), "dynamicisland.opt.durabilityMax", Config.durabilityMax, 1f, 200f, true,
                v -> Config.durabilityMax = (int) v);
        slider(col1(), topAt(), colW(), "dynamicisland.opt.dayLeadSec", Config.dayLeadSec, 5f, 10f, false,
                v -> Config.dayLeadSec = v);
        slider(col0(), row(), contentW, "dynamicisland.opt.potionMinSec", Config.potionMinSec, 30f, 600f, false,
                v -> Config.potionMinSec = v);
        note("dynamicisland.note.focusThresh");

        section("dynamicisland.section.focusRecall");
        noteRaw(tr("dynamicisland.note.focusKeyRecent") + "  " + keyName(KeyBinds.FOCUS));
        noteRaw(tr("dynamicisland.note.focusKeyLeft") + "  " + keyName(KeyBinds.FOCUS_LEFT));
        noteRaw(tr("dynamicisland.note.focusKeyRight") + "  " + keyName(KeyBinds.FOCUS_RIGHT));
    }

    // ================= 布局基元 =================

    private int row() { int y = viewTop + cursor; cursor += ROW_H; return y; }

    /** 复用上一个 row() 的纵坐标，用来在同一行放第二个控件 */
    private int topAt() { return viewTop + cursor - ROW_H; }

    private void section(String key) {
        lines.add(new Line(viewTop + cursor + 3, tr(key), true));
        cursor += 16;
    }

    private void note(String key) {
        lines.add(new Line(viewTop + cursor, tr(key), false));
        cursor += 13;
    }

    /** 已经拼好、不再走翻译键的说明行 */
    private void noteRaw(String text) {
        lines.add(new Line(viewTop + cursor, text, false));
        cursor += 13;
    }

    private int colW() { return (contentW - 6) / 2; }
    private int col0() { return contentX; }
    private int col1() { return contentX + colW() + 6; }

    private void addTabs() {
        int n = TAB_KEY.length;
        int gapPx = 2;
        int tw = (contentW - gapPx * (n - 1)) / n;
        int x = contentX;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Button b = new Button.Builder(Component.literal(tr(TAB_KEY[i])),
                            btn -> { tab = idx; scroll = 0; rebuild(); })
                    .bounds(x, 32, tw, 16).build();
            if (i == tab) b.active = false; // 当前页置灰，当作指示
            addRenderableWidget(b);
            tabButtons.add(b);
            x += tw + gapPx;
        }
    }

    private void toggle(int x, int y, String key, BooleanSupplier get, Consumer<Boolean> set) {
        Button b = new Button.Builder(Component.empty(), btn -> {
            boolean nv = !get.getAsBoolean();
            set.accept(nv);
            btn.setMessage(Component.literal(label(key, nv)));
        }).bounds(x, y, colW(), BTN_H).build();
        b.setMessage(Component.literal(label(key, get.getAsBoolean())));
        addRenderableWidget(b);
    }

    private void toggleWide(int y, String key, BooleanSupplier get, Consumer<Boolean> set) {
        Button b = new Button.Builder(Component.empty(), btn -> {
            boolean nv = !get.getAsBoolean();
            set.accept(nv);
            btn.setMessage(Component.literal(label(key, nv)));
        }).bounds(contentX, y, contentW, BTN_H).build();
        b.setMessage(Component.literal(label(key, get.getAsBoolean())));
        addRenderableWidget(b);
    }

    private void slider(int x, int y, int w, String key, float v, float lo, float hi, boolean intMode, DiSlider.Sink sink) {
        addRenderableWidget(new DiSlider(x, y, w, BTN_H, tr(key), v, lo, hi, intMode, sink));
    }

    private void action(int x, int y, String key, Runnable r) {
        addRenderableWidget(new Button.Builder(Component.translatable(key), b -> { r.run(); rebuild(); })
                .bounds(x, y, colW(), BTN_H).build());
    }


    /** 板块调色：点击在调色板里循环，按钮上直接显示色块 */
    private void colorCycle(int x, int y, String key, IntSupplier get, IntConsumer set) {
        ColorButton b = new ColorButton(x, y, colW(), BTN_H, btn -> {
            int cur = get.getAsInt();
            int idx = 0;
            for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == cur) { idx = i; break; }
            int nv = PALETTE[(idx + 1) % PALETTE.length];
            set.accept(nv);
            ((ColorButton) btn).set(nv, colorLabel(key, nv));
        });
        b.set(get.getAsInt(), colorLabel(key, get.getAsInt()));
        addRenderableWidget(b);
    }

    private String colorLabel(String key, int c) {
        return tr(key) + ": " + (c < 0 ? tr("dynamicisland.color.auto") : Config.hex(c));
    }

    /** 通知音效等级：0=关闭 1=仅紧急 2=全部 */
    private void cycleSound(int x, int y) {
        Button b = new Button.Builder(Component.empty(), btn ->
                btn.setMessage(Component.literal(soundLabel(Config.noticeSound = (Config.noticeSound + 1) % 3)))
        ).bounds(x, y, colW(), BTN_H).build();
        b.setMessage(Component.literal(soundLabel(Config.noticeSound)));
        addRenderableWidget(b);
    }

    private String soundLabel(int v) {
        String k = v == 0 ? "dynamicisland.sound.off"
                : (v == 1 ? "dynamicisland.sound.urgent" : "dynamicisland.sound.all");
        return tr("dynamicisland.opt.noticeSound") + ": " + tr(k);
    }

    private void cycleInfo(int x, int y, int w) {
        Button b = new Button.Builder(Component.empty(), btn -> {
            Config.infoMode = nextInfo();
            btn.setMessage(Component.literal(infoLabel()));
        }).bounds(x, y, w, BTN_H).build();
        b.setMessage(Component.literal(infoLabel()));
        addRenderableWidget(b);
    }

    private void cycleTheme(int x, int y, int w) {
        Button b = new Button.Builder(Component.empty(), btn -> {
            Config.theme = Theme.next(Config.theme);
            btn.setMessage(Component.literal(skinLabel()));
        }).bounds(x, y, w, BTN_H).build();
        b.setMessage(Component.literal(skinLabel()));
        addRenderableWidget(b);
    }

    // ================= 滚动 =================

    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, contentHeight - viewH());
        if (maxScroll <= 0) return false;
        scroll = (int) Math.round(Anim.clamp((float) (scroll - scrollY * 18f), 0f, (float) maxScroll));
        rebuild();
        return true;
    }

    private int viewH() { return Math.max(48, viewBottom - viewTop); }

    private void clampScroll() {
        int maxScroll = Math.max(0, contentHeight - viewH());
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private void shift(int dy) {
        if (dy == 0) return;
        for (GuiEventListener l : children()) {
            if (!(l instanceof AbstractWidget w) || tabButtons.contains(w)) continue;
            w.setY(w.getY() + dy);
        }
        for (Line l : lines) l.y += dy;
    }

    /** 视口外的控件不再响应鼠标，避免点到看不见的东西 */
    private void refreshInteractivity() {
        for (GuiEventListener l : children()) {
            if (!(l instanceof AbstractWidget w) || tabButtons.contains(w)) continue;
            w.active = w.getY() + w.getHeight() > viewTop && w.getY() < viewBottom;
        }
    }

    // ================= 渲染 =================

    @Override public void extractRenderState(GuiGraphicsExtractor gg, int mx, int my, float pt) {
        // 注意：框架的 extractRenderStateWithTooltipAndSubtitles 已经调过 extractBackground
        // （里面会做高斯模糊，一帧只能有一次），这里绝不能再调，否则 "Can only blur once per frame"
        Font f = this.font;
        gg.centeredText(f, title, width / 2, 10, 0xFFFFFF);
        gg.centeredText(f, keyHint(), width / 2, 21, 0x8A9099);

        // 标签栏画在内容视口(viewTop)上方，必须先画：
        // 若交给下面的 super.render()，会被 scissor(从 viewTop 起) 整条裁掉，导致看起来没有分页。
        for (AbstractWidget tabBtn : tabButtons) tabBtn.extractRenderState(gg, mx, my, pt);

        gg.enableScissor(contentX - 2, viewTop, contentX + contentW + 8, viewBottom);
        try {
            super.extractRenderState(gg, mx, my, pt);
            for (Line l : lines) {
                if (l.section) {
                    gg.text(f, l.text, contentX, l.y, 0x8FB4D6, false);
                    gg.horizontalLine(contentX, contentX + contentW, l.y + 11, 0x221E3A);
                } else {
                    gg.text(f, l.text, contentX, l.y, 0x6E7681, false);
                }
            }
        } finally {
            gg.disableScissor();
        }

        drawScrollbar(gg);
        gg.centeredText(f, Component.literal("FPS " + Metrics.fps + "  ·  "
                        + (int) (Metrics.memRatio * 100) + "%  ·  " + skinName()),
                width / 2, height - 13, 0x8A9099);
    }

    private void drawScrollbar(GuiGraphicsExtractor gg) {
        int maxScroll = contentHeight - viewH();
        if (maxScroll <= 0) return;
        int x = contentX + contentW + 3;
        gg.horizontalLine(x, x + 2, viewTop, 0x30FFFFFF);
        gg.horizontalLine(x, x + 2, viewBottom, 0x30FFFFFF);
        int thumbH = Math.max(14, viewH() * viewH() / Math.max(1, contentHeight));
        int y = viewTop + (int) ((viewH() - thumbH) * (scroll / (float) maxScroll));
        gg.fill(x, y, x + 3, y + thumbH, 0x70FFFFFF);
    }

    // ================= 杂项 =================

    private Config.InfoMode nextInfo() {
        Config.InfoMode[] v = Config.InfoMode.values();
        return v[(Config.infoMode.ordinal() + 1) % v.length];
    }

    private String infoLabel() {
        return tr("dynamicisland.opt.infoMode") + ": " + tr("dynamicisland.info." + Config.infoMode.name().toLowerCase());
    }

    private String skinLabel() {
        String name;
        try { name = Component.translatable(Config.theme.translationKey()).getString(); }
        catch (Throwable t) { name = Config.theme.id; }
        return tr("dynamicisland.opt.theme") + ": " + name;
    }

    private String skinName() {
        try { return Component.translatable(Config.theme.translationKey()).getString(); }
        catch (Throwable t) { return Config.theme.id; }
    }

    private String label(String key, boolean v) { return tr(key) + ": " + (v ? "✔" : "✘"); }

    private String keyHint() {
        String key;
        try { key = KeyBinds.PANEL == null ? "?" : KeyBinds.PANEL.getTranslatedKeyMessage().getString(); }
        catch (Throwable t) { key = "?"; }
        try { return Component.translatable("dynamicisland.screen.hintKey", key).getString(); }
        catch (Throwable t) { return key; }
    }

    private String keyName(net.minecraft.client.KeyMapping km) {
        try { return km == null ? "?" : km.getTranslatedKeyMessage().getString(); }
        catch (Throwable t) { return "?"; }
    }

    private static String tr(String key) {
        try { return Component.translatable(key).getString(); } catch (Throwable t) { return key; }
    }

    @Override public void onClose() {
        Config.save();
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
