package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.managers.RotationManager;
import com.RegalManiac.addon.modules.movement.MovementFix;
import meteordevelopment.meteorclient.utils.player.Rotations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Rotations.class, remap = false)
public class RotationsMixin {

    @Inject(method = "rotate(DDIZLjava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
    private static void onRotate(double yaw, double pitch, int priority, boolean clientSide, Runnable callback, CallbackInfo ci) {
        if (MovementFix.active()) {
            RotationManager.rotate(new RotationManager.Rotation(priority, 0, (float) yaw, (float) pitch, callback));
            ci.cancel();
        }
    }
}
