package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.managers.RotationManager;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Item.class)
public class ItemMixin {

    @ModifyExpressionValue(method = "raycast", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getPitch()F"))
    private static float hookRaycastPitch(float original, @Local(argsOnly = true) PlayerEntity player) {
        if (player != MinecraftClient.getInstance().player) return original;
        RotationManager.Rotation rotation = RotationManager.getCurrentRotation();
        return rotation != null ? rotation.getPitch() : original;
    }

    @ModifyExpressionValue(method = "raycast", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"))
    private static float hookRaycastYaw(float original, @Local(argsOnly = true) PlayerEntity player) {
        if (player != MinecraftClient.getInstance().player) return original;
        RotationManager.Rotation rotation = RotationManager.getCurrentRotation();
        return rotation != null ? rotation.getYaw() : original;
    }
}
