package com.RegalManiac.addon.mixin.mixins;

import net.minecraft.client.util.NarratorManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NarratorManager.class)
public class NarratorManagerMixin {

    @Inject(method = "narrate(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void onNarrate(Text message, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "narrateSystemImmediately(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void onNarrateSystem(String text, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "say(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void onSay(String text, boolean interrupt, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void onIsActive(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
