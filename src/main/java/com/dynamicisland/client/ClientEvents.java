package com.dynamicisland.client;

import com.dynamicisland.Config;
import com.dynamicisland.command.IslandCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END) return;
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
        FocusOrb.update(1f / 20f);
        PetWatch.tick(1f / 20f, mc);

        if (++secondCounter >= 20) { secondCounter = 0; Notifier.check(mc); Notifier.checkSystem(); }

        handleKeys(mc);
    }

    @SubscribeEvent
    public void onRender(RenderGuiEvent.Post e) {
        IslandRenderer.INSTANCE.render(e.getGuiGraphics(), e.getPartialTick());
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
    public void onSound(net.minecraftforge.client.event.sound.PlaySoundEvent e) {
        String name = null;
        try { name = e.getName(); } catch (Throwable ignored) { }
        if (name == null) {
            try {
                var loc = e.getSound() == null ? null : e.getSound().getLocation();
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

    private void handleKeys(Minecraft mc) {
        boolean t = KeyBinds.TOGGLE.isDown();
        if (t && !prevToggle) {
            IslandRenderer.INSTANCE.visible = !IslandRenderer.INSTANCE.visible;
            Config.enabled = IslandRenderer.INSTANCE.visible;
            Config.save();
        }
        prevToggle = t;

        boolean x = KeyBinds.EXPAND.isDown();
        if (x && !prevExpand) IslandRenderer.INSTANCE.forceExpand = !IslandRenderer.INSTANCE.forceExpand;
        prevExpand = x;

        boolean th = KeyBinds.THEME.isDown();
        if (th && !prevTheme) {
            Config.theme = Theme.next(Config.theme);
            Config.save();
        }
        prevTheme = th;

        boolean dr = KeyBinds.DEATH_RECALL.isDown();
        if (dr && !prevDeathRecall && mc.screen == null && mc.level != null) DeathMark.toggle(mc.player, mc.level);
        prevDeathRecall = dr;

        boolean pa = KeyBinds.PANEL.isDown();
        if (pa && !prevPanel && mc.screen == null) mc.setScreen(new IslandScreen(null));
        prevPanel = pa;

        boolean p = KeyBinds.POS.isDown();
        if (p && !prevPos && mc.screen == null) mc.setScreen(new DragScreen());
        prevPos = p;

        boolean f = KeyBinds.FOCUS.isDown();
        if (f && !prevFocus && mc.screen == null) FocusOrb.bringBackRecent();
        prevFocus = f;

        boolean fl = KeyBinds.FOCUS_LEFT.isDown();
        if (fl && !prevFocusLeft && mc.screen == null) FocusOrb.bringBack(FocusOrb.SLOT_LEFT);
        prevFocusLeft = fl;

        boolean fr = KeyBinds.FOCUS_RIGHT.isDown();
        if (fr && !prevFocusRight && mc.screen == null) FocusOrb.bringBack(FocusOrb.SLOT_RIGHT);
        prevFocusRight = fr;
    }
}
