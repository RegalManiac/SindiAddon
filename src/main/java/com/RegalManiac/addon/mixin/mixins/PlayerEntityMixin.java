package com.RegalManiac.addon.mixin.mixins;

import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    @Inject(method = "isCreative", at = @At("HEAD"), cancellable = true)
    private void forceSurvivalCreative(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof FakePlayerEntity) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isSpectator", at = @At("HEAD"), cancellable = true)
    private void forceSurvivalSpectator(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof FakePlayerEntity) {
            cir.setReturnValue(false);
        }
    }
}
