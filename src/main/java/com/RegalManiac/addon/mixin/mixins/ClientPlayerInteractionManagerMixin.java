package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.managers.RotationManager;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @ModifyExpressionValue(method = "method_41929", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"))
    private float modifyInteractItemYaw(float original) {
        RotationManager.Rotation current = RotationManager.getCurrentRotation();
        return (current != null || RotationManager.isWireFresh()) ? RotationManager.getSentYaw() : original;
    }

    @ModifyExpressionValue(method = "method_41929", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getPitch()F"))
    private float modifyInteractItemPitch(float original) {
        RotationManager.Rotation current = RotationManager.getCurrentRotation();
        return (current != null || RotationManager.isWireFresh()) ? RotationManager.getSentPitch() : original;
    }
}
