package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.modules.movement.VelocityPlus;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void onPushOutOfBlocks(double x, double z, CallbackInfo ci) {
        VelocityPlus module = Modules.get().get(VelocityPlus.class);

        if (module != null && module.isActive() && module.blockPush.get()) {
            ci.cancel();
        }
    }
}
