package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.managers.RotationManager;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @ModifyExpressionValue(method = "calcGlidingVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getPitch()F"))
    private float hookModifyFallFlyingPitch(float original) {
        if ((Object) this != MinecraftClient.getInstance().player) return original;

        RotationManager.Rotation rotation = RotationManager.getCurrentRotation();
        if (rotation != null) {
            return rotation.getPitch();
        }
        return original;
    }

    @WrapOperation(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float wrapJumpYaw(LivingEntity instance, Operation<Float> original) {
        if ((Object) this != MinecraftClient.getInstance().player) {
            return original.call(instance);
        }

        RotationManager.Rotation rotation = RotationManager.getCurrentRotation();
        return rotation != null ? rotation.getYaw() : original.call(instance);
    }
}
