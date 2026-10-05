package com.dynamicisland.mixin;

import net.minecraft.world.entity.monster.Endermite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 末影螨的自增存活字段 life：原版每 tick +1，到 MAX_LIFE（2400 = 2 分钟）就自然死亡。
 * 没有公开 getter，只能开 accessor。读不到时 EndermiteWatch 会退回 Entity.tickCount 兜底。
 */
@Mixin(Endermite.class)
public interface EndermiteAccessor {
    @Accessor("life")
    int dynamicisland$getLife();
}
