package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.modules.movement.VelocityPlus;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void onPushAwayFrom(Entity entity, CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            VelocityPlus module = Modules.get().get(VelocityPlus.class);

            if (module != null && module.isActive() && module.entityPush.get()) {
                ci.cancel();
            }
        }
    }
}
