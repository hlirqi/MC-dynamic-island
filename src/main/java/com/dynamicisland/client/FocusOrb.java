package com.dynamicisland.client;

import com.dynamicisland.Config;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 灵动焦点：胶囊左右两侧的圆形小窗，用来承载需要长时间驻留的信息。
 *
 * 形状：直径恒等于胶囊高度的圆；最多两个（左右各一），只有一个时默认落在右侧。
 * 因为是圆的，信息用「外圈进度环 + 中心内容」表达，无需占用胶囊本身。
 *
 * 多个来源同时竞争时按优先级取前两名；左、右各自有独立呼出键，互不干扰。
 * 胶囊展开显示详细内容时，焦点会整体淡出（不跟着一起变大）。
 */
public class FocusOrb {

    public static final int SLOT_RIGHT = 0;
    public static final int SLOT_LEFT = 1;

    public enum Kind { NONE, MUSIC, TRITIUM, DAY, DURABILITY, POTION, HUNGER, AIR,
        DROP, MARK, CURE, XPORB, CLOUD, ENDERMITE, TIMER_A, TIMER_B }

    /**
     * 占位优先级，数字大的优先。氧气最紧急，昼夜只是预告所以最低。
     * 自定义定时器排得很靠前：那是玩家自己明确要盯的东西。
     */
    private static int priority(Kind k) {
        switch (k) {
            case AIR:        return 13;
            case DROP:       return 12;  // 掉落物会永久消失，硬终点最不能错过的
            case TIMER_A:    return 11;  // 玩家自定义的倒计时 / 计时器
            case TIMER_B:    return 11;
            case XPORB:      return 10;  // 经验球同样是永久消失的硬终点，但满地都是
            case MARK:       return 9;   // PVP 里被标记是生死信息
            case CLOUD:      return 8;   // 持续伤害 / 治疗云的剩余时间，战斗里很关键
            case DURABILITY: return 7;
            case HUNGER:     return 6;
            case POTION:     return 5;
            case CURE:       return 4;
            case ENDERMITE:  return 3;   // 末影螨 2 分钟后自然死亡，属情报类
            case MUSIC:      return 2;
            case TRITIUM:    return 2;
            case DAY:        return 1;
            default:         return 0;
        }
    }

    public static class Slot {
        public Kind kind = Kind.NONE;
        public ItemStack icon = ItemStack.EMPTY;
        public String label = "", sub = "", center = "";
        public float progress = 0f;
        public int accent = -1;
        /** >=0 时覆盖中心文字颜色（自定义定时器用事件自己的颜色） */
        public int textColor = -1;
        public ResourceLocation cover = null;  // 专辑封面（仅 TRITIUM 用）
        final Anim alpha = new Anim(0f);   // 0=不可见 1=完全显示
        final Anim travel = new Anim(0f);  // 0=仍贴在胶囊里 1=已飞到焦点位置
    }

    private static final Slot[] slots = new Slot[] { new Slot(), new Slot() };
    private static final Kind[] lastKinds = new Kind[] { Kind.NONE, Kind.NONE };

    private static class Cand {
        Kind kind; ItemStack icon = ItemStack.EMPTY;
        String label = "", sub = "", center = "";
        float progress = 0f; int accent = -1; int pri = 0;
        int textColor = -1;
        ResourceLocation cover = null;
        Cand(Kind k) { kind = k; pri = priority(k); }
    }

    public static Slot slot(int i) { return slots[i]; }

    public static boolean hasContent() {
        for (Slot s : slots) if (s.kind != Kind.NONE && s.alpha.get() > 0.05f) return true;
        return false;
    }

    // ---------- 数据收集 ----------

    private static void collect(List<Cand> out) {
        if (Config.focusMusic && MusicCard.playing && !MusicCard.onIsland()) {
            Cand c = new Cand(Kind.MUSIC);
            c.icon = MusicCard.icon; c.label = MusicCard.title;
            c.sub = MusicCard.sub; c.progress = MusicCard.progress;
            out.add(c);
        }
        if (Config.modTritium && TritiumLink.focusActive()) {
            Cand c = new Cand(Kind.TRITIUM);
            c.label = TritiumLink.title;
            c.sub = TritiumLink.artist.isEmpty() ? TritiumLink.sub
                    : TritiumLink.artist + " · " + TritiumLink.sub;
            c.center = TritiumLink.sub;
            c.progress = TritiumLink.progress;
            if (Config.tritiumCover && TritiumLink.coverReady()) c.cover = TritiumLink.cover;
            out.add(c);
        }
        if (DayClock.focusActive()) {
            Cand c = new Cand(Kind.DAY);
            c.label = DayClock.title; c.sub = DayClock.sub;
            c.center = DayClock.center; c.progress = DayClock.progress;
            out.add(c);
        }
        if (Config.focusDurability && DurabilityWatch.active && !DurabilityWatch.onIsland()) {
            Cand c = new Cand(Kind.DURABILITY);
            c.icon = DurabilityWatch.icon; c.label = DurabilityWatch.label;
            c.sub = DurabilityWatch.sub; c.progress = DurabilityWatch.progress;
            out.add(c);
        }
        if (Config.focusPotion && PotionWatch.active && !PotionWatch.onIsland()) {
            Cand c = new Cand(Kind.POTION);
            c.label = PotionWatch.label; c.sub = PotionWatch.sub;
            c.center = PotionWatch.center; c.progress = PotionWatch.progress;
            c.accent = PotionWatch.accent;
            out.add(c);
        }
        if (Config.focusHunger && VitalsWatch.hunger.active && !VitalsWatch.hungerOnIsland()) {
            Cand c = new Cand(Kind.HUNGER);
            c.label = VitalsWatch.hunger.label; c.sub = VitalsWatch.hunger.sub;
            c.center = VitalsWatch.hunger.center; c.progress = VitalsWatch.hunger.progress;
            out.add(c);
        }
        if (Config.focusDrop && DropWatch.active && !DropWatch.onIsland()) {
            Cand c = new Cand(Kind.DROP);
            c.label = DropWatch.label; c.sub = DropWatch.sub;
            c.center = DropWatch.center; c.progress = DropWatch.progress;
            c.accent = Config.theme.bad;
            out.add(c);
        }
        if (Config.focusXpOrb && XpOrbWatch.active && !XpOrbWatch.onIsland()) {
            Cand c = new Cand(Kind.XPORB);
            c.icon = XpOrbWatch.icon;
            c.label = XpOrbWatch.label; c.sub = XpOrbWatch.sub;
            c.center = XpOrbWatch.center; c.progress = XpOrbWatch.progress;
            out.add(c);
        }
        if (Config.focusMark && MarkWatch.active && !MarkWatch.onIsland()) {
            Cand c = new Cand(Kind.MARK);
            c.label = MarkWatch.label; c.sub = MarkWatch.sub;
            c.center = MarkWatch.center; c.progress = MarkWatch.progress;
            c.accent = Config.theme.warn;
            out.add(c);
        }
        if (Config.focusCloud && CloudWatch.active && !CloudWatch.onIsland()) {
            Cand c = new Cand(Kind.CLOUD);
            c.icon = CloudWatch.icon;
            c.label = CloudWatch.label; c.sub = CloudWatch.sub;
            c.center = CloudWatch.center; c.progress = CloudWatch.progress;
            c.accent = CloudWatch.accent;
            out.add(c);
        }
        if (Config.focusCure && CureWatch.active && !CureWatch.onIsland()) {
            Cand c = new Cand(Kind.CURE);
            c.label = CureWatch.label; c.sub = CureWatch.sub;
            c.center = CureWatch.center; c.progress = CureWatch.progress;
            out.add(c);
        }
        if (Config.focusEndermite && EndermiteWatch.active && !EndermiteWatch.onIsland()) {
            Cand c = new Cand(Kind.ENDERMITE);
            c.icon = EndermiteWatch.icon;
            c.label = EndermiteWatch.label; c.sub = EndermiteWatch.sub;
            c.center = EndermiteWatch.center; c.progress = EndermiteWatch.progress;
            out.add(c);
        }
        if (Config.focusAir && VitalsWatch.air.active && !VitalsWatch.airOnIsland()) {
            Cand c = new Cand(Kind.AIR);
            c.label = VitalsWatch.air.label; c.sub = VitalsWatch.air.sub;
            c.center = VitalsWatch.air.center; c.progress = VitalsWatch.air.progress;
            c.accent = VitalsWatch.air.accent;
            out.add(c);
        }
        // 自定义定时器：TimerCenter 已经把「谁的进哪个槽」算好了（槽 1→TIMER_A，槽 2→TIMER_B），
        // 这里只按标记取数据，不自己排优先级，呼出/收回才不会错人。
        if (Config.modTimer) {
            addTimer(out, Kind.TIMER_A, TimerCenter.bySlot(1));
            addTimer(out, Kind.TIMER_B, TimerCenter.bySlot(2));
        }
    }

    private static void addTimer(List<Cand> out, Kind k, TimerCenter.Event ev) {
        if (ev == null) return;
        Cand c = new Cand(k);
        c.label = ev.name;
        c.sub = TimerCenter.subOf(ev);
        c.center = TimerCenter.centerOf(ev);
        c.progress = ev.progress();
        c.accent = ev.color;
        c.textColor = ev.color;
        out.add(c);
    }

    /** 每帧刷新槽位归属与动画。新增数据源只需在 collect() 里加一项。 */
    public static void update(float dt) {
        List<Cand> cands = new ArrayList<>();
        collect(cands);

        // 优先级降序，同优先级按 Kind 顺序，保证结果稳定不抖
        cands.sort((a, b) -> a.pri != b.pri
                ? Integer.compare(b.pri, a.pri)
                : Integer.compare(a.kind.ordinal(), b.kind.ordinal()));
        while (cands.size() > 2) cands.remove(cands.size() - 1);

        // 先让上一帧就在某槽的来源保持原位，剩下的再填空槽
        Kind[] assign = { Kind.NONE, Kind.NONE };
        boolean[] used = { false, false };
        for (Cand c : cands) {
            for (int i = 0; i < 2; i++) {
                if (!used[i] && lastKinds[i] == c.kind) { assign[i] = c.kind; used[i] = true; break; }
            }
        }
        int pref = Config.focusRightFirst ? SLOT_RIGHT : SLOT_LEFT;
        int[] order = { pref, 1 - pref };
        for (Cand c : cands) {
            boolean placed = false;
            for (Kind k : assign) if (k == c.kind) { placed = true; break; }
            if (placed) continue;
            for (int i : order) {
                if (!used[i]) { assign[i] = c.kind; used[i] = true; break; }
            }
        }

        for (int i = 0; i < 2; i++) {
            Slot s = slots[i];
            Kind k = assign[i];
            if (k == Kind.NONE) {
                s.kind = Kind.NONE; s.icon = ItemStack.EMPTY;
                s.label = ""; s.sub = ""; s.center = ""; s.accent = -1;
                s.textColor = -1; s.cover = null;
                s.alpha.to(0f); s.travel.to(0f);
            } else {
                Cand c = null;
                for (Cand x : cands) if (x.kind == k) { c = x; break; }
                if (c != null) {
                    s.kind = k; s.icon = c.icon; s.label = c.label; s.sub = c.sub;
                    s.center = c.center; s.progress = c.progress; s.accent = c.accent;
                    s.textColor = c.textColor;
                    s.cover = c.cover;
                }
                s.alpha.to(1f); s.travel.to(1f);
            }
            s.alpha.update(dt);
            s.travel.update(dt);
        }
        lastKinds[0] = assign[0];
        lastKinds[1] = assign[1];
    }

    // ---------- 呼出 ----------

    /**
     * 找出正被钉在胶囊上的来源。内容一旦钉住就不再占焦点槽
     * （focusActive 为 false），所以不能只看槽位，否则再按一次解除不了。
     */
    private static Kind pinnedKind() {
        if (Config.focusMusic && MusicCard.pinned()) return Kind.MUSIC;
        if (Config.modTritium && TritiumLink.pinned()) return Kind.TRITIUM;
        if (Config.focusDay && DayClock.pinned()) return Kind.DAY;
        if (Config.focusDurability && DurabilityWatch.pinned()) return Kind.DURABILITY;
        if (Config.focusPotion && PotionWatch.pinned()) return Kind.POTION;
        if (Config.focusHunger && VitalsWatch.hungerPinned()) return Kind.HUNGER;
        if (Config.focusAir && VitalsWatch.airPinned()) return Kind.AIR;
        if (Config.focusDrop && DropWatch.pinned()) return Kind.DROP;
        if (Config.focusXpOrb && XpOrbWatch.pinned()) return Kind.XPORB;
        if (Config.focusMark && MarkWatch.pinned()) return Kind.MARK;
        if (Config.focusCloud && CloudWatch.pinned()) return Kind.CLOUD;
        if (Config.focusCure && CureWatch.pinned()) return Kind.CURE;
        if (Config.focusEndermite && EndermiteWatch.pinned()) return Kind.ENDERMITE;
        if (Config.modTimer && TimerCenter.anyPinned()) return Kind.TIMER_A;
        return Kind.NONE;
    }

    /** 对某个来源执行一次「呼出 / 收回」切换 */
    private static void release(Kind k) {
        switch (k) {
            case MUSIC:      MusicCard.bringBack();         break;
            case TRITIUM:    TritiumLink.bringBack();       break;
            case DAY:        DayClock.bringBack();          break;
            case DURABILITY: DurabilityWatch.bringBack();   break;
            case POTION:     PotionWatch.bringBack();       break;
            case HUNGER:     VitalsWatch.bringBackHunger(); break;
            case AIR:        VitalsWatch.bringBackAir();    break;
            case DROP:       DropWatch.bringBack();         break;
            case XPORB:      XpOrbWatch.bringBack();        break;
            case MARK:       MarkWatch.bringBack();         break;
            case CLOUD:      CloudWatch.bringBack();        break;
            case CURE:       CureWatch.bringBack();         break;
            case ENDERMITE:  EndermiteWatch.bringBack();    break;
            case TIMER_A:    TimerCenter.bringBackSlot(1);  break;
            case TIMER_B:    TimerCenter.bringBackSlot(2);  break;
            default: break;
        }
    }

    /** 把指定一侧焦点的内容收回胶囊；再按一次则退回焦点 */
    public static void bringBack(int slot) {
        if (slot != SLOT_RIGHT && slot != SLOT_LEFT) return;
        // 槽里没内容时，可能它正被钉在胶囊上 —— 这时要解除的是那个
        if (slots[slot].kind == Kind.NONE) {
            Kind pk = pinnedKind();
            if (pk != Kind.NONE) release(pk);
            return;
        }
        release(slots[slot].kind);
    }

    /** 通用键：呼出当前优先级最高的那一侧（只有一个焦点时最顺手） */
    public static void bringBackRecent() {
        int pref = Config.focusRightFirst ? SLOT_RIGHT : SLOT_LEFT;
        if (slots[pref].kind != Kind.NONE) { bringBack(pref); return; }
        int other = 1 - pref;
        if (slots[other].kind != Kind.NONE) { bringBack(other); return; }
        // 两侧焦点都空 —— 但可能有内容正被钉在胶囊上，再按一次就解除它
        Kind pk = pinnedKind();
        if (pk != Kind.NONE) release(pk);
    }

    // ---------- 渲染 ----------

    /**
     * 在胶囊的局部坐标系里绘制（已 translate 到胶囊中心顶部并 scale 过），
     * 因此焦点会跟随胶囊的缩放、位置偏移与脉冲动画。
     *
     * @param fade 外部淡出系数，胶囊展开时传 (1 - grow) 让焦点自动隐去
     */
    public static void render(GuiGraphics gg, PoseStack ps, Font font, Theme th,
                              float w, float h, float alpha, float fade) {
        if (!Config.focusEnabled) return;
        float baseH = Math.min(h, 32f);   // 用收起时的高度做基准，胶囊展开时不会跟着变大
        float r = baseH * 0.5f;
        if (r < 4f) return;
        float cy = h * 0.5f;
        float gap = Config.focusGap;

        for (int i = 0; i < slots.length; i++) {
            Slot s = slots[i];
            float a = Anim.clamp(s.alpha.get(), 0f, 1f) * alpha * Anim.clamp(fade, 0f, 1f);
            if (a <= 0.02f) continue;

            float t = Anim.easeOutCubic(Anim.clamp(s.travel.get(), 0f, 1f));
            float sign = (i == SLOT_RIGHT) ? 1f : -1f;
            float fromX = sign * w * 0.30f;              // 起点：还贴在胶囊内
            float toX = sign * (w * 0.5f + gap + r);     // 终点：胶囊外侧
            float cx = Anim.lerp(fromX, toX, t);
            drawOrb(gg, ps, font, th, cx, cy, r, s, a);
        }
    }

    private static void drawOrb(GuiGraphics gg, PoseStack ps, Font font, Theme th,
                                float cx, float cy, float r, Slot s, float a) {
        float d = r * 2f;
        int accent = s.accent < 0 ? Config.accentOf(Config.colorFocus, th) : s.accent;

        if (Config.shadow) RenderUtil.shadow(ps, cx - r, cy - r, d, d, r, 0x000000, a * 0.55f);

        // 圆形底：正方形把圆角拉到半径即为圆
        RenderUtil.roundRect(ps, cx - r, cy - r, d, d, r,
                RenderUtil.argb(th.bg, Config.bgOpacity() * a));
        RenderUtil.roundRect(ps, cx - r + 0.8f, cy - r + 0.8f, d - 1.6f, d - 1.6f, r,
                RenderUtil.argb(th.bg, Config.bgOpacity() * 0.5f * a));

        // 进度环：底环 + 进度
        float ringR = r - Math.max(1.5f, r * 0.16f);
        float thk = Math.max(1.2f, r * 0.15f);
        RenderUtil.ringTrack(ps, cx, cy, ringR, thk, RenderUtil.argb(th.textDim, 0.28f * a));
        RenderUtil.ring(ps, cx, cy, ringR, thk, Anim.clamp(s.progress, 0f, 1f),
                RenderUtil.argb(accent, 0.95f * a));

        // 中心：专辑封面 > 物品图标 > 短文本
        if (s.cover != null) {
            // 裁成圆来贴合焦点形状；内缩一点，别压到外圈的进度环
            float cr = Math.max(3f, (d - Math.max(5f, r * 0.55f)) * 0.5f);
            try {
                RenderUtil.roundImage(ps, s.cover, cx, cy, cr, a);
            } catch (Throwable ignored) { }
        } else if (!s.icon.isEmpty()) {
            float is = Math.max(1f, r / 16f);            // 图标随圆放大，避免小圆挤、大圆空
            ps.pushPose();
            ps.translate(cx, cy, 0f);
            ps.scale(is, is, 1f);
            gg.renderItem(s.icon, -8, -8);
            ps.popPose();
        } else if (!s.center.isEmpty()) {
            int col = s.textColor >= 0 ? s.textColor : th.text;
            gg.drawCenteredString(font, s.center, (int) cx, (int) (cy - 4f),
                    RenderUtil.argb(col, 0.95f * a));
        }
    }

    public static void reset() {
        for (Slot s : slots) {
            s.kind = Kind.NONE;
            s.icon = ItemStack.EMPTY;
            s.label = ""; s.sub = ""; s.center = ""; s.accent = -1;
            s.textColor = -1; s.cover = null;
            s.alpha.snap(0f); s.travel.snap(0f);
        }
        lastKinds[0] = Kind.NONE;
        lastKinds[1] = Kind.NONE;
    }
}
