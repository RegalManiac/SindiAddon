package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.modules.movement.MovementFix;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.config.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Setting.class, remap = false)
public class SettingMixin {

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void onIsVisible(CallbackInfoReturnable<Boolean> cir) {
        Setting<?> self = (Setting<?>) (Object) this;

        if (MovementFix.active() && Config.get() != null && self == Config.get().rotationHoldTicks) {
            cir.setReturnValue(false);
        }
    }
}
