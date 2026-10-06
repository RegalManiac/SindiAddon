package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.utils.sound.SoundUtils;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Module.class, remap = false)
public abstract class ModuleMixin {

    @Shadow
    public abstract boolean isActive();

    @Inject(method = "toggle", at = @At("TAIL"), remap = false)
    private void onToggleTail(CallbackInfo ci) {
        SoundUtils.playModuleToggleSound(isActive());
    }
}
