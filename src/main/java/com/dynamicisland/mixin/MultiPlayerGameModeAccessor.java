package com.dynamicisland.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取本地玩家的破坏进度（原版未暴露公开 getter） */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Accessor("destroyProgress")
    float dynamicisland$getDestroyProgress();

    @Accessor("destroyBlockPos")
    BlockPos dynamicisland$getDestroyBlockPos();

    @Accessor("isDestroying")
    boolean dynamicisland$isDestroying();
}
