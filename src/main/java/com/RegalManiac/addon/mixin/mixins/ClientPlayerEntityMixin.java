package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.managers.RotationManager;
import com.RegalManiac.addon.modules.movement.VelocityPlus;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPlayerEntity.class, priority = Integer.MAX_VALUE)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {

    @Shadow public Input input;

    public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void onPushOutOfBlocks(double x, double z, CallbackInfo ci) {
        VelocityPlus module = Modules.get().get(VelocityPlus.class);

        if (module != null && module.isActive() && module.blockPush.get()) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    private float sendMovementPackets$modifyGetYaw(float original) {
        return (RotationManager.getCurrentRotation() == null && !RotationManager.isWireFresh()) ? original : RotationManager.getSentYaw();
    }

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    private float sendMovementPackets$modifyGetPitch(float original) {
        return (RotationManager.getCurrentRotation() == null && !RotationManager.isWireFresh()) ? original : RotationManager.getSentPitch();
    }

    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick()V", shift = At.Shift.AFTER))
    private void onInputTick(CallbackInfo ci) {
        RotationManager.applyMovementFix(this.input);
    }
}
