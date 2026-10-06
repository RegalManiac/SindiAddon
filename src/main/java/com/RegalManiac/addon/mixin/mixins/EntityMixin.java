package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.managers.RotationManager;
import com.RegalManiac.addon.modules.movement.VelocityPlus;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

    @WrapOperation(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    private float updateVelocity$overrideYaw(Entity instance, Operation<Float> original) {
        if ((Object) this != meteordevelopment.meteorclient.MeteorClient.mc.player) {
            return original.call(instance);
        }
        RotationManager.Rotation rotation = RotationManager.getCurrentRotation();
        return rotation != null ? rotation.getYaw() : original.call(instance);
    }

    @WrapOperation(method = "getRotationVector()Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    private float wrapRotationVectorYaw(Entity entity, Operation<Float> original) {
        if ((Object) this != MinecraftClient.getInstance().player) return original.call(entity);
        RotationManager.Rotation rotation = RotationManager.getCurrentRotation();
        return rotation != null ? rotation.getYaw() : original.call(entity);
    }

    @WrapOperation(method = "getRotationVector()Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch()F"))
    private float wrapRotationVectorPitch(Entity entity, Operation<Float> original) {
        if ((Object) this != MinecraftClient.getInstance().player) return original.call(entity);
        RotationManager.Rotation rotation = RotationManager.getCurrentRotation();
        return rotation != null ? rotation.getPitch() : original.call(entity);
    }

    @WrapOperation(method = "getOppositeRotationVector(F)Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
    private float wrapOppositeRotationVectorYaw(Entity entity, float tickProgress, Operation<Float> original) {
        if ((Object) this != MinecraftClient.getInstance().player) return original.call(entity, tickProgress);
        RotationManager.Rotation rotation = RotationManager.getCurrentRotation();
        return rotation != null ? rotation.getYaw() : original.call(entity, tickProgress);
    }

    @WrapOperation(method = "getOppositeRotationVector(F)Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
    private float wrapOppositeRotationVectorPitch(Entity entity, float tickProgress, Operation<Float> original) {
        if ((Object) this != MinecraftClient.getInstance().player) return original.call(entity, tickProgress);
        RotationManager.Rotation rotation = RotationManager.getCurrentRotation();
        return rotation != null ? rotation.getPitch() : original.call(entity, tickProgress);
    }
}
