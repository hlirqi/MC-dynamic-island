package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能中心：模组各类「玩家自定义功能」的入口界面。
 *
 * 布局与设置界面一致 —— 顶部是**分页标签**（后续再加功能只需往 TAB_KEY 里补一行），
 * 中间是可滚动的事件列表，下方是操作按钮。当前只有「定时器」一页。
 *
 * 列表操作：单击选中，双击该行进入编辑；下方按钮对选中项生效。
 */
public class FunctionCenter extends Screen {

    /** 后续新增功能只要往这里加一行 */
    private static final String[] TAB_KEY = { "dynamicisland.center.tab.timer" };

    private static final int ROW_H = 36;
    private static final int BTN_H = 20;
    private static final int BTN_W = 62;

    private final Screen parent;
    private int tab = 0;

    private int contentX, contentW;
    private int listTop, listBottom;
    private int listScroll = 0;
    private int listContentH = 0;

    /** 选中的事件 id（-1 表示没选） */
    private int selectedId = -1;

    /** 删除确认：第一次按「删除」先武装，3 秒内再按一次才真的删 */
    private long deleteArmedUntil = 0L;
    private int deleteArmedId = -1;

    private long lastClickAt = 0L;
    private int lastClickRow = -1;

    private final List<AbstractWidget> tabButtons = new ArrayList<>();
    private Button deleteButton;

    public FunctionCenter(Screen parent) {
        super(Component.translatable("dynamicisland.center.title"));
        this.parent = parent;
    }

    // ================= 生命周期 =================

    @Override
    protected void init() {
        clearWidgets();
        tabButtons.clear();
        TimerCenter.ensureLoaded();

        contentW = Math.min(360, Math.max(240, width - 24));
        contentX = (width - contentW) / 2;
        listTop = 50;
        listBottom = Math.max(listTop + 40, height - 42);

        buildTabs();
        buildButtons();
        clampScroll();
    }

    private void buildTabs() {
        int n = TAB_KEY.length;
        int tw = Math.min(120, contentW / Math.max(1, n));
        int x = contentX;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Button b = new Button.Builder(Component.literal(tr(TAB_KEY[i])),
                    btn -> { tab = idx; listScroll = 0; rebuild(); })
                    .bounds(x, 26, tw, 16).build();
            if (i == tab) b.active = false;   // 当前页置灰当指示
            addRenderableWidget(b);
            tabButtons.add(b);
            x += tw + 2;
        }
    }

    private void rebuild() {
        double keepScroll = listScroll;
        init();
        listScroll = (int) keepScroll;
        clampScroll();
    }

    private void buildButtons() {
        int y = height - 30;
        int gap = 4;
        // 窗口窄时自动收窄按钮，保证 5 个按钮永远在屏幕内
        int bw = Math.min(BTN_W, Math.max(36, (width - 8 - gap * 4) / 5));
        int total = bw * 5 + gap * 4;
        int x = (width - total) / 2;

        addRenderableWidget(new Button.Builder(Component.translatable("dynamicisland.center.enable"), b -> {
            TimerCenter.Event e = selected();
            if (e != null) TimerCenter.start(e);
        }).bounds(x, y, bw, BTN_H).build());
        x += bw + gap;

        addRenderableWidget(new Button.Builder(Component.translatable("dynamicisland.center.disable"), b -> {
            TimerCenter.Event e = selected();
            if (e != null) TimerCenter.stop(e);
        }).bounds(x, y, bw, BTN_H).build());
        x += bw + gap;

        addRenderableWidget(new Button.Builder(Component.translatable("dynamicisland.center.add"), b -> {
            if (this.minecraft != null) this.minecraft.setScreen(new TimerEditScreen(this, null));
        }).bounds(x, y, bw, BTN_H).build());
        x += bw + gap;

        deleteButton = new Button.Builder(Component.translatable("dynamicisland.center.delete"), b -> onDelete())
                .bounds(x, y, bw, BTN_H).build();
        addRenderableWidget(deleteButton);
        x += bw + gap;

        addRenderableWidget(new Button.Builder(Component.translatable("dynamicisland.center.close"), b -> onClose())
                .bounds(x, y, bw, BTN_H).build());
    }

    private boolean isDeleteArmed(TimerCenter.Event e) {
        return e != null && deleteArmedId == e.id && System.currentTimeMillis() < deleteArmedUntil;
    }

    private void onDelete() {
        TimerCenter.Event e = selected();
        if (e == null) return;
        if (!isDeleteArmed(e)) {
            deleteArmedId = e.id;
            deleteArmedUntil = System.currentTimeMillis() + 3000L;
            syncDeleteButton();
            return;
        }
        TimerCenter.remove(e);
        selectedId = -1;
        cancelDeleteArm();
        rebuild();
    }

    private void cancelDeleteArm() {
        deleteArmedId = -1;
        deleteArmedUntil = 0L;
        syncDeleteButton();
    }

    /** 删除按钮二次确认文案 */
    private void syncDeleteButton() {
        if (deleteButton == null) return;
        deleteButton.setMessage(Component.translatable(isDeleteArmed(selected())
                ? "dynamicisland.center.confirmDelete" : "dynamicisland.center.delete"));
    }

    private TimerCenter.Event selected() {
        TimerCenter.Event e = TimerCenter.byId(selectedId);
        if (e == null) selectedId = -1;
        return e;
    }

    // ================= 交互 =================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int idx = rowAt(mx, my);
            if (idx >= 0) {
                TimerCenter.Event e = TimerCenter.events().get(idx);
                long now = System.currentTimeMillis();
                if (lastClickRow == idx && now - lastClickAt < 320L) {
                    // 双击行 = 编辑
                    if (this.minecraft != null) this.minecraft.setScreen(new TimerEditScreen(this, e));
                    lastClickRow = -1;
                    return true;
                }
                lastClickRow = idx;
                lastClickAt = now;
                selectedId = e.id;
                cancelDeleteArm();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, listContentH - (listBottom - listTop));
        if (max <= 0) return false;
        listScroll = (int) Anim.clamp((float) (listScroll - delta * 20f), 0f, (float) max);
        return true;
    }

    private void clampScroll() {
        int max = Math.max(0, listContentH - (listBottom - listTop));
        listScroll = Math.max(0, Math.min(listScroll, max));
    }

    private int rowAt(double mx, double my) {
        if (mx < contentX || mx > contentX + contentW) return -1;
        if (my < listTop || my > listBottom) return -1;
        int y = (int) (my - listTop + listScroll);
        int idx = y / ROW_H;
        if (idx < 0 || idx >= TimerCenter.events().size()) return -1;
        return idx;
    }

    // ================= 渲染 =================

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        renderBackground(gg);
        Font f = this.font;

        // 删除二次确认的窗口过期后自动撤回文案
        if (deleteArmedId != -1 && System.currentTimeMillis() > deleteArmedUntil) cancelDeleteArm();

        gg.drawCenteredString(f, title, width / 2, 10, 0xFFFFFF);
        gg.drawCenteredString(f, tr("dynamicisland.center.hint"), width / 2, 39, 0x8A9099);

        int clipL = contentX - 2, clipR = contentX + contentW + 2;
        gg.enableScissor(clipL, listTop, clipR, listBottom);
        try {
            if (tab == 0) drawTimerList(gg, f, mx, my);
        } finally {
            gg.disableScissor();
        }

        drawScrollbar(gg);
        super.render(gg, mx, my, pt);
        gg.drawCenteredString(f, tr("dynamicisland.center.footer", TimerCenter.events().size()),
                width / 2, height - 12, 0x6E7681);
    }

    private void drawTimerList(GuiGraphics gg, Font f, int mx, int my) {
        List<TimerCenter.Event> list = TimerCenter.events();
        listContentH = Math.max(list.size() * ROW_H, listBottom - listTop);

        if (list.isEmpty()) {
            gg.drawCenteredString(f, tr("dynamicisland.center.empty"), width / 2, listTop + 34, 0x6E7681);
            gg.drawCenteredString(f, tr("dynamicisland.center.emptyHint"), width / 2, listTop + 48, 0x565C66);
            return;
        }

        int hover = rowAt(mx, my);
        for (int i = 0; i < list.size(); i++) {
            TimerCenter.Event e = list.get(i);
            int y = listTop + i * ROW_H - listScroll;
            if (y + ROW_H < listTop || y > listBottom) continue;
            boolean sel = e.id == selectedId;
            drawRow(gg, f, e, contentX, y, contentW, sel, i == hover);
        }
    }

    private void drawRow(GuiGraphics gg, Font f, TimerCenter.Event e,
                         int x, int y, int w, boolean sel, boolean hover) {
        Theme th = Config.theme;
        int bg = sel ? 0x3A3A46 : (hover ? 0x2C2C36 : 0x23232C);
        RenderUtil.roundRect(gg.pose(), x, y + 1, w, ROW_H - 4, 5f, 0xFF000000 | bg);
        if (sel) {
            RenderUtil.roundRect(gg.pose(), x, y + 1, w, ROW_H - 4, 5f,
                    RenderUtil.argb(e.color, 0.16f));
        }

        // 左侧色条：事件自己的颜色
        RenderUtil.roundRect(gg.pose(), x + 4, y + 7, 3f, ROW_H - 16, 1.5f, 0xFF000000 | e.color);

        int left = x + 13;
        int right = x + w - 10;

        // 名称 + 类型标签
        String name = RenderUtil.elide(f, e.name, (right - left) * 0.6f);
        gg.drawString(f, name, left, y + 6, 0xFFE8E8F0, false);
        String typeTag = "[" + tr(e.countdown ? "dynamicisland.timer.type.countdown"
                : "dynamicisland.timer.type.timer") + "]";
        gg.drawString(f, typeTag, left + f.width(name) + 5, y + 6, 0xFF8A9099, false);

        // 右侧：状态 + 时间
        String state = TimerCenter.stateText(e);
        int stateCol = e.enabled ? th.good : (e.done || e.doneHold > 0f ? e.color : th.textDim);
        gg.drawString(f, state, right - f.width(state), y + 6, RenderUtil.argb(stateCol, 0.95f), false);

        String time = e.countdown
                ? tr("dynamicisland.timer.sub.countdown", TimerCenter.fullClock(e.remainingSecs()))
                : tr("dynamicisland.timer.sub.timer", TimerCenter.fullClock(e.elapsedSecs()),
                TimerCenter.fullClock(e.seconds));
        gg.drawString(f, RenderUtil.elide(f, time, (right - left) * 0.9f), left, y + 17,
                RenderUtil.argb(e.color, 0.9f), false);

        // 描述（有就显示在时间右侧）
        if (!e.desc.isEmpty()) {
            int dvx = left + f.width(time) + 8;
            int avail = right - dvx;
            if (avail > 20) {
                gg.drawString(f, RenderUtil.elide(f, e.desc, avail), dvx, y + 17, 0xFF6E7681, false);
            }
        }

        // 进度条
        float p = e.progress();
        int barY = y + ROW_H - 8;
        RenderUtil.progressBar(gg.pose(), left, barY, right - left, 4f, p,
                0x33FFFFFF, RenderUtil.argb(e.color, 0.95f));
    }

    private void drawScrollbar(GuiGraphics gg) {
        int viewH = listBottom - listTop;
        int max = listContentH - viewH;
        if (max <= 0) return;
        int x = contentX + contentW + 3;
        gg.hLine(x, x + 2, listTop, 0x30FFFFFF);
        gg.hLine(x, x + 2, listBottom, 0x30FFFFFF);
        int thumbH = Math.max(14, viewH * viewH / Math.max(1, listContentH));
        int y = listTop + (int) ((viewH - thumbH) * (listScroll / (float) max));
        gg.fill(x, y, x + 3, y + thumbH, 0x70FFFFFF);
    }

    // ================= 杂项 =================

    private static String tr(String key, Object... args) {
        try { return Component.translatable(key, args).getString(); } catch (Throwable t) { return key; }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new FunctionCenter(parent));
    }
}
