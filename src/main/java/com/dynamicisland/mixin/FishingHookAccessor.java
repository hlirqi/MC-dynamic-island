package com.dynamicisland.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 钓鱼浮标的「有鱼咬钩」标志 biting：由同步数据 DATA_BITING 驱动
 * （`onSyncedDataUpdated` 里把同步值写进这个字段，服务端咬钩 / 跑鱼时都会下发），
 * 所以客户端读到的是权威判定，多人模式一样准。没有公开 getter，只能开 accessor。
 */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
    @Accessor("biting")
    boolean dynamicisland$isBiting();
}
