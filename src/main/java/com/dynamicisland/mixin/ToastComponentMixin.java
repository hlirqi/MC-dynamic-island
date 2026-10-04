package com.dynamicisland.mixin;

import com.dynamicisland.client.Notifier;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 拦截成就弹窗，转成灵动岛通知 */
@Mixin(ToastComponent.class)
public class ToastComponentMixin {

    @Inject(method = "addToast", at = @At("HEAD"), cancellable = false)
    private void dynamicisland$onAddToast(Toast toast, CallbackInfoReturnable<Toast> cir) {
        if (!(toast instanceof AdvancementToast)) return;
        try {
            Advancement a = ((AdvancementToastAccessor) toast).dynamicisland$getAdvancement();
            if (a != null && a.getDisplay() != null) {
                Notifier.onAdvancement(a.getDisplay().getTitle(), Component.empty());
            }
        } catch (Throwable ignored) { }
    }
}
