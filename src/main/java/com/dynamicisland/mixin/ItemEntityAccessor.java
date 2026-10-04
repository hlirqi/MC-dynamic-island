package com.dynamicisland.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 掉落物的「消失计时」字段 age：原版每 tick +1，到 6000 就永久消失。
 * 没有公开 getter，只能开 accessor。比 Entity.tickCount 更准确（合并后 age 会重置为较小值）。
 */
@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {
    @Accessor("age")
    int dynamicisland$getAge();
}
