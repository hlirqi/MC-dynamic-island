package com.dynamicisland.client;

import com.dynamicisland.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义定时器 / 倒计时中枢。
 *
 * 玩家在「功能中心」里添加若干条事件，每条可以单独启用、禁用、删除；
 * 启用后按现有的**两段式**流程呈现：先在胶囊上提示几秒（名称 + 剩余时间），
 * 随后并入灵动焦点用圆环持续显示，圆环与中心时间的颜色都用事件自己的颜色。
 * 到达目标时间时自动停止并推一条通知。
 *
 * 两种类型：
 * <ul>
 *   <li><b>倒计时</b> countdown=true —— 从设定时长往下数，圆环由满到空（由多至少）</li>
 *   <li><b>计时器</b> countdown=false —— 从 0 往上数到「参考时长」，圆环由空到满（由少到多）</li>
 * </ul>
 *
 * 计时用游戏 tick 累加（20 tick = 1 秒），因此暂停游戏时计时也会暂停，符合游戏内直觉。
 * 定义（名称/描述/类型/时长/颜色）会落盘到 <code>config/dynamicisland_timers.json</code>；
 * 运行时状态标了 <code>transient</code>，不落盘 —— 重开游戏后所有定时器都是停止状态。
 */
public class TimerCenter {

    /** 完成后在胶囊/焦点上继续展示「已完成」的秒数 */
    private static final float DONE_HOLD = 8f;
    /** 兜底上限：24 小时 */
    public static final int MAX_SECONDS = 86400;

    // ================= 数据模型 =================

    public static class Event {
        public int id;
        public String name = "";
        public String desc = "";
        /** true=倒计时（由多至少） false=计时器（由少到多） */
        public boolean countdown = true;
        /** 目标时长 / 参考时长（秒） */
        public int seconds = 60;
        /** 0xRRGGBB；胶囊与焦点中心的时间文字、圆环、通知都用它 */
        public int color = 0x40C8E0;

        // ---- 运行时状态（不落盘） ----
        public transient boolean enabled = false;
        public transient boolean done = false;
        public transient int elapsedTicks = 0;
        public transient float doneHold = 0f;
        public transient float islandTimer = 0f;
        public transient boolean pinned = false;
        /** 本帧占用的焦点槽：0=无 1=A 2=B */
        public transient int slot = 0;

        public int targetTicks() { return Math.max(1, seconds) * 20; }

        /** 是否还应该出现在胶囊 / 焦点上（运行中，或刚完成还在展示窗口内） */
        public boolean visible() { return enabled || doneHold > 0f; }

        /** 是否正被胶囊占用（上台预告 或 被手动钉住） */
        public boolean onIsland() { return pinned || islandTimer > 0f; }

        /** 焦点圆环进度：倒计时 1→0，计时器 0→1 */
        public float progress() {
            float t = targetTicks();
            float e = Math.min(elapsedTicks, t);
            return countdown ? Anim.clamp(1f - e / t, 0f, 1f) : Anim.clamp(e / t, 0f, 1f);
        }

        public int remainingSecs() { return Math.max(0, (targetTicks() - elapsedTicks) / 20); }

        public int elapsedSecs() { return Math.min(seconds, elapsedTicks / 20); }

        /** 越快结束越靠前（焦点槽只有两个，先保最紧急的） */
        public int urgency() { return Math.max(0, targetTicks() - elapsedTicks); }

        public void bringBack() {
            if (!visible()) return;
            pinned = !pinned;
            if (pinned) islandTimer = 0f;
        }
    }

    private static final List<Event> events = new ArrayList<>();
    private static int nextId = 1;
    private static boolean loaded = false;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ItemStack ICON = new ItemStack(Items.CLOCK);

    // ================= 存取 =================

    public static List<Event> events() { return events; }

    public static Event byId(int id) {
        for (Event e : events) if (e.id == id) return e;
        return null;
    }

    /** 供界面调用的增删 */
    public static Event create(String name, String desc, boolean countdown, int seconds, int color) {
        Event e = new Event();
        e.id = nextId++;
        e.name = name == null || name.trim().isEmpty() ? tr("dynamicisland.timer.unnamed") : name.trim();
        e.desc = desc == null ? "" : desc.trim();
        e.countdown = countdown;
        e.seconds = Math.max(1, Math.min(MAX_SECONDS, seconds));
        e.color = color & 0xFFFFFF;
        events.add(e);
        assignSlots();
        save();
        return e;
    }

    public static void remove(Event e) {
        events.remove(e);
        assignSlots();
        save();
    }

    /** 启用：从头开始运行（重置进度并上台提示一次） */
    public static void start(Event e) {
        if (e == null) return;
        e.enabled = true;
        e.done = false;
        e.doneHold = 0f;
        e.elapsedTicks = 0;
        e.pinned = false;
        e.islandTimer = Config.focusHold;
        assignSlots();
    }

    /** 禁用：停止并把当前进度留在界面上，下次启用会从 0 重来 */
    public static void stop(Event e) {
        if (e == null) return;
        e.enabled = false;
        e.done = false;
        e.doneHold = 0f;
        e.pinned = false;
        e.islandTimer = 0f;
        assignSlots();
    }

    // ================= 每 tick 推进 =================

    public static void update(float dt) {
        if (!loaded) load();
        if (events.isEmpty()) return;

        for (Event e : events) {
            if (e.enabled) {
                e.elapsedTicks++;
                if (e.elapsedTicks >= e.targetTicks()) {
                    e.elapsedTicks = e.targetTicks();
                    e.enabled = false;
                    e.done = true;
                    e.doneHold = DONE_HOLD;
                    e.islandTimer = Config.focusHold;   // 完成时再上台提示一次
                    notifyDone(e);
                }
            } else if (e.done && e.doneHold > 0f) {
                e.doneHold -= dt;
            }
            if (e.islandTimer > 0f) e.islandTimer -= dt;
        }
        assignSlots();
    }

    private static void notifyDone(Event e) {
        if (!Config.timerNotify) return;
        String sub = e.desc.isEmpty() ? tr("dynamicisland.timer.done") : e.desc;
        Notifier.push(e.name, sub, ICON, e.color, "timer:" + e.id, 0, Notifier.Level.URGENT);
    }

    // ================= 焦点槽归属 =================

    /**
     * 焦点一共只有两个槽。这里每 tick 重算一次：把「还该显示且没占着胶囊」的事件
     * 按紧急度排序，前两名分别标记为槽 1 / 槽 2，FocusOrb 直接读这个标记。
     * 这样 collect() 不需要自己维护 A/B 的对应关系，呼出 / 收回也不会错人。
     */
    private static void assignSlots() {
        for (Event e : events) e.slot = 0;
        List<Event> pend = new ArrayList<>();
        for (Event e : events) if (e.visible() && !e.onIsland()) pend.add(e);
        pend.sort((a, b) -> Integer.compare(a.urgency(), b.urgency()));
        for (int i = 0; i < pend.size() && i < 2; i++) pend.get(i).slot = i + 1;
    }

    /** 槽位对应的事件（FocusOrb.collect 用） */
    public static Event bySlot(int slot) {
        for (Event e : events) if (e.slot == slot) return e;
        return null;
    }

    /** 呼出 / 收回：把当前占着该焦点槽的事件拉回胶囊 */
    public static void bringBackSlot(int slot) {
        Event e = bySlot(slot);
        if (e == null) e = firstPinned();
        if (e == null) e = mostUrgent();
        if (e != null) e.bringBack();
    }

    /** 通用呼出键：优先解除已钉住的，否则钉住最紧急的那条 */
    public static void bringBackAny() {
        Event e = firstPinned();
        if (e == null) e = mostUrgent();
        if (e != null) e.bringBack();
    }

    private static Event firstPinned() {
        for (Event e : events) if (e.pinned) return e;
        return null;
    }

    private static Event mostUrgent() {
        Event best = null;
        for (Event e : events) {
            if (!e.visible()) continue;
            if (best == null || e.urgency() < best.urgency()) best = e;
        }
        return best;
    }

    public static boolean anyPinned() {
        for (Event e : events) if (e.pinned) return true;
        return false;
    }

    /** 当前应该显示在胶囊上的事件（优先钉住的，其次还在上台窗口内的） */
    public static Event islandEvent() {
        Event p = firstPinned();
        if (p != null) return p;
        Event best = null;
        for (Event e : events) {
            if (e.islandTimer <= 0f) continue;
            if (best == null || e.islandTimer > best.islandTimer) best = e;
        }
        return best;
    }

    // ================= 文本 =================

    /** 焦点圆环中心的短时间：60 秒内直接给秒数，1 小时内 mm:ss，再长 h:mm */
    public static String orbClock(int secs) {
        if (secs >= 3600) return (secs / 3600) + ":" + String.format("%02d", (secs % 3600) / 60);
        if (secs >= 60) return (secs / 60) + ":" + String.format("%02d", secs % 60);
        return String.valueOf(Math.max(0, secs));
    }

    /** 完整时间：h:mm:ss / m:ss */
    public static String fullClock(int secs) {
        int h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return h > 0 ? h + ":" + String.format("%02d:%02d", m, s) : m + ":" + String.format("%02d", s);
    }

    /** 焦点中心显示的时间：倒计时给剩余，计时器给已用 */
    public static String centerOf(Event e) {
        return orbClock(e.countdown ? e.remainingSecs() : e.elapsedSecs());
    }

    /** 胶囊副标题 */
    public static String subOf(Event e) {
        if (e.done || e.doneHold > 0f) return tr("dynamicisland.timer.done");
        if (e.countdown) return tr("dynamicisland.timer.sub.countdown", fullClock(e.remainingSecs()));
        return tr("dynamicisland.timer.sub.timer", fullClock(e.elapsedSecs()), fullClock(e.seconds));
    }

    // ================= 落盘 =================

    private static class Data {
        int version = 1;
        int nextId = 1;
        List<Event> events = new ArrayList<>();
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("dynamicisland_timers.json");
    }

    public static void load() {
        loaded = true;
        events.clear();
        nextId = 1;
        try {
            Path p = file();
            if (!Files.exists(p)) return;
            Data d = GSON.fromJson(new String(Files.readAllBytes(p), StandardCharsets.UTF_8), Data.class);
            if (d == null || d.events == null) return;
            for (Event e : d.events) {
                if (e == null) continue;
                e.enabled = false; e.done = false; e.elapsedTicks = 0;
                e.doneHold = 0f; e.islandTimer = 0f; e.pinned = false; e.slot = 0;
                e.seconds = Math.max(1, Math.min(MAX_SECONDS, e.seconds));
                e.name = e.name == null ? "" : e.name;
                e.desc = e.desc == null ? "" : e.desc;
                e.color &= 0xFFFFFF;
                events.add(e);
            }
            nextId = Math.max(d.nextId, 1);
            for (Event e : events) if (e.id >= nextId) nextId = e.id + 1;
        } catch (Throwable ignored) {
            // 配置损坏不该让游戏起不来：直接当作没有事件
            events.clear();
            nextId = 1;
        }
        assignSlots();
    }

    public static void save() {
        try {
            Data d = new Data();
            d.nextId = nextId;
            d.events = new ArrayList<>(events);
            Path p = file();
            Files.createDirectories(p.getParent());
            Files.write(p, GSON.toJson(d).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) { }
    }

    /** 只在第一次真正读盘；界面反复打开也不会把正在跑的定时器重置掉 */
    public static void ensureLoaded() {
        if (!loaded) load();
    }

    public static void reload() {
        loaded = false;
        load();
    }

    // ================= 杂项 =================

    /** 界面用：把一条事件重置回「未运行」并落盘 */
    public static void resetAll() {
        for (Event e : events) {
            e.enabled = false; e.done = false; e.elapsedTicks = 0;
            e.doneHold = 0f; e.islandTimer = 0f; e.pinned = false; e.slot = 0;
        }
    }

    /** 状态文案 + 颜色（界面列表用） */
    public static String stateText(Event e) {
        if (e.enabled) return tr("dynamicisland.timer.state.running");
        if (e.done || e.doneHold > 0f) return tr("dynamicisland.timer.state.done");
        if (e.elapsedTicks > 0) return tr("dynamicisland.timer.state.paused");
        return tr("dynamicisland.timer.state.idle");
    }

    private static String tr(String key, Object... args) {
        try { return Component.translatable(key, args).getString(); } catch (Throwable t) { return key; }
    }
}
