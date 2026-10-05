package com.dynamicisland.client;

import com.dynamicisland.Config;
import com.dynamicisland.mixin.EntityAccessor;
import com.dynamicisland.mixin.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 挖掘 / 使用 / 状态感知：每帧算出胶囊该显示什么。 */
public class ProgressTracker {

    private static boolean mixinOk = true;
    private static float lastMining = 0f;
    private static float miningRate = 0f;
    private static long lastMiningAt = 0;
    private static float cooldownHold = 0f;

    public static boolean mixinAvailable() { return mixinOk; }

    public static IslandStatus update(float dt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return IslandStatus.idle("", "");

        IslandStatus s = tryDeath(p);
        if (s != null) return s;
        s = tryPortal(p);
        if (s != null) return s;
        s = trySleep(p, mc.level);
        if (s != null) return s;

        // 爆炸倒计时排在很前面：这种信息值一条命
        if (Config.modBlast) {
            s = blastStatus();
            if (s != null) return s;
        }

        // 被埋窒息 / 细雪冻结：同样是「不知道就死」的信息，但让爆炸倒计时先说话
        s = trySurvival();
        if (s != null) return s;
        // 盾牌破防的 5 秒在 PVP 里等于敞开挨打，排在物品使用之前
        s = tryCombat();
        if (s != null) return s;
        // 咬钩窗口只有一瞬，插在挖掘 / 使用之前，保证抢得到胶囊
        s = tryFishing();
        if (s != null) return s;

        if (Config.modUse) {
            s = tryUse(p);
            if (s != null) return s;
        }
        // 坠落预测放在「正在使用物品」之后：玩家一旦开始放水桶，优先反馈操作本身
        if (Config.modFall) {
            s = fallStatus();
            if (s != null) return s;
        }
        if (Config.modMining) {
            s = tryMining(mc, p);
            if (s != null) return s;
        }
        if (Config.modSense) {
            // 载具速度表要在鞘翅卡片之前接管，关掉本功能才会退回原来的鞘翅卡片
            if (Config.modRide) {
                s = rideStatus(p);
                if (s != null) return s;
            }
            s = tryFly(p);
            if (s != null) return s;
            s = tryAlert(p);
            if (s != null) return s;
        }
        // 攻击充能不再占用胶囊，改由 IslandRenderer 画进下方小信息栏（开关：modAttack）
        if (Config.modPickup || Config.modHotbar) {
            s = ItemPop.status();
            if (s != null) return s;
        }
        if (Config.modBuild) {
            s = BuildTracker.status();
            if (s != null) return s;
        }
        if (Config.modMusic) {
            s = MusicCard.status();
            if (s != null) return s;
        }
        if (Config.modTritium) {
            s = tritiumStatus();
            if (s != null) return s;
        }
        if (Config.modDayTime) {
            s = DayClock.status();
            if (s != null) return s;
        }
        if (Config.modDepth) {
            s = DepthWatch.status();
            if (s != null) return s;
        }
        if (Config.modReticle) {
            s = Reticle.status();
            if (s != null) return s;
        }
        if (Config.modTrade) {
            s = tradeStatus();
            if (s != null) return s;
        }
        // 焦点上台内容排最后：钉在胶囊上时也不挡住挖掘 / 拾取 / 告警等功能
        s = tryFocusStage(p);
        if (s != null) return s;
        return null; // 交给 IDLE
    }

    // ---------- 死亡 ----------
    private static IslandStatus tryDeath(LocalPlayer p) {
        if (!p.isDeadOrDying()) return null;
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.DEAD;
        s.title = I18nS.tr("dynamicisland.state.dead");
        s.sub = "点击重生";
        s.progress = 1f;
        return s.withAccent(Config.theme.bad).pulse();
    }

    // ---------- 传送门 ----------
    private static IslandStatus tryPortal(LocalPlayer p) {
        try {
            int t = ((EntityAccessor) p).dynamicisland$getPortalTime();
            if (t <= 0) return null;
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.PORTAL;
            s.title = I18nS.tr("dynamicisland.state.portal");
            s.sub = String.format("%.1fs", t / 20f);
            s.progress = Anim.clamp(t / 80f, 0f, 1f);
            return s.withAccent(Config.theme.accent);
        } catch (Throwable e) {
            return null;
        }
    }

    // ---------- 睡觉 ----------
    private static IslandStatus trySleep(LocalPlayer p, Level lv) {
        if (!p.isSleeping()) return null;
        long day = lv.getDayTime() % 24000L;
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.SLEEP;
        s.title = I18nS.tr("dynamicisland.state.sleep");
        s.sub = "Zzz…";
        s.progress = Anim.clamp((day - 12500f) / 11500f, 0.03f, 1f);
        return s.withAccent(0x8E7BFF);
    }

    // ---------- 使用物品 ----------
    private static IslandStatus tryUse(LocalPlayer p) {
        if (!p.isUsingItem()) return null;
        ItemStack stack = p.getUseItem();
        if (stack.isEmpty()) return null;
        int duration = stack.getUseDuration();
        if (duration <= 0) return null;
        int remain = p.getUseItemRemainingTicks();
        int elapsed = Math.max(0, duration - remain);

        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.USE;
        s.icon = stack;
        s.title = shorten(stack.getHoverName().getString(), 16);

        int full = Config.modBowCharge ? fullChargeTicks(stack) : 0;
        if (full > 0) {
            // 弓 / 弩 / 三叉戟：原版界面完全没有读数，这里给精确的蓄力百分比
            float charge = Anim.clamp(elapsed / (float) full, 0f, 1f);
            s.sub = Math.round(charge * 100f) + "%";
            s.progress = charge;
            return s.withAccent(charge >= 0.999f ? Config.theme.good : Config.theme.warn);
        }

        float prog = Anim.clamp(elapsed / (float) duration, 0f, 1f);
        float secs = remain / 20f;
        s.sub = duration > 100 ? (int) (prog * 100) + "%" : String.format("%.1fs", secs);
        s.progress = prog;
        return s.withAccent(Config.theme.accent);
    }

    /**
     * 蓄力类武器的「满蓄力」需要多少 tick；0 表示不是蓄力类。
     * 数值全部取原版常量，避免自己写死后被数据包 / 版本改动坑到：
     * 弓 20（MAX_DRAW_DURATION）、三叉戟 10（THROW_THRESHOLD_TIME）、
     * 弩由 getChargeDuration(stack) 计算（会算上快速装填附魔）。
     */
    private static int fullChargeTicks(ItemStack stack) {
        try {
            net.minecraft.world.item.Item it = stack.getItem();
            if (it instanceof net.minecraft.world.item.BowItem) {
                return net.minecraft.world.item.BowItem.MAX_DRAW_DURATION;
            }
            if (it instanceof net.minecraft.world.item.TridentItem) {
                return net.minecraft.world.item.TridentItem.THROW_THRESHOLD_TIME;
            }
            if (it instanceof net.minecraft.world.item.CrossbowItem) {
                return net.minecraft.world.item.CrossbowItem.getChargeDuration(stack);
            }
        } catch (Throwable ignored) { }
        return 0;
    }

    // ---------- 挖掘 ----------
    private static IslandStatus tryMining(Minecraft mc, LocalPlayer p) {
        if (!mixinOk) return null;
        float prog;
        BlockPos pos;
        boolean destroying;
        try {
            MultiPlayerGameModeAccessor acc = (MultiPlayerGameModeAccessor) mc.gameMode;
            if (acc == null) return null;
            prog = acc.dynamicisland$getDestroyProgress();
            pos = acc.dynamicisland$getDestroyBlockPos();
            destroying = acc.dynamicisland$isDestroying();
        } catch (Throwable e) {
            mixinOk = false;
            return null;
        }
        if (!destroying || prog <= 0f || pos == null) return null;

        long now = System.nanoTime();
        if (lastMiningAt > 0) {
            float dt = (now - lastMiningAt) / 1e9f;
            if (dt > 0.0005f) {
                float inst = (prog - lastMining) / dt;
                if (inst > 0f) miningRate = miningRate <= 0 ? inst : Anim.lerp(miningRate, inst, 0.25f);
            }
        }
        lastMiningAt = now;
        lastMining = prog;

        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.MINING;
        BlockState st = mc.level.getBlockState(pos);
        s.title = shorten(st.getBlock().getName().getString(), 16);
        ItemStack asItem = st.getBlock().asItem() == net.minecraft.world.item.Items.AIR
                ? p.getMainHandItem() : new ItemStack(st.getBlock());
        s.icon = asItem;
        if (miningRate > 0.0001f) {
            float left = (1f - prog) / miningRate;
            s.sub = left > 99 ? ">99s" : String.format("%.1fs", left);
        } else {
            s.sub = (int) (prog * 100) + "%";
        }
        s.progress = Anim.clamp(prog, 0f, 1f);
        return s.withAccent(Config.theme.warn);
    }

    // ---------- 攻击充能 ----------
    private static IslandStatus tryCooldown(LocalPlayer p) {
        float c = p.getAttackStrengthScale(0f);
        if (c >= 0.98f && cooldownHold <= 0f) return null;
        if (c < 0.98f) cooldownHold = 0.35f;
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.COOLDOWN;
        s.title = I18nS.tr("dynamicisland.state.cooldown");
        s.sub = (int) (c * 100) + "%";
        s.progress = c;
        return s.withAccent(Config.theme.warn);
    }

    // ---------- 鞘翅 ----------
    private static IslandStatus tryFly(LocalPlayer p) {
        if (!p.isFallFlying()) return null;
        double v = p.getDeltaMovement().length() * 20.0 * 3.6;
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.FLY;
        s.title = I18nS.tr("dynamicisland.state.fly");
        s.sub = String.format("%.0f km/h · Y %d", v, p.blockPosition().getY());
        s.progress = Anim.clamp((float) (v / 120f), 0f, 1f);
        return s.withAccent(Config.theme.accent);
    }

    // ---------- 爆炸倒计时 ----------
    private static IslandStatus blastStatus() {
        if (!BlastWatch.active) return null;
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.ALERT;
        s.title = BlastWatch.title;
        s.sub = BlastWatch.sub;
        s.progress = BlastWatch.progress;
        s.showBar = true;
        return s.withAccent(Config.theme.bad).pulse();
    }

    // ---------- 坠落预测 ----------
    private static IslandStatus fallStatus() {
        if (!FallWatch.active) return null;
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.ALERT;
        s.title = FallWatch.title;
        s.sub = FallWatch.sub;
        s.progress = FallWatch.progress;
        s.showBar = true;
        return s.withAccent(Config.theme.bad).pulse();
    }

    // ---------- 生存告警：窒息 / 细雪冻结 ----------
    private static IslandStatus trySurvival() {
        if (Config.modSuffocate && SurvivalWatch.suffocateActive) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.ALERT;
            s.icon = SurvivalWatch.SUFFOCATE_ICON;
            s.title = I18nS.tr("dynamicisland.state.suffocate");
            s.sub = SurvivalWatch.suffocateSub;
            s.progress = SurvivalWatch.suffocateProgress;
            s.showBar = true;
            return s.withAccent(Config.theme.bad).pulse();
        }
        if (Config.modFreezeWarning && SurvivalWatch.freezeActive) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.ALERT;
            s.icon = SurvivalWatch.FREEZE_ICON;
            s.title = I18nS.tr("dynamicisland.state.freeze");
            s.sub = SurvivalWatch.freezeSub;
            s.progress = SurvivalWatch.freezeProgress;
            s.showBar = true;
            return SurvivalWatch.freezeUrgent
                    ? s.withAccent(Config.theme.bad).pulse()
                    : s.withAccent(Config.theme.accent);
        }
        return null;
    }

    // ---------- 盾牌破防禁用 ----------
    private static IslandStatus tryCombat() {
        if (!Config.modShieldBreak) return null;
        if (CombatWatch.shieldActive) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.ALERT;
            s.icon = CombatWatch.SHIELD_ICON;
            s.title = I18nS.tr("dynamicisland.state.shieldDown");
            s.sub = CombatWatch.shieldSub;
            s.progress = CombatWatch.shieldProgress;
            s.showBar = true;
            return s.withAccent(Config.theme.bad).pulse();
        }
        if (CombatWatch.shieldBack) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.ALERT;
            s.icon = CombatWatch.SHIELD_ICON;
            s.title = I18nS.tr("dynamicisland.state.shieldUp");
            s.sub = CombatWatch.shieldSub;
            s.progress = CombatWatch.shieldProgress;
            s.showBar = true;
            return s.withAccent(Config.theme.good);
        }
        return null;
    }

    // ---------- 钓鱼咬钩 ----------
    private static IslandStatus tryFishing() {
        if (!Config.modFishingBite || !FishingWatch.active) return null;
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.ALERT;
        s.title = I18nS.tr("dynamicisland.state.fishing");
        s.sub = FishingWatch.sub;
        s.progress = FishingWatch.progress;
        s.showBar = true;
        return s.withAccent(Config.theme.good).pulse();
    }

    // ---------- 载具速度 ----------
    private static IslandStatus rideStatus(LocalPlayer p) {
        if (!RideWatch.active) return null;
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.FLY;
        s.icon = RideWatch.icon(p);
        s.title = RideWatch.title;
        s.sub = RideWatch.sub;
        s.progress = RideWatch.progress;
        s.showBar = true;
        return s.withAccent(Config.theme.accent);
    }

    // ---------- Tritium Music 联动 ----------
    private static IslandStatus tritiumStatus() {
        if (!TritiumLink.playing) return null;
        if (!TritiumLink.onIsland()) return null;   // 已并入焦点
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.MUSIC;
        s.title = TritiumLink.title;
        s.sub = TritiumLink.artist.isEmpty() ? TritiumLink.sub
                : TritiumLink.artist + " · " + TritiumLink.sub;
        s.progress = TritiumLink.progress;
        s.showBar = true;
        if (Config.tritiumCover && TritiumLink.coverReady()) s.cover = TritiumLink.cover;
        // 开封面取色时让封面主色接管，否则用联动板块的自定义色
        int accent = Config.accentOf(Config.colorMusic, Config.theme);
        if (Config.tritiumCover && TritiumLink.tint >= 0) accent = TritiumLink.tint;
        return s.withAccent(accent);
    }

    // ---------- 交易补货倒计时 ----------
    private static IslandStatus tradeStatus() {
        if (!TradeWatch.active) return null;
        IslandStatus s = new IslandStatus();
        s.kind = IslandStatus.Kind.ALERT;
        s.title = TradeWatch.title;
        s.sub = TradeWatch.sub;
        s.progress = TradeWatch.progress;
        s.showBar = true;
        return s.withAccent(Config.theme.warn);
    }

    // ---------- 灵动焦点：上台预告 ----------
    /** 焦点来源刚被触发时先在胶囊显示几秒，之后让位给焦点；这里负责那几秒的内容。 */
    private static IslandStatus tryFocusStage(LocalPlayer p) {
        // 自定义定时器排在最前，且不受「灵动焦点」总开关影响：
        // 那是玩家自己明确要盯的东西，就算焦点关了也该在胶囊上闪一下。
        if (Config.modTimer) {
            TimerCenter.Event ev = TimerCenter.islandEvent();
            if (ev != null) {
                IslandStatus s = new IslandStatus();
                s.kind = IslandStatus.Kind.FOCUS;
                s.title = ev.name;
                s.sub = TimerCenter.subOf(ev);
                s.progress = ev.progress();
                s.textColor = ev.color;
                s.showBar = true;
                return s.withAccent(ev.color);
            }
        }

        if (!Config.focusEnabled) return null;

        if (Config.focusAir && VitalsWatch.airOnIsland()) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.FOCUS;
            s.title = VitalsWatch.air.label;
            s.sub = VitalsWatch.air.sub;
            s.progress = VitalsWatch.air.progress;
            return s.withAccent(VitalsWatch.air.accent).pulse();
        }
        if (Config.focusDrop && DropWatch.onIsland()) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.FOCUS;
            s.icon = DropWatch.icon;
            s.title = DropWatch.label;
            s.sub = DropWatch.sub;
            s.progress = DropWatch.progress;
            return s.withAccent(Config.theme.bad).pulse();
        }
        if (Config.focusXpOrb && XpOrbWatch.onIsland()) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.FOCUS;
            s.icon = XpOrbWatch.icon;
            s.title = XpOrbWatch.label;
            s.sub = XpOrbWatch.sub;
            s.progress = XpOrbWatch.progress;
            return s.withAccent(Config.theme.good);
        }
        if (Config.focusMark && MarkWatch.onIsland()) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.FOCUS;
            s.title = MarkWatch.label;
            s.sub = MarkWatch.sub;
            s.progress = MarkWatch.progress;
            return s.withAccent(Config.theme.warn).pulse();
        }
        if (Config.focusCloud && CloudWatch.onIsland()) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.FOCUS;
            s.icon = CloudWatch.icon;
            s.title = CloudWatch.label;
            s.sub = CloudWatch.sub;
            s.progress = CloudWatch.progress;
            return CloudWatch.accent < 0 ? s.withAccent(Config.theme.accent) : s.withAccent(CloudWatch.accent);
        }
        if (Config.focusDurability && DurabilityWatch.onIsland()) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.FOCUS;
            s.icon = DurabilityWatch.icon;
            s.title = DurabilityWatch.label;
            s.sub = DurabilityWatch.sub;
            s.progress = DurabilityWatch.progress;
            return s.withAccent(Config.theme.warn);
        }
        if (Config.focusHunger && VitalsWatch.hungerOnIsland()) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.FOCUS;
            s.title = VitalsWatch.hunger.label;
            s.sub = VitalsWatch.hunger.sub;
            s.progress = VitalsWatch.hunger.progress;
            return s.withAccent(Config.theme.warn);
        }
        if (Config.focusPotion && PotionWatch.onIsland()) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.FOCUS;
            s.title = PotionWatch.label;
            s.sub = PotionWatch.sub;
            s.progress = PotionWatch.progress;
            return s.withAccent(PotionWatch.accent);
        }
        if (Config.focusCure && CureWatch.onIsland()) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.FOCUS;
            s.title = CureWatch.label;
            s.sub = CureWatch.sub;
            s.progress = CureWatch.progress;
            return s.withAccent(Config.theme.accent);
        }
        if (Config.focusEndermite && EndermiteWatch.onIsland()) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.FOCUS;
            s.icon = EndermiteWatch.icon;
            s.title = EndermiteWatch.label;
            s.sub = EndermiteWatch.sub;
            s.progress = EndermiteWatch.progress;
            return s.withAccent(Config.theme.accent);
        }
        return null;
    }

    /** 氧气是否已被灵动焦点接管（接管后胶囊不再重复告警） */
    private static boolean airTakenByFocus() {
        return Config.focusEnabled && Config.focusAir
                && VitalsWatch.air.active && !VitalsWatch.airOnIsland();
    }

    /** 饥饿是否已被灵动焦点接管 */
    private static boolean hungerTakenByFocus() {
        return Config.focusEnabled && Config.focusHunger
                && VitalsWatch.hunger.active && !VitalsWatch.hungerOnIsland();
    }

    // ---------- 危险告警 ----------
    private static IslandStatus tryAlert(LocalPlayer p) {
        int air = p.getAirSupply(), maxAir = p.getMaxAirSupply();
        float hp = p.getHealth() / Math.max(1f, p.getMaxHealth());
        int food = p.getFoodData().getFoodLevel();
        int fire = p.getRemainingFireTicks();

        // 已交给焦点的项目不再重复占用胶囊（血量除外，血量始终告警，不并入焦点）
        if (air < maxAir * 0.35f && !airTakenByFocus()) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.ALERT;
            s.title = I18nS.tr("dynamicisland.state.breath");
            s.sub = String.format("%.1fs", air / 20f);
            s.progress = Anim.clamp(air / (float) Math.max(1, maxAir), 0f, 1f);
            return s.withAccent(0x40A9FF).pulse();
        }
        if (fire > 0) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.ALERT;
            s.title = I18nS.tr("dynamicisland.state.burn");
            s.sub = String.format("%.1fs", fire / 20f);
            s.progress = Anim.clamp(fire / 200f, 0f, 1f);
            return s.withAccent(Config.theme.bad).pulse();
        }
        if (hp < 0.3f) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.ALERT;
            s.title = I18nS.tr("dynamicisland.state.lowhp");
            s.sub = String.format("%.1f / %.1f", p.getHealth(), p.getMaxHealth());
            s.progress = hp;
            return s.withAccent(Config.theme.bad).pulse();
        }
        if (food <= 6 && !hungerTakenByFocus()) {
            IslandStatus s = new IslandStatus();
            s.kind = IslandStatus.Kind.ALERT;
            s.title = I18nS.tr("dynamicisland.state.hungry");
            s.sub = food + " / 20";
            s.progress = food / 20f;
            return s.withAccent(Config.theme.warn);
        }
        return null;
    }

    public static void resetMining() { lastMining = 0f; miningRate = 0f; lastMiningAt = 0; }

    private static String shorten(String s, int max) { return RenderUtil.shorten(s, max); }

    /** 极小工具：拿当前语言取翻译键 */
    public static class I18nS {
        public static String tr(String key, Object... args) {
            try {
                return Component.translatable(key, args).getString();
            } catch (Throwable t) {
                return key;
            }
        }
    }
}
