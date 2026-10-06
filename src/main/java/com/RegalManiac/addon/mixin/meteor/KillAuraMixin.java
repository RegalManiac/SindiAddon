package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.utils.player.PredictionUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KillAura.class, remap = false)
public class KillAuraMixin {

    @Unique
    private Setting<Boolean> predict;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        KillAura aura = (KillAura) (Object) this;

        SettingGroup sgPrediction = aura.settings.createGroup("Prediction");

        predict = sgPrediction.add(new BoolSetting.Builder()
            .name("predict")
            .description("Predicts target movement and calculates optimal hit position on the bounding box.")
            .defaultValue(true)
            .build()
        );
    }

    @WrapOperation(method = "onTick", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/player/Rotations;getYaw(Lnet/minecraft/entity/Entity;)D"))
    private double redirectGetYawOnTick(Entity entity, Operation<Double> original) {
        if (predict != null && predict.get()) {
            Vec3d aimPoint = PredictionUtils.getAimPoint(entity, true);
            if (aimPoint != null) {
                return Rotations.getYaw(aimPoint);
            }
        }
        return Rotations.getYaw(entity);
    }

    @WrapOperation(method = "onTick", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/player/Rotations;getPitch(Lnet/minecraft/entity/Entity;Lmeteordevelopment/meteorclient/utils/entity/Target;)D"))
    private double redirectGetPitchOnTick(Entity entity, Target target, Operation<Double> original) {
        if (predict != null && predict.get()) {
            Vec3d aimPoint = PredictionUtils.getAimPoint(entity, true);
            if (aimPoint != null) {
                return Rotations.getPitch(aimPoint);
            }
        }
        return Rotations.getPitch(entity, target);
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/player/Rotations;getYaw(Lnet/minecraft/entity/Entity;)D"))
    private double redirectGetYawOnAttack(Entity entity, Operation<Double> original) {
        if (predict != null && predict.get()) {
            Vec3d aimPoint = PredictionUtils.getAimPoint(entity, true);
            if (aimPoint != null) {
                return Rotations.getYaw(aimPoint);
            }
        }
        return Rotations.getYaw(entity);
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/player/Rotations;getPitch(Lnet/minecraft/entity/Entity;Lmeteordevelopment/meteorclient/utils/entity/Target;)D"))
    private double redirectGetPitchOnAttack(Entity entity, Target target, Operation<Double> original) {
        if (predict != null && predict.get()) {
            Vec3d aimPoint = PredictionUtils.getAimPoint(entity, true);
            if (aimPoint != null) {
                return Rotations.getPitch(aimPoint);
            }
        }
        return Rotations.getPitch(entity, target);
    }
}
