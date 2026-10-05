package com.dynamicisland.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 经验球的「消失计时」字段 age：原版每 tick +1，到 6000（=5 分钟）就永久消失，
 * 与掉落物是同一套机制。没有公开 getter，只能开 accessor。
 * 读不到时 XpOrbWatch 会退回 Entity.tickCount 兜底，不影响其它功能。
 */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccessor {
    @Accessor("age")
    int dynamicisland$getAge();
}
