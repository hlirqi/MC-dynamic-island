package com.dynamicisland.client;

import com.dynamicisland.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 宠物看护：宠物死亡 / 拴绳断裂时播报。
 *
 * 做法很朴素——每秒扫一遍附近属于自己的驯服生物，上一秒还在、这一秒没了就判定死亡；
 * 拴着的实体不再被拴住则判定拴绳断了。原版这两种情况都只是静悄悄发生。
 */
public class PetWatch {

    private static final double RADIUS = 40.0;
    private static final Map<UUID, String> known = new HashMap<>();
    private static final Map<UUID, UUID> leashed = new HashMap<>();
    private static float timer = 0f;

    public static void tick(float dt, Minecraft mc) {
        if (!Config.noticePet) { known.clear(); leashed.clear(); return; }
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;

        timer += dt;
        if (timer < 1f) return;
        timer = 0f;

        Map<UUID, String> now = new HashMap<>();
        Map<UUID, UUID> nowLeash = new HashMap<>();

        try {
            AABB box = new AABB(p.blockPosition()).inflate(RADIUS);
            for (Entity e : mc.level.getEntities(p, box)) {
                if (e instanceof TamableAnimal t && p.getUUID().equals(t.getOwnerUUID())) {
                    if (t.isAlive()) now.put(t.getUUID(), t.getName().getString());
                }
                // 被玩家拴着的实体
                try {
                    if (e.isAlive() && e instanceof net.minecraft.world.entity.Mob mob && mob.isLeashed()) {
                        // 只认拴在自己手上的；不同版本 API 名字不一，拿 holder 实体直接比
                        Entity holder = mob.getLeashHolder();
                        if (holder != null && holder.getUUID().equals(p.getUUID())) {
                            nowLeash.put(e.getUUID(), p.getUUID());
                        }
                    }
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }

        // 上一轮在、这一轮没了 -> 宠物没了
        for (Map.Entry<UUID, String> en : known.entrySet()) {
            if (!now.containsKey(en.getKey())) Notifier.onPetDeath(en.getValue());
        }
        // 上一轮拴着、这一轮脱开了 -> 拴绳断了
        for (UUID id : leashed.keySet()) {
            if (!nowLeash.containsKey(id)) Notifier.onLeashBroken();
        }

        known.clear(); known.putAll(now);
        leashed.clear(); leashed.putAll(nowLeash);
    }

    public static void reset() { known.clear(); leashed.clear(); timer = 0f; }
}
