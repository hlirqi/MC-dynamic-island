package com.dynamicisland.client;

import com.dynamicisland.Config;
import org.joml.Matrix3x2fStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 方块信息弹窗：一个独立于灵动岛的小窗口，位置可在设置里自定义。
 * 只在准星指向方块时出现，移开即淡出，内容 = 方块名 + 硬度 + 所需工具。
 */
public class BlockPanel {

    private static String title = "";
    private static String hardness = "";
    private static String tool = "";
    private static String coord = "";
    private static ItemStack icon = ItemStack.EMPTY;
    private static boolean warnTool = false;
    private static float alpha = 0f;
    private static float visibleTimer = 0f;

    /** 由 Reticle 在准星命中方块时调用 */
    public static void show(BlockState st, String name, String hardnessText, String toolText,
                            boolean needTool, ItemStack iconStack, long x, long y, long z) {
        title = name;
        hardness = hardnessText;
        tool = toolText;
        warnTool = needTool;
        icon = iconStack;
        coord = x + " " + y + " " + z;
        visibleTimer = 0.25f;
    }

    public static void tick(float dt) {
        visibleTimer = Math.max(0f, visibleTimer - dt);
        boolean want = Config.modReticleBlock && visibleTimer > 0f;
        alpha = Anim.smooth(alpha, want ? 1f : 0f, dt, 0.07f);
        if (!want && alpha < 0.01f) alpha = 0f;
    }

    public static boolean active() { return Config.modReticleBlock && alpha > 0.02f; }

    public static void render(GuiGraphicsExtractor gg, float partialTick) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Theme th = Config.theme;
        float a = alpha;

        float scale = Config.scale;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // ---- 面板内容尺寸 ----
        String toolLine = tool.isEmpty() ? hardness : hardness + "  ·  " + tool;
        String head = RenderUtil.elide(font, title, 150f);
        String line = RenderUtil.elide(font, toolLine, 150f);
        float w = 186f;
        boolean showCoord = Config.blockPanelCoord && !coord.isEmpty();
        float h = showCoord ? 54f : 44f;

        // ---- 位置：屏幕中心 + 水平偏移，垂直从顶部往下 ----
        float cx = sw / 2f + Config.blockPanelX;
        float cy = Config.blockPanelY + (1f - a) * -8f; // 淡入时轻微下滑
        float x = cx - w / 2f;
        x = Anim.clamp(x, 4f, Math.max(4f, sw - w - 4f));
        cy = Anim.clamp(cy, 4f, Math.max(4f, sh - h - 4f));

        Matrix3x2fStack ps = gg.pose();
        ps.pushMatrix();
        ps.translate(0f, 0f);
        ps.scale(scale, scale);

        int blockAccent = Config.colorBlock >= 0 ? Config.colorBlock : th.border;
        if (Config.shadow) RenderUtil.shadow(gg, x, cy, w, h, 8f, 0x000000, a * 0.8f);
        RenderUtil.roundRect(gg, x, cy, w, h, 8f,
                RenderUtil.argb(RenderUtil.mix(th.bg, blockAccent, 0.14f), Config.bgOpacity() * 0.95f * a));
        RenderUtil.roundRect(gg, x + 0.6f, cy + 0.6f, w - 1.2f, h - 1.2f, 8f,
                RenderUtil.argb(blockAccent, 0.35f * a));

        float px = x + 10f;
        // 图标（方块对应物品）
        boolean drewIcon = !icon.isEmpty() && icon.getItem() != net.minecraft.world.item.Items.AIR;
        if (drewIcon) {
            gg.item(icon, (int) (px), (int) (cy + 10f));
            px += 20f;
        }

        float titleSpace = x + w - 10f - px;
        gg.text(font, RenderUtil.elide(font, head, titleSpace), (int) px, (int) (cy + 9f),
                RenderUtil.argb(th.text, 0.95f * a), false);
        gg.text(font, RenderUtil.elide(font, line, x + w - 10f - px), (int) px, (int) (cy + 21f),
                RenderUtil.argb(warnTool ? th.bad : th.textDim, 0.92f * a), false);
        if (showCoord) {
            String c = RenderUtil.elide(font, coord, w - 20f);
            gg.text(font, c, (int) (x + 10f), (int) (cy + 34f), RenderUtil.argb(th.textDim, 0.7f * a), false);
        }

        ps.popMatrix();
    }

    /** 把面板挪到指定 GUI 坐标并存进配置（x 记账为相对屏幕中心的偏移） */
    public static void moveTo(int absX, int absY) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        Config.blockPanelX = (int) Anim.clamp(absX - sw / 2f, -2000f, 2000f);
        Config.blockPanelY = (int) Anim.clamp((float) absY, -1000f, 2000f);
        Config.save();
    }

    public static Component titleKey() {
        return Component.translatable("dynamicisland.opt.reticleBlock");
    }
}
