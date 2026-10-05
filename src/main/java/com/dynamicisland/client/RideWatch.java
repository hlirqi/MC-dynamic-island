package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.item.ItemStack;

/**
 * 载具速度表：坐矿车 / 船或鞘翅高速滑翔时，胶囊实时显示速度（km/h）。
 *
 * 低速时不打扰（低于 rideMinSpeed 就不展开）。鞘翅在开启本功能时交给这里显示，
 * 关掉本功能就回退到原来的状态感知卡片。
 */
public class RideWatch {

    public static String title = "";
    public static String sub = "";
    public static float progress = 0f;
    public static boolean active = false;

    /** 进度条按这个速度拉满 */
    private static final float FULL_SPEED = 120f;

    public static void tick(Minecraft mc) {
        if (!Config.modRide) { active = false; return; }
        LocalPlayer p = mc.player;
        if (p == null) { active = false; return; }

        Entity veh = p.getVehicle();
        boolean minecart = veh instanceof Minecart;
        boolean boat = veh instanceof Boat;
        boolean elytra = p.isFallFlying();

        if (!minecart && !boat && !elytra) { active = false; return; }

        double kmh = p.getDeltaMovement().length() * 20.0 * 3.6;
        if (kmh < Config.rideMinSpeed) { active = false; return; }

        active = true;
        title = minecart
                ? ProgressTracker.I18nS.tr("dynamicisland.ride.minecart")
                : (boat ? ProgressTracker.I18nS.tr("dynamicisland.ride.boat")
                        : ProgressTracker.I18nS.tr("dynamicisland.ride.elytra"));
        sub = (int) Math.round(kmh) + " km/h";
        progress = Anim.clamp((float) (kmh / FULL_SPEED), 0f, 1f);
    }

    public static void reset() {
        active = false; title = ""; sub = ""; progress = 0f;
    }

    /** 载具对应的图标，没有就用空 */
    public static ItemStack icon(LocalPlayer p) {
        Entity veh = p.getVehicle();
        if (veh instanceof Minecart) return new ItemStack(net.minecraft.world.item.Items.MINECART);
        if (veh instanceof Boat boat) {
            try { ItemStack pick = boat.getPickResult(); return pick == null ? ItemStack.EMPTY : pick; }
            catch (Throwable t) { return ItemStack.EMPTY; }
        }
        if (p.isFallFlying()) return new ItemStack(net.minecraft.world.item.Items.ELYTRA);
        return ItemStack.EMPTY;
    }
}
