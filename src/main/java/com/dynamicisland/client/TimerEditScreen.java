package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 添加 / 编辑一条定时器。
 *
 * 字段与需求一一对应：名称、描述、类型（倒计时 / 计时器）、时间、颜色。
 * 「时间」用分 + 秒两个输入框，另配一排常用预设；颜色是一排色块，直接点选。
 * 右侧实时预览一枚焦点小圆，颜色和进度就是它上岛后的样子。
 */
public class TimerEditScreen extends Screen {

    /** 可选颜色（0xRRGGBB） */
    private static final int[] PALETTE = {
            0x5AC8FA, 0x0A84FF, 0x5E5CE6, 0xBF5AF2, 0xFF375F, 0xFF453A,
            0xFF9F0A, 0xFFD60A, 0x30D158, 0x40C8E0, 0xFFFFFF, 0x8E8E93
    };
    /** 常用时长预设（秒），与按钮文案一一对应 */
    private static final int[] PRESETS = { 30, 60, 300, 600, 1800 };
    private static final String[] PRESET_KEY = {
            "dynamicisland.center.preset30", "dynamicisland.center.preset1m",
            "dynamicisland.center.preset5m", "dynamicisland.center.preset10m",
            "dynamicisland.center.preset30m"
    };

    private static final int SWATCH = 16;
    private static final int ROW = 22;
    /** 右侧留给预览圆的宽度 */
    private static final int PREVIEW_W = 74;

    private final Screen parent;
    private final TimerCenter.Event editing;

    private String name = "";
    private String desc = "";
    private boolean countdown = true;
    private int color = 0x5AC8FA;
    private int minutes = 1;
    private int seconds = 0;

    private EditBox nameBox, descBox, minBox, secBox;
    private Button typeButton, saveButton;

    private int panelX, panelW, panelTop;
    private int swatchX, swatchY;
    /** 色块尺寸与间距：面板窄时自动收缩，保证 12 个色块不溢出 */
    private int swSize = SWATCH, swGap = 6;

    public TimerEditScreen(Screen parent, TimerCenter.Event editing) {
        super(Component.translatable(editing == null ? "dynamicisland.center.addTitle"
                : "dynamicisland.center.editTitle"));
        this.parent = parent;
        this.editing = editing;
        if (editing != null) {
            name = editing.name;
            desc = editing.desc;
            countdown = editing.countdown;
            color = editing.color;
            minutes = editing.seconds / 60;
            seconds = editing.seconds % 60;
        }
    }

    // ================= 生命周期 =================

    @Override
    protected void init() {
        // Screen.resize() 会再调一次 init()，先把用户已经输入的内容收回来，免得改窗口大小就清空
        if (nameBox != null) name = nameBox.getValue();
        if (descBox != null) {
            desc = descBox.getValue();
            minutes = Math.max(0, parseInt(minBox.getValue(), minutes));
            seconds = Math.max(0, Math.min(59, parseInt(secBox.getValue(), seconds)));
        }

        panelW = Math.min(330, Math.max(230, width - 24));
        panelX = (width - panelW) / 2;
        panelTop = 34;

        int fx = panelX + 12;
        int fw = panelW - 24;
        int y = panelTop + 14;

        nameBox = new EditBox(font, fx, y, fw, 18, Component.empty());
        nameBox.setMaxLength(24);
        nameBox.setValue(name);
        nameBox.setHint(Component.translatable("dynamicisland.center.fieldNameHint"));
        addRenderableWidget(nameBox);
        y += ROW + 6;

        descBox = new EditBox(font, fx, y, fw, 18, Component.empty());
        descBox.setMaxLength(48);
        descBox.setValue(desc);
        descBox.setHint(Component.translatable("dynamicisland.center.fieldDescHint"));
        addRenderableWidget(descBox);
        y += ROW + 6;

        typeButton = new Button.Builder(typeLabel(), b -> {
            countdown = !countdown;
            b.setMessage(typeLabel());
        }).bounds(fx, y, 110, 18).build();
        addRenderableWidget(typeButton);
        y += ROW + 6;

        int half = (fw - PREVIEW_W - 8) / 2;
        minBox = new EditBox(font, fx, y, half, 18, Component.empty());
        minBox.setMaxLength(4);
        minBox.setValue(String.valueOf(minutes));
        minBox.setResponder(s -> clampMin());
        addRenderableWidget(minBox);

        secBox = new EditBox(font, fx + half + 8, y, half, 18, Component.empty());
        secBox.setMaxLength(2);
        secBox.setValue(String.valueOf(seconds));
        secBox.setResponder(s -> clampSec());
        addRenderableWidget(secBox);
        y += ROW + 2;

        // 预设
        int pw = (fw - 4 * 4) / 5;
        int px = fx;
        for (int i = 0; i < PRESETS.length; i++) {
            final int secs = PRESETS[i];
            addRenderableWidget(new Button.Builder(Component.translatable(PRESET_KEY[i]), b -> applyPreset(secs))
                    .bounds(px, y, pw, 16).build());
            px += pw + 4;
        }
        y += ROW + 2;

        swatchX = fx;
        swatchY = y;
        swGap = 6;
        int fit = (fw - (PALETTE.length - 1) * swGap) / PALETTE.length;
        swSize = Math.max(9, Math.min(SWATCH, fit));
        if (swSize < SWATCH) {   // 还不够就再压间距
            swGap = 2;
            swSize = Math.max(9, Math.min(SWATCH, (fw - (PALETTE.length - 1) * swGap) / PALETTE.length));
        }

        saveButton = new Button.Builder(Component.translatable("dynamicisland.center.save"), b -> onSave())
                .bounds(width / 2 - 82, height - 28, 78, 20).build();
        addRenderableWidget(saveButton);
        addRenderableWidget(new Button.Builder(Component.translatable("dynamicisland.center.cancel"), b -> onClose())
                .bounds(width / 2 + 4, height - 28, 78, 20).build());

        refreshSave();
    }

    private Component typeLabel() {
        return Component.translatable("dynamicisland.center.fieldType")
                .append(Component.literal(": "))
                .append(Component.translatable(countdown
                        ? "dynamicisland.timer.type.countdown" : "dynamicisland.timer.type.timer"));
    }

    private void clampMin() {
        minutes = Math.max(0, Math.min(TimerCenter.MAX_SECONDS / 60, parseInt(minBox.getValue(), minutes)));
        refreshSave();
    }

    private void clampSec() {
        seconds = Math.max(0, Math.min(59, parseInt(secBox.getValue(), seconds)));
        refreshSave();
    }

    private void applyPreset(int total) {
        minutes = total / 60;
        seconds = total % 60;
        minBox.setValue(String.valueOf(minutes));
        secBox.setValue(String.valueOf(seconds));
        refreshSave();
    }

    private void refreshSave() {
        if (saveButton != null) saveButton.active = totalSeconds() >= 1;
    }

    private int totalSeconds() {
        int t = minutes * 60 + seconds;
        return Math.max(0, Math.min(TimerCenter.MAX_SECONDS, t));
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return fallback; }
    }

    private void onSave() {
        if (totalSeconds() < 1) return;
        String n = nameBox.getValue().trim();
        String d = descBox.getValue().trim();
        if (editing == null) {
            TimerCenter.create(n, d, countdown, totalSeconds(), color);
        } else {
            editing.name = n.isEmpty() ? ProgressTracker.I18nS.tr("dynamicisland.timer.unnamed") : n;
            editing.desc = d;
            editing.countdown = countdown;
            editing.seconds = totalSeconds();
            editing.color = color & 0xFFFFFF;
            TimerCenter.save();
        }
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    // ================= 交互：色块 =================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (my >= swatchY && my <= swatchY + swSize) {
                for (int i = 0; i < PALETTE.length; i++) {
                    int x = swatchX + i * (swSize + swGap);
                    if (mx >= x && mx <= x + swSize) { color = PALETTE[i]; return true; }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // ================= 渲染 =================

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        renderBackground(gg);
        Font f = this.font;
        Theme th = Config.theme;

        int panelBottom = height - 36;
        RenderUtil.roundRect(gg.pose(), panelX, panelTop, panelW, panelBottom - panelTop, 7f, 0xE6101016);
        RenderUtil.roundRect(gg.pose(), panelX, panelTop, panelW, panelBottom - panelTop, 7f,
                RenderUtil.argb(color, 0.10f));

        gg.drawCenteredString(f, title, width / 2, 12, 0xFFFFFF);

        // 标签直接贴着各自的控件画，避免写死纵坐标
        int lx = panelX + 12;
        gg.drawString(f, tr("dynamicisland.center.fieldName"), lx, nameBox.getY() - 10, 0xFF9AA0AA, false);
        gg.drawString(f, tr("dynamicisland.center.fieldDesc"), lx, descBox.getY() - 10, 0xFF9AA0AA, false);
        gg.drawString(f, tr("dynamicisland.center.fieldTime"), lx, minBox.getY() - 10, 0xFF9AA0AA, false);

        // 颜色
        gg.drawString(f, tr("dynamicisland.center.fieldColor"), lx, swatchY - 12, 0xFF9AA0AA, false);
        for (int i = 0; i < PALETTE.length; i++) {
            int x = swatchX + i * (swSize + swGap);
            boolean on = PALETTE[i] == (color & 0xFFFFFF);
            RenderUtil.roundRect(gg.pose(), x - 1, swatchY - 1, swSize + 2, swSize + 2, 4f,
                    on ? 0xFFFFFFFF : 0x30FFFFFF);
            RenderUtil.roundRect(gg.pose(), x, swatchY, swSize, swSize, 3f, 0xFF000000 | PALETTE[i]);
        }

        drawPreview(gg, f, th);

        super.render(gg, mx, my, pt);
    }

    /** 右上角预览：跟焦点小圆同构的迷你版，颜色与进度即时反映当前设置 */
    private void drawPreview(GuiGraphics gg, Font f, Theme th) {
        float r = 18f;
        float cx = panelX + panelW - 36f;
        float cy = panelTop + 96f;
        int accent = color & 0xFFFFFF;

        RenderUtil.roundRect(gg.pose(), cx - r, cy - r, r * 2, r * 2, r, RenderUtil.argb(th.bg, 0.92f));
        float ringR = r - 3.5f, thk = 3.5f;
        RenderUtil.ringTrack(gg.pose(), cx, cy, ringR, thk, RenderUtil.argb(th.textDim, 0.28f));
        // 预览固定取「刚过半」的样子：一来能同时看见进度环和底环，二来时间文字长度适中
        RenderUtil.ring(gg.pose(), cx, cy, ringR, thk, 0.5f, RenderUtil.argb(accent, 0.95f));

        int total = Math.max(1, totalSeconds());
        String sample = TimerCenter.orbClock(countdown ? total / 2 : total / 2);
        gg.drawCenteredString(f, sample, (int) cx, (int) (cy - 4f), RenderUtil.argb(accent, 0.95f));
        gg.drawCenteredString(f, tr("dynamicisland.center.preview"), (int) cx, (int) (cy + r + 2f), 0xFF6E7681);
    }

    private static String tr(String key, Object... args) {
        try { return Component.translatable(key, args).getString(); } catch (Throwable t) { return key; }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
