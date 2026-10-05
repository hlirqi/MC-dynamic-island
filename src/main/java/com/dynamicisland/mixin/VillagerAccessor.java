package com.dynamicisland.mixin;

import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 村民补货时间：原版没有公开 API，只能开个 accessor。
 * 补货规则见 Villager#restock —— 距离上次补货超过 12000 tick（半个 MC 日）且当天补货次数未用完。
 */
@Mixin(Villager.class)
public interface VillagerAccessor {
    @Accessor("lastRestockGameTime")
    long dynamicisland$getLastRestockGameTime();

    @Accessor("numberOfRestocksToday")
    int dynamicisland$getNumberOfRestocksToday();
}
