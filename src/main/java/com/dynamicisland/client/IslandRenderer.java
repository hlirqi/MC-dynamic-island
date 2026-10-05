package com.dynamicisland.client;

import com.dynamicisland.Config;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 灵动岛 HUD 的全部绘制逻辑 */
public class IslandRenderer {

    public static final IslandRenderer INSTANCE = new IslandRenderer();

    public boolean visible = true;
    public boolean forceExpand = false;

    private final Anim animW = new Anim(180f);
    private final Anim animH = new Anim(30f);
    private final Anim animInfoW = new Anim(130f);
    private final Anim animInfoH = new Anim(18f);
    private final Anim animY = new Anim(0f);
    private final Anim animGrow = new Anim(0f); // 0=收起 1=展开，用于内容淡入
    // 封面配色：展开时把胶囊染成专辑封面主色，并铺上模糊封面
    // 过渡调快一些，短卡片（拾取等）才来得及染上色
    private final Anim coverMix = new Anim(0f);
    private final Anim tintR = new Anim(0f), tintG = new Anim(0f), tintB = new Anim(0f);

    {
        coverMix.stiffness = 520f; coverMix.damping = 38f;
        tintR.stiffness = 320f; tintR.damping = 32f;
        tintG.stiffness = 320f; tintG.damping = 32f;
        tintB.stiffness = 320f; tintB.damping = 32f;
    }
    private float alpha = 1f;
    private float contentFade = 1f;
    private float clock = 0f;
    private long lastNano = 0;

    private IslandStatus last = null;
    private String infoText = "";
    private float attackCharge = 1f;
    private boolean infoRowIsAttack = false;
    private int rotateIndex = 0;
    private float rotateTimer = 0f;
    private final int[] graphBuf = new int[Metrics.HISTORY];

    public void reset() {
        animW.snap(180f); animH.snap(30f); animInfoW.snap(130f); animInfoH.snap(18f);
        animY.snap(0f); animGrow.snap(0f); alpha = 1f; contentFade = 1f;
        FocusOrb.reset();
    }

    public void render(GuiGraphics gg, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        float dt = deltaTime();
        clock += dt;

        boolean wantVisible = visible && Config.enabled && !(Config.hideOnDebug && mc.options.renderDebug);
        alpha = Anim.smooth(alpha, wantVisible ? 1f : 0f, dt, 0.09f);
        animY.to(wantVisible ? 0f : -26f);
        animY.update(dt * Config.animSpeed);

        if (alpha <= 0.01f) { last = null; return; }

        IslandStatus st = ProgressTracker.update(dt);
        boolean expanded = st != null || forceExpand;
        if (st == null) st = idleStatus();

        if (last == null || last.kind != st.kind || !safeEq(last.title, st.title)) {
            contentFade = 0f;
            last = st;
        }
        contentFade = Anim.smooth(contentFade, 1f, dt * Config.animSpeed, 0.055f);

        // ---- 尺寸目标 ----
        float targetW, targetH;
        if (expanded) {
            targetW = 208f;
            targetH = forceExpand && !isRealState(st) ? 74f : 40f;
        } else {
            targetW = idleWidth();
            targetH = 30f;
        }
        animW.to(targetW); animH.to(targetH);
        animGrow.to(expanded ? 1f : 0f);
        animW.update(dt * Config.animSpeed);
        animH.update(dt * Config.animSpeed);
        animGrow.update(dt * Config.animSpeed);

        float scale = Config.scale;

        // ---- 信息条 / 攻击充能条（二者共用小信息栏，尺寸远小于胶囊） ----
        updateInfoText(dt, expanded);
        attackCharge = Config.modAttack ? attackScale(mc.player) : 1f;
        boolean attackRow = Config.modAttack && attackCharge < 0.985f;
        boolean textRow = Config.showInfo && !expanded && !infoText.isEmpty();
        boolean showInfoRow = attackRow || textRow;
        if (!attackRow) {
            // 信息条宽度跟随文字，但要保证不会顶到屏幕边缘（横向也是「不溢出」的一部分）
            float localAvail = Math.max(72f, mc.getWindow().getGuiScaledWidth() / Math.max(0.2f, scale) - 28f);
            infoText = RenderUtil.elide(mc.font, infoText, localAvail - 22f);
        }
        float iw = attackRow ? 104f : mc.font.width(infoText) + 22f;
        animInfoW.to(showInfoRow ? iw : 0f);
        animInfoH.to(showInfoRow ? (attackRow ? 20f : 18f) : 0f);
        animInfoW.update(dt * Config.animSpeed);
        animInfoH.update(dt * Config.animSpeed);
        infoRowIsAttack = attackRow;

        // ---- 定位 ----
        float cx = mc.getWindow().getGuiScaledWidth() / 2f + Config.offsetX;
        float cy = Config.offsetY + animY.get();
        // 脉冲缩放并入整体缩放，方便下面算出胶囊在屏幕上的真实矩形
        float pulse = st.pulse ? 1f + (float) Math.sin(clock * 7f) * 0.022f : 1f;
        float effScale = scale * pulse;

        PoseStack ps = gg.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0f);
        ps.scale(effScale, effScale, 1f);

        float w = animW.get(), h = animH.get();
        float x = -w / 2f, y = 0f;
        Theme th = Config.theme;

        // ---- 封面配色 ----
        // 只在「当前卡片就是这首歌」时才染封面色、铺模糊封面；
        // 拾取、挖掘等别的卡片一律用皮肤自己的背景。
        // st.cover != null 正是 tritiumStatus 给音乐卡片打的标记。
        boolean wantCover = Config.modTritium && Config.tritiumCover
                && TritiumLink.playing && TritiumLink.tint >= 0 && st.cover != null
                && (animGrow.get() > 0.25f || forceExpand);
        coverMix.to(wantCover ? 1f : 0f);
        coverMix.update(dt);
        int tc = TritiumLink.tint;
        if (tc >= 0) {
            tintR.to((tc >> 16) & 0xFF); tintG.to((tc >> 8) & 0xFF); tintB.to(tc & 0xFF);
        }
        tintR.update(dt); tintG.update(dt); tintB.update(dt);
        float cm = Anim.clamp(coverMix.get(), 0f, 1f);

        // ---- 胶囊本体 ----
        int baseAccent = Config.accentOf(Config.colorIsland, th);
        int accent = st.accent < 0 ? baseAccent : st.accent;
        if (cm > 0.01f && tc >= 0) {
            int live = ((int) tintR.get() << 16) | ((int) tintG.get() << 8) | (int) tintB.get();
            accent = RenderUtil.mix(accent, live, cm);
        }
        float bgA = Config.bgOpacity() * alpha;
        if (Config.shadow) RenderUtil.shadow(ps, x, y, w, h, h * 0.5f, 0x000000, alpha * (th.dark ? 1f : 0.7f));
        RenderUtil.roundRect(ps, x - 0.8f, y - 0.8f, w + 1.6f, h + 1.6f, h * 0.5f,
                RenderUtil.argb(mixTint(th.bg, accent, 0.10f), bgA * 0.9f));
        RenderUtil.roundRect(ps, x, y, w, h, h * 0.5f, RenderUtil.argb(th.bg, bgA));

        // 模糊封面铺底：只在音乐卡片上淡入，上面压一层暗色保证文字可读
        if (cm > 0.01f && TritiumLink.blurredReady()) {
            try {
                // 按封面真实比例裁中心（cover），避免方形封面被拉成胶囊的扁宽形
                float texAspect = CoverTint.aspect();
                float boxAspect = w / Math.max(1f, h);
                float uSpan = 1f, vSpan = 1f;
                if (boxAspect > texAspect) vSpan = texAspect / boxAspect;
                else uSpan = boxAspect / texAspect;
                // 略微放大取样区域 = 画面被放大 -> 视觉上更糊，同时边缘裁掉更干净
                float zoom = 1.28f;
                uSpan /= zoom; vSpan /= zoom;
                float u0 = 0.5f - uSpan * 0.5f, u1 = 0.5f + uSpan * 0.5f;
                float v0 = 0.5f - vSpan * 0.5f, v1 = 0.5f + vSpan * 0.5f;
                RenderUtil.roundRectImage(ps, TritiumLink.blurred, x, y, w, h, h * 0.5f, cm,
                        u0, v0, u1, v1);
            } catch (Throwable ignored) { }
            RenderUtil.roundRect(ps, x, y, w, h, h * 0.5f,
                    RenderUtil.argb(th.dark ? 0x000000 : 0x101014, 0.42f * cm));
        }

        Font font = mc.font;
        float grow = animGrow.get();
        float fade = contentFade * alpha;

        // 把绘制裁剪进胶囊矩形。enableScissor 用的是未缩放的 GUI 坐标，
        // 所以这里要自己把局部矩形换算回屏幕像素；有了它，任何文本都不可能溢出到胶囊外。
        int clipL = (int) Math.floor(cx + x * effScale);
        int clipT = (int) Math.floor(cy + y * effScale);
        int clipR = (int) Math.ceil(cx + (x + w) * effScale);
        int clipB = (int) Math.ceil(cy + (y + h) * effScale);
        boolean clipped = clipR - clipL > 2 && clipB - clipT > 2;
        if (clipped) gg.enableScissor(clipL, clipT, clipR, clipB);
        try {
            if (grow > 0.02f) drawExpanded(gg, font, th, st, x, y, w, h, accent, grow * fade);
            if (grow < 0.98f) drawIdle(gg, font, th, x, y, w, h, (1f - grow) * fade);
        } finally {
            if (clipped) gg.disableScissor();
        }

        // ---- 信息条 ----
        float iwidth = animInfoW.get(), iheight = animInfoH.get();
        if (iwidth > 1f) {
            float iy = y + h + 4f;
            int infoAccent = Config.accentOf(Config.colorInfo, th);
            RenderUtil.roundRect(ps, -iwidth / 2f, iy, iwidth, iheight, iheight * 0.5f,
                    RenderUtil.argb(RenderUtil.mix(th.bg, infoAccent, 0.18f), Config.bgOpacity() * 0.72f * alpha));
            if (infoRowIsAttack) {
                drawAttackRow(gg, font, th, -iwidth / 2f, iy, iwidth, iheight, alpha);
            } else {
                gg.drawCenteredString(font, infoText, (int) (0f), (int) (iy + iheight / 2f - 4f),
                        RenderUtil.argb(th.textDim, 0.95f * alpha));
            }
        }

        // ---- 灵动焦点：胶囊左右两侧的圆形小窗 ----
        // 仍在胶囊的局部坐标系里，因此跟随整体缩放、位置偏移与脉冲动画。
        // 胶囊一旦展开显示详细内容，焦点就整体淡出，不跟着一起变大。
        FocusOrb.render(gg, ps, font, th, w, h, alpha, 1f - Anim.clamp(grow, 0f, 1f));

        ps.popPose();

        // ---- 方块信息弹窗（独立小窗，位置可在设置里调） ----
        BlockPanel.render(gg, partialTick);

        // ---- 新增功能层：死亡点 / 冷却环 / 效果队列 / 经验 ----
        CooldownWheel.update(mc.player, partialTick);
        float extrasTop = cy + (h + (Config.showInfo ? animInfoH.get() + 4f : 0f)) * scale + 4f;
        float extrasUsed = ExtrasRenderer.render(gg, font, th, mc.player, mc.level,
                cx, extrasTop, scale, alpha, partialTick, System.currentTimeMillis());

        // ---- 通知 ----
        drawNotices(gg, font, th, cx, extrasTop + extrasUsed + 2f, scale);
    }

    private boolean isRealState(IslandStatus st) { return st.kind != IslandStatus.Kind.IDLE; }

    private void drawIdle(GuiGraphics gg, Font font, Theme th, float x, float y, float w, float h, float fade) {
        float px = x + 11f;
        float midY = y + h / 2f;
        float limitX = x + w - 11f; // 收起态同样不允许越过胶囊右缘

        if (Config.showFps) {
            String fps = String.valueOf(Metrics.fps);
            float need = Math.max(font.width(fps), font.width("FPS"));
            if (px + need <= limitX) {
                int col = RenderUtil.fpsColor(Metrics.fps, th);
                gg.drawString(font, fps, (int) px, (int) (midY - 9f), RenderUtil.argb(col, fade), false);
                gg.drawString(font, "FPS", (int) px, (int) (midY + 1f), RenderUtil.argb(th.textDim, 0.85f * fade), false);
                px += need + 9f;
            }
        }

        if (Config.showGraph) {
            int n = Metrics.historyCount;
            float gw = 46f, gh = 15f;
            if (n >= 2 && px + gw <= limitX) {
                Metrics.ordered(graphBuf);
                RenderUtil.graph(gg.pose(), px, midY - gh / 2f, gw, gh, graphBuf, n,
                        RenderUtil.fpsColor(Metrics.fps, th), 0.85f * fade);
                px += gw + 9f;
            }
        }

        if (Config.showMem) {
            float barW = 32f, barH = 5f;
            String txt = (int) (Metrics.memRatio * 100) + "%";
            float need = Math.max(barW, font.width(txt));
            if (px + need <= limitX) {
                RenderUtil.progressBar(gg.pose(), px, midY - barH / 2f - 5f, barW, barH, Metrics.memRatio,
                        RenderUtil.argb(th.textDim, 0.28f * fade), RenderUtil.argb(memColor(th), fade));
                gg.drawString(font, txt, (int) px, (int) (midY + 1f), RenderUtil.argb(th.textDim, 0.85f * fade), false);
                px += need + 6f;
            }
        }

        if (Config.showTps && px < limitX) {
            String t = "TPS " + Metrics.tps + (Metrics.ping >= 0 ? " · " + Metrics.ping + "ms" : "");
            gg.drawString(font, RenderUtil.elide(font, t, limitX - px), (int) px, (int) (midY - 4f),
                    RenderUtil.argb(th.text, 0.9f * fade), false);
        }
    }

    private void drawExpanded(GuiGraphics gg, Font font, Theme th, IslandStatus st,
                              float x, float y, float w, float h, int accent, float fade) {
        // 手动展开时的完整仪表盘
        if (st.kind == IslandStatus.Kind.IDLE && forceExpand) {
            drawDashboard(gg, font, th, x, y, w, h, fade);
            return;
        }

        float px = x + 11f;
        float iconSize = 16f;
        if (st.cover != null) {
            // 有封面就用圆形封面代替左侧那个小圆点
            RenderUtil.roundImage(gg.pose(), st.cover, px + iconSize / 2f, y + h / 2f, iconSize / 2f, fade);
            px += iconSize + 8f;
        } else if (!st.icon.isEmpty() && st.icon.getItem() != Items.AIR) {
            gg.renderItem(st.icon, (int) px, (int) (y + h / 2f - iconSize / 2f));
            px += iconSize + 8f;
        } else {
            float d = 8f;
            RenderUtil.roundRect(gg.pose(), px, y + h / 2f - d / 2f, d, d, d / 2f, RenderUtil.argb(accent, fade));
            px += d + 8f;
        }

        // 标题与右侧副标题共享一行：先给副标题留出空间，标题按剩余宽度截断
        int subW = st.sub.isEmpty() ? 0 : font.width(st.sub);
        float rightEdge = x + w - 11f;
        float titleSpace = rightEdge - px - (subW > 0 ? subW + 8f : 0f);
        if (titleSpace > 8f) {
            String title = RenderUtil.elide(font, st.title, titleSpace);
            gg.drawString(font, title, (int) px, (int) (y + 7f), RenderUtil.argb(th.text, fade), false);
        }

        if (subW > 0) {
            String sub = RenderUtil.elide(font, st.sub, Math.max(10f, (rightEdge - px) * 0.55f));
            int sw = font.width(sub);
            // 自定义定时器会用事件自己的颜色显示时间；其它来源仍走皮肤的次要文字色
            int subCol = st.textColor >= 0 ? st.textColor : th.textDim;
            gg.drawString(font, sub, (int) (rightEdge - sw), (int) (y + 7f),
                    RenderUtil.argb(subCol, 0.95f * fade), false);
        }

        if (st.showBar) {
            float barX = px, barW = x + w - 11f - px, barY = y + h - 15f;
            if (barW > 4f) {
                RenderUtil.progressBar(gg.pose(), barX, barY, barW, 5f, st.progress,
                        RenderUtil.argb(th.textDim, 0.22f * fade), RenderUtil.argb(accent, fade));
            }
        }
    }

    /**
     * 手动展开的完整仪表盘。
     *
     * 排版完全跟随「当前实时高度」：自上而下逐行分配，装不下就整行不画。
     * 之前四行是按最终高度写死的偏移量（y+42 / y+53），胶囊从 30 拉伸到 74
     * 的过程中会有一小段时间把下面两行画到胶囊外面——这就是溢出 Bug 的根因。
     */
    private void drawDashboard(GuiGraphics gg, Font font, Theme th, float x, float y, float w, float h, float fade) {
        float leftX = x + 12f;
        float rightX = x + w - 12f;
        if (rightX - leftX < 46f) return;

        float top = y + 6f;
        float bottom = y + h - 6f;
        if (bottom - top < 12f) return;
        float cur = top;

        // ---- 第一行：FPS / TPS / 延迟 + 帧率折线 ----
        if (cur + 19f <= bottom) {
            int col = RenderUtil.fpsColor(Metrics.fps, th);
            boolean hasGraph = Metrics.historyCount >= 2;
            // 有折线时给右侧留出固定空间，避免数值列把折线挤没
            float cellLimit = hasGraph ? rightX - 26f : rightX;

            float px = statCell(gg, font, String.valueOf(Metrics.fps), "FPS", leftX, cellLimit, cur,
                    RenderUtil.argb(col, fade), RenderUtil.argb(th.textDim, 0.8f * fade));
            px = statCell(gg, font, String.valueOf(Metrics.tps), "TPS", px, cellLimit, cur,
                    RenderUtil.argb(th.text, fade), RenderUtil.argb(th.textDim, 0.8f * fade));
            if (Metrics.ping >= 0) {
                px = statCell(gg, font, Metrics.ping + "ms", ProgressTracker.I18nS.tr("dynamicisland.unit.ping"), px,
                        cellLimit, cur, RenderUtil.argb(th.text, fade), RenderUtil.argb(th.textDim, 0.8f * fade));
            }

            if (hasGraph) {
                float gx = Math.max(px, cellLimit);
                float gw = rightX - gx;
                if (gw > 24f) {
                    Metrics.ordered(graphBuf);
                    RenderUtil.graph(gg.pose(), gx, cur, gw, 19f, graphBuf, Metrics.historyCount, col, 0.9f * fade);
                }
            }
            cur += 21f;
        }

        // ---- 第二行：内存条 ----
        if (cur + 9f <= bottom) {
            RenderUtil.progressBar(gg.pose(), leftX, cur, rightX - leftX, 5f, Metrics.memRatio,
                    RenderUtil.argb(th.textDim, 0.22f * fade), RenderUtil.argb(memColor(th), fade));
            cur += 9f;
        }

        // ---- 第三行：内存实况（左） + 坐标朝向（右，空间不足自动截断） ----
        if (cur + 11f <= bottom) {
            float avail = rightX - leftX;
            String mem = ProgressTracker.I18nS.tr("dynamicisland.dash.mem", Metrics.memUsedMb, Metrics.memMaxMb);
            String memTxt = RenderUtil.elide(font, mem, avail * 0.62f);
            gg.drawString(font, memTxt, (int) leftX, (int) (cur + 1f), RenderUtil.argb(th.textDim, 0.9f * fade), false);
            drawRight(gg, font, Metrics.coords + "  " + Metrics.facing,
                    leftX + font.width(memTxt) + 8f, rightX, cur + 1f, RenderUtil.argb(th.textDim, 0.9f * fade));
            cur += 11f;
        }

        // ---- 第四行：环境信息 ----
        if (cur + 11f <= bottom) {
            String env = Metrics.biome + "  ·  " + Metrics.timeStr + "  ·  " + Metrics.weather;
            String envTxt = RenderUtil.elide(font, env, rightX - leftX);
            gg.drawString(font, envTxt, (int) leftX, (int) (cur + 1f), RenderUtil.argb(th.textDim, 0.75f * fade), false);
        }
    }

    /**
     * 画一列「大数值 + 小标签」，返回下一列的起始 x。
     * 剩余宽度放不下就整列不画（返回原 x），永远不越过 limitX。
     */
    private float statCell(GuiGraphics gg, Font font, String value, String label, float px, float limitX,
                           float y, int vcol, int lcol) {
        float need = Math.max(font.width(value), font.width(label));
        if (px + need > limitX) return px;
        gg.drawString(font, value, (int) px, (int) y, vcol, false);
        gg.drawString(font, label, (int) px, (int) (y + 9f), lcol, false);
        return px + need + 12f;
    }

    /**
     * 攻击充能：画在下方小信息栏里（不是胶囊），宽度固定 104、高度 20，永远比胶囊小一圈。
     * MC 的攻击充能会在跳跃/切物品时掉，所以必须和胶囊本体分开，否则一直被撑开。
     */
    private void drawAttackRow(GuiGraphics gg, Font font, Theme th, float x, float y, float w, float h, float alpha) {
        float a = alpha;
        float pad = 8f;
        String label = RenderUtil.elide(font, ProgressTracker.I18nS.tr("dynamicisland.state.cooldown"), w - pad * 2f - 30f);
        int lw = font.width(label);
        gg.drawString(font, label, (int) (x + pad), (int) (y + h / 2f - 4f), RenderUtil.argb(th.textDim, 0.9f * a), false);

        float bx = x + pad + lw + 7f;
        float bw = x + w - pad - bx;
        if (bw > 6f) {
            float p = Anim.clamp(attackCharge, 0f, 1f);
            int col = p >= 0.98f ? th.good : (p >= 0.6f ? th.warn : th.bad);
            RenderUtil.progressBar(gg.pose(), bx, y + h / 2f - 2.5f, bw, 5f, p,
                    RenderUtil.argb(th.textDim, 0.24f * a), RenderUtil.argb(col, 0.95f * a));
        }
    }

    /** 取攻击充能比例，任何异常都当作满充能（不显示） */
    private float attackScale(net.minecraft.world.entity.player.Player p) {
        try {
            if (p == null) return 1f;
            return p.getAttackStrengthScale(0f);
        } catch (Throwable t) {
            return 1f;
        }
    }

    /** 右对齐绘制，可用宽度不足以显示时自动截断；空间太小则整段不画，避免和左边文字叠在一起 */
    private void drawRight(GuiGraphics gg, Font font, String text, float leftLimit, float rightX, float yPos, int color) {
        float avail = rightX - leftLimit;
        if (avail < 22f) return;
        String txt = RenderUtil.elide(font, text, avail);
        gg.drawString(font, txt, (int) (rightX - font.width(txt)), (int) yPos, color, false);
    }

    private void drawNotices(GuiGraphics gg, Font font, Theme th, float cx, float top, float scale) {
        var list = Notifier.visible();
        if (list.isEmpty()) return;
        PoseStack ps = gg.pose();
        float yy = top;
        ps.pushPose();
        ps.translate(cx, yy, 0f);
        ps.scale(scale, scale, 1f);
        float localY = 0f;
        for (Notifier.Notice n : list) {
            Minecraft mc = Minecraft.getInstance();
            String sub = RenderUtil.shorten(n.sub, 22);
            int tw = mc.font.width(RenderUtil.shorten(n.title, 16));
            int sw = mc.font.width(sub);
            float w = Math.max(150f, Math.max(tw, sw) + 46f);
            float h = 30f;
            float a = Anim.clamp(n.inAnim, 0f, 1f) * alpha;
            float slide = (1f - Anim.easeOutCubic(a)) * 10f;
            float x = -w / 2f;
            float y = localY + slide;
            int noticeAccent = Config.colorNotice >= 0 ? Config.colorNotice : n.accent;
            if (Config.shadow) RenderUtil.shadow(ps, x, y, w, h, h * 0.5f, 0x000000, a * 0.8f);
            RenderUtil.roundRect(ps, x, y, w, h, h * 0.5f,
                    RenderUtil.argb(RenderUtil.mix(th.bg, noticeAccent, 0.10f), Config.bgOpacity() * 0.94f * a));

            float px = x + 11f;
            if (!n.icon.isEmpty() && n.icon.getItem() != Items.AIR) {
                gg.renderItem(n.icon, (int) px, (int) (y + h / 2f - 8f));
                px += 24f;
            } else {
                RenderUtil.roundRect(ps, px, y + h / 2f - 4f, 8f, 8f, 4f, RenderUtil.argb(noticeAccent, a));
                px += 18f;
            }
            gg.drawString(font, RenderUtil.shorten(n.title, 16), (int) px, (int) (y + 6f), RenderUtil.argb(th.text, a), false);
            gg.drawString(font, sub, (int) px, (int) (y + 17f), RenderUtil.argb(th.textDim, 0.9f * a), false);
            localY += h + 5f;
        }
        ps.popPose();
    }

    private void updateInfoText(float dt, boolean expanded) {
        if (expanded) { rotateTimer = 0f; return; }
        if (Config.infoMode != Config.InfoMode.ROTATE) {
            infoText = infoByMode(Config.infoMode);
            return;
        }
        rotateTimer += dt;
        if (rotateTimer >= Config.infoRotate) {
            rotateTimer = 0f;
            rotateIndex = (rotateIndex + 1) % INDEX_MODE.length;
        }
        String txt = infoByMode(INDEX_MODE[rotateIndex]);
        infoText = txt == null || txt.isEmpty() ? infoByMode(Config.InfoMode.COORDS) : txt;
    }

    private static final Config.InfoMode[] INDEX_MODE = {
            Config.InfoMode.COORDS, Config.InfoMode.BIOME, Config.InfoMode.TIME, Config.InfoMode.SPEED,
            Config.InfoMode.PLAYTIME, Config.InfoMode.ENTITIES, Config.InfoMode.STRUCTURE
    };

    private static String infoByMode(Config.InfoMode m) {
        String rot = " · " + Metrics.facing;
        switch (m) {
            case PLAYTIME: return formatPlay(Metrics.playSeconds);
            case ENTITIES: return Metrics.entityCount + " 实体 · " + Metrics.particleCount + " 粒子";
            case STRUCTURE: return Metrics.structure.isEmpty() ? "" : Metrics.structure;
            case BIOME: return Metrics.biome + " · " + Metrics.dimension;
            case TIME: return Metrics.timeStr + " · " + Metrics.weather;
            case SPEED: {
                String[] c = Metrics.coords.split(" ");
                return Metrics.speed + (c.length > 1 ? " · Y " + c[1] : "");
            }
            case WEATHER: return Metrics.weather;
            case COORDS:
            default: return Metrics.coords + rot;
        }
    }

    /** 今日游戏时长：不到一小时就按 mm:ss，超过则 h:mm:ss */
    private static String formatPlay(int secs) {
        int h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        String mm = String.format("%02d:%02d", m, s);
        return h > 0 ? h + ":" + mm : mm;
    }

    private IslandStatus idleStatus() { return IslandStatus.idle("", ""); }

    private float idleWidth() {
        Minecraft mc = Minecraft.getInstance();
        float w = 22f;
        if (Config.showFps) w += Math.max(mc.font.width("188"), mc.font.width("FPS")) + 9f;
        if (Config.showGraph && Metrics.historyCount >= 2) w += 55f;
        if (Config.showMem) w += 40f;
        if (Config.showTps) w += mc.font.width("TPS 20 · 999ms") + 8f;
        return Math.max(72f, w);
    }

    private int memColor(Theme th) {
        return Metrics.memRatio < 0.6f ? th.good : (Metrics.memRatio < 0.85f ? th.warn : th.bad);
    }

    private int mixTint(int base, int accent, float t) { return RenderUtil.mix(base, accent, t); }

    private static boolean safeEq(String a, String b) { return a == null ? b == null : a.equals(b); }

    private float deltaTime() {
        long now = System.nanoTime();
        if (lastNano == 0) { lastNano = now; return 1f / 60f; }
        float dt = (now - lastNano) / 1e9f;
        lastNano = now;
        return Anim.clamp(dt, 0.0005f, 0.1f);
    }

    public static ItemStack safe(ItemStack s) { return s == null ? ItemStack.EMPTY : s; }
}
