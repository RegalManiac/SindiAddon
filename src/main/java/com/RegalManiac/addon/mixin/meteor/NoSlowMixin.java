package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.modules.movement.NoSlowPlus;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(value = NoSlow.class, remap = false)
public class NoSlowMixin {

    @Inject(method = "items", at = @At("HEAD"), cancellable = true)
    private void items(CallbackInfoReturnable<Boolean> cir) {
        if(Objects.requireNonNull(Modules.get().get(NoSlowPlus.class)).canNoSlow())
            cir.setReturnValue(true);
    }

}
