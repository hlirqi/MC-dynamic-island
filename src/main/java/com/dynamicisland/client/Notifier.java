package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** 通知中心：背包满 / 耐久告急 / 效果结束 / 被 @ / 进度达成 */
public class Notifier {

    /** 紧急程度：决定是否出声 */
    public enum Level { NORMAL, URGENT }

    public static class Notice {
        public String title, sub;
        public ItemStack icon = ItemStack.EMPTY;
        public int accent;
        public float age = 0f, life = 3.5f;
        public float inAnim = 0f;
        public boolean dying = false;
        public Level level = Level.NORMAL;
        public Notice(String t, String s, ItemStack i, int a) { title = t; sub = s; icon = i; accent = a; }
    }

    private static final Deque<Notice> list = new ArrayDeque<>();
    private static final Map<String, Long> dedup = new HashMap<>();
    /** 历史回看：最近若干条，先进先出 */
    private static final Deque<Notice> history = new ArrayDeque<>();
    private static final int HISTORY_MAX = 12;
    private static int noticeSeq = 0;

    public static List<Notice> visible() { return new ArrayList<>(list); }

    /** 最近的通知（新的在前），供历史回看翻页 */
    public static List<Notice> history() {
        List<Notice> out = new ArrayList<>(history);
        Collections.reverse(out);
        return out;
    }

    public static void push(String title, String sub, ItemStack icon, int accent, String dedupKey, int cooldownSec) {
        push(title, sub, icon, accent, dedupKey, cooldownSec, Level.NORMAL);
    }

    public static void push(String title, String sub, ItemStack icon, int accent,
                            String dedupKey, int cooldownSec, Level level) {
        if (!Config.modNotice) return;
        if (dedupKey != null) {
            Long last = dedup.get(dedupKey);
            long now = System.currentTimeMillis();
            if (last != null && now - last < cooldownSec * 1000L) return;
            dedup.put(dedupKey, now);
        }
        while (list.size() >= 3) list.removeFirst();
        Notice n = new Notice(title, sub, icon, accent);
        n.life = Config.noticeTime;
        n.level = level;
        list.addLast(n);
        noticeSeq++;

        // 历史留档（存副本，避免随显示动画被改）
        if (Config.noticeHistory) {
            Notice h = new Notice(title, sub, icon, accent);
            h.level = level;
            h.inAnim = 1f;
            history.addLast(h);
            while (history.size() > HISTORY_MAX) history.removeFirst();
        }

        // 声音分级：0=不出声 1=仅紧急 2=全部
        if (Config.noticeSound >= 2 || (Config.noticeSound == 1 && level == Level.URGENT)) playSound(level);
    }

    private static void playSound(Level level) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            net.minecraft.sounds.SoundEvent ev = level == Level.URGENT
                    ? net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value()
                    : net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value();
            mc.player.playSound(ev, 0.6f, level == Level.URGENT ? 1.6f : 1.2f);
        } catch (Throwable ignored) { }
    }

    public static void tick(float dt) {
        Iterator<Notice> it = list.iterator();
        while (it.hasNext()) {
            Notice n = it.next();
            if (!n.dying) {
                n.inAnim = Math.min(1f, n.inAnim + dt * 6f);
                n.age += dt;
                if (n.age >= n.life) n.dying = true;
            } else {
                n.inAnim -= dt * 5f;
                if (n.inAnim <= 0f) it.remove();
            }
        }
    }

    /** 由 ClientTickEvent 驱动的周期性检查 */
    public static void check(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;

        if (Config.noticeFull && p.getInventory().getFreeSlot() == -1) {
            push(ProgressTracker.I18nS.tr("dynamicisland.notice.fullinv"),
                    ProgressTracker.I18nS.tr("dynamicisland.notice.fullinv.sub"),
                    ItemStack.EMPTY, Config.theme.warn, "inv", 30);
        }

        if (Config.noticeDurability) for (ItemStack s : new ItemStack[]{
                p.getMainHandItem(), p.getOffhandItem(),
                p.getItemBySlot(EquipmentSlot.HEAD), p.getItemBySlot(EquipmentSlot.CHEST),
                p.getItemBySlot(EquipmentSlot.LEGS), p.getItemBySlot(EquipmentSlot.FEET)}) {
            if (s.isEmpty() || s.getMaxDamage() <= 0) continue;
            int left = s.getMaxDamage() - s.getDamageValue();
            if (left > 0 && left <= s.getMaxDamage() * 0.1f) {
                String key = "dur:" + s.getItem() + ":" + left;
                push(Component.translatable("dynamicisland.notice.durability",
                                RenderUtil.shorten(s.getHoverName().getString(), 10)).getString(),
                        Component.translatable("dynamicisland.notice.durability.sub", left).getString(),
                        s, Config.theme.bad, key, 60);
            }
        }

        if (Config.noticeEffect) for (MobEffectInstance e : p.getActiveEffects()) {
            if (e.getDuration() > 0 && e.getDuration() <= 200) {
                push(ProgressTracker.I18nS.tr("dynamicisland.notice.effect"),
                        e.getEffect().value().getDisplayName().getString() + " " + (e.getDuration() / 20) + "s",
                        ItemStack.EMPTY, Config.theme.accent, "eff:" + e.getEffect(), 25);
            }
        }
    }

    public static void onAdvancement(Component title, Component desc) {
        if (!Config.noticeAdvancement) return;
        push(ProgressTracker.I18nS.tr("dynamicisland.notice.advancement"),
                title.getString(), ItemStack.EMPTY, Config.theme.good, "adv", 0);
    }

    public static void onMention(String who) {
        push(ProgressTracker.I18nS.tr("dynamicisland.notice.mention"),
                who, ItemStack.EMPTY, Config.theme.accent, null, 0);
    }

    // ---------- 新增通知源 ----------

    /** 系统告警：内存过高 / 帧率骤降 / TPS 崩溃 */
    public static void checkSystem() {
        if (!Config.noticeSystem) return;
        if (Metrics.memRatio >= 0.9f) {
            push(ProgressTracker.I18nS.tr("dynamicisland.notice.mem"),
                    Metrics.memUsedMb + " / " + Metrics.memMaxMb + " MB",
                    ItemStack.EMPTY, Config.theme.bad, "sys:mem", 60, Level.URGENT);
        }
        if (Metrics.fps > 0 && Metrics.fps < 20) {
            push(ProgressTracker.I18nS.tr("dynamicisland.notice.fps"),
                    Metrics.fps + " FPS", ItemStack.EMPTY, Config.theme.warn, "sys:fps", 45);
        }
        if (Metrics.tps > 0 && Metrics.tps < 12) {
            push(ProgressTracker.I18nS.tr("dynamicisland.notice.tps"),
                    "TPS " + Metrics.tps, ItemStack.EMPTY, Config.theme.bad, "sys:tps", 60, Level.URGENT);
        }
    }

    /** 玩家进出服务器（多人场景） */
    public static void onPlayerJoin(String name) {
        if (!Config.noticePlayer) return;
        push(ProgressTracker.I18nS.tr("dynamicisland.notice.join"), name,
                ItemStack.EMPTY, Config.theme.accent, "join:" + name, 10);
    }

    public static void onPlayerLeave(String name) {
        if (!Config.noticePlayer) return;
        push(ProgressTracker.I18nS.tr("dynamicisland.notice.leave"), name,
                ItemStack.EMPTY, Config.theme.textDim, "leave:" + name, 10);
    }

    /** 宠物死亡 / 拴绳断裂 */
    public static void onPetDeath(String name) {
        if (!Config.noticePet) return;
        push(ProgressTracker.I18nS.tr("dynamicisland.notice.petdeath"), name,
                ItemStack.EMPTY, Config.theme.bad, "pet:" + name, 30, Level.URGENT);
    }

    public static void onLeashBroken() {
        if (!Config.noticePet) return;
        push(ProgressTracker.I18nS.tr("dynamicisland.notice.leash"),
                ProgressTracker.I18nS.tr("dynamicisland.notice.leash.sub"),
                ItemStack.EMPTY, Config.theme.warn, "leash", 20);
    }

    public static void clear() { list.clear(); }
    public static int seq() { return noticeSeq; }
}
