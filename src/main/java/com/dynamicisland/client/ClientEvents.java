package com.dynamicisland.client;

import com.dynamicisland.Config;
import com.dynamicisland.command.IslandCommand;
import com.dynamicisland.mixin.AdvancementToastAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ToastAddEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class ClientEvents {

    public static final ClientEvents INSTANCE = new ClientEvents();
    private boolean prevToggle = false, prevExpand = false, prevTheme = false, prevPos = false;
    private boolean prevPanel = false, prevDeathRecall = false, prevFocus = false;
    private boolean prevFocusLeft = false, prevFocusRight = false;
    private boolean wasDead = false;
    private int secondCounter = 0;

    public void init() {
        IslandRenderer.INSTANCE.reset();
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Pre e) {
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Metrics.tick();
        Notifier.tick(1f / 20f);

        // ---- 新功能模块 ----
        BuildTracker.tick(1f / 20f, mc.player);
        Reticle.tick(1f / 20f, mc);
        DeathMark.tick(mc.player, mc.level);
        XpWatch.tick(1f / 20f, mc.player);
        CooldownWheel.update(mc.player, 0f);
        EffectQueue.update(mc.player);
        ItemPop.tick(1f / 20f, mc.player);
        MusicCard.tick(1f / 20f, mc);
        TritiumLink.tick(1f / 20f, mc);
        DayClock.tick(1f / 20f, mc);
        DepthWatch.tick(1f / 20f, mc);
        BlastWatch.tick(mc);
        FallWatch.tick(mc);
        RideWatch.tick(mc);
        TradeWatch.tick(mc);
        DurabilityWatch.tick(1f / 20f, mc.player);
        PotionWatch.tick(1f / 20f, mc.player);
        VitalsWatch.tick(1f / 20f, mc.player);
        // 玩家刚死：给掉落物回收打点，之后只认这一带掉出来的东西
        if (mc.player != null) {
            boolean dead = mc.player.isDeadOrDying();
            if (dead && !wasDead) DropWatch.markDeath(mc.player.blockPosition());
            wasDead = dead;
        } else {
            wasDead = false;
        }
        DropWatch.tick(1f / 20f, mc);
        MarkWatch.tick(1f / 20f, mc.player);
        CureWatch.tick(1f / 20f, mc);
        XpOrbWatch.tick(1f / 20f, mc);
        CloudWatch.tick(1f / 20f, mc);
        EndermiteWatch.tick(1f / 20f, mc);
        FocusOrb.update(1f / 20f);
        PetWatch.tick(1f / 20f, mc);

        if (++secondCounter >= 20) { secondCounter = 0; Notifier.check(mc); Notifier.checkSystem(); }

        handleKeys(mc);
    }

    @SubscribeEvent
    public void onRender(RenderGuiEvent.Post e) {
        IslandRenderer.INSTANCE.render(e.getGuiGraphics(), e.getPartialTick().getGameTimeDeltaPartialTick(true));
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        if (!Config.modNotice || !Config.noticeMention) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String name = mc.player.getName().getString();
        if (name == null || name.isEmpty()) return;
        String text = chatText(e);
        if (text != null && text.contains(name)) Notifier.onMention("@ " + name);
    }

    /** 成就弹窗 → 灵动岛通知（26.x 起用 NeoForge 的 ToastAddEvent，不再靠 mixin 拦截） */
    @SubscribeEvent
    public void onToast(ToastAddEvent e) {
        if (!Config.modNotice || !Config.noticeAdvancement) return;
        if (!(e.getToast() instanceof AdvancementToast)) return;
        try {
            AdvancementHolder a = ((AdvancementToastAccessor) e.getToast()).dynamicisland$getAdvancement();
            if (a != null) {
                Component title = a.value().display().map(d -> d.getTitle()).orElse(Component.empty());
                Notifier.onAdvancement(title, Component.empty());
            }
        } catch (Throwable ignored) { }
    }

    @SubscribeEvent
    public void onCommands(RegisterClientCommandsEvent e) {
        IslandCommand.register(e.getDispatcher());
    }

    /** 玩家进出服务器：从游戏消息里认，避免依赖服务端插件 */
    @SubscribeEvent
    public void onGameMessage(ClientChatReceivedEvent e) {
        if (!Config.noticePlayer) return;
        String raw = chatText(e);
        if (raw == null) return;
        String name = matchJoinLeave(raw);
        if (name == null) return;
        if (lastJoin) Notifier.onPlayerJoin(name); else Notifier.onPlayerLeave(name);
    }

    /** 上一条进服消息是「加入」还是「离开」，由 matchJoinLeave 写入 */
    private static boolean lastJoin = false;

    /** 匹配常见进服 / 离服句式，取不出名字就不打扰 */
    private static String matchJoinLeave(String text) {
        try {
            String t = text.trim();
            if (t.isEmpty()) return null;
            // 形如 "xxx joined the game" / "xxx left the game" / 中文服务器 "xxx 加入了游戏"
            boolean isJoin = t.contains("joined the game") || t.contains("加入了游戏")
                    || t.contains("加入游戏");
            boolean isLeave = t.contains("left the game") || t.contains("离开了游戏")
                    || t.contains("离开游戏");
            if (!isJoin && !isLeave) return null;
            lastJoin = isJoin;
            // 名字取第一个空白之前的片段，并去掉可能的修饰
            String name = t.split("\\s+")[0];
            if (name.isEmpty() || name.length() > 24) return null;
            return name;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 声音播放：唱片 / 背景音乐 / 洞穴环境音都从这里进来 */
    @SubscribeEvent
    public void onSound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent e) {
        String name = null;
        try { name = e.getName(); } catch (Throwable ignored) { }
        if (name == null) {
            try {
                var loc = e.getSound() == null ? null : e.getSound().getIdentifier();
                name = loc == null ? null : loc.toString();
            } catch (Throwable ignored) { }
        }
        if (name == null) return;
        MusicCard.onSound(name);
        DepthWatch.onSound(name);
    }

    /** 兼容不同 Forge 版本的字段名差异 */
    private static String chatText(ClientChatReceivedEvent e) {
        try {
            Object c = e.getClass().getMethod("getMessage").invoke(e);
            if (c instanceof Component comp) return comp.getString();
        } catch (Throwable ignored) { }
        try {
            Object c = e.getClass().getMethod("getContent").invoke(e);
            if (c instanceof Component comp) return comp.getString();
        } catch (Throwable ignored) { }
        return null;
    }

    /** 按键在注册事件之前是 null，统一走这里避免 NPE */
    private static boolean kb(net.minecraft.client.KeyMapping km) {
        return km != null && km.isDown();
    }

    private void handleKeys(Minecraft mc) {
        boolean t = kb(KeyBinds.TOGGLE);
        if (t && !prevToggle) {
            IslandRenderer.INSTANCE.visible = !IslandRenderer.INSTANCE.visible;
            Config.enabled = IslandRenderer.INSTANCE.visible;
            Config.save();
        }
        prevToggle = t;

        boolean x = kb(KeyBinds.EXPAND);
        if (x && !prevExpand) IslandRenderer.INSTANCE.forceExpand = !IslandRenderer.INSTANCE.forceExpand;
        prevExpand = x;

        boolean th = kb(KeyBinds.THEME);
        if (th && !prevTheme) {
            Config.theme = Theme.next(Config.theme);
            Config.save();
        }
        prevTheme = th;

        boolean dr = kb(KeyBinds.DEATH_RECALL);
        if (dr && !prevDeathRecall && mc.gui.screen() == null && mc.level != null) DeathMark.toggle(mc.player, mc.level);
        prevDeathRecall = dr;

        boolean pa = kb(KeyBinds.PANEL);
        if (pa && !prevPanel && mc.gui.screen() == null) mc.gui.setScreen(new IslandScreen(null));
        prevPanel = pa;

        boolean p = kb(KeyBinds.POS);
        if (p && !prevPos && mc.gui.screen() == null) mc.gui.setScreen(new DragScreen());
        prevPos = p;

        boolean f = kb(KeyBinds.FOCUS);
        if (f && !prevFocus && mc.gui.screen() == null) FocusOrb.bringBackRecent();
        prevFocus = f;

        boolean fl = kb(KeyBinds.FOCUS_LEFT);
        if (fl && !prevFocusLeft && mc.gui.screen() == null) FocusOrb.bringBack(FocusOrb.SLOT_LEFT);
        prevFocusLeft = fl;

        boolean fr = kb(KeyBinds.FOCUS_RIGHT);
        if (fr && !prevFocusRight && mc.gui.screen() == null) FocusOrb.bringBack(FocusOrb.SLOT_RIGHT);
        prevFocusRight = fr;
    }
}
