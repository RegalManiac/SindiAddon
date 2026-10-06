package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.modules.combat.OffhandPlus;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow
    public ClientPlayerEntity player;

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void onDoItemUse(CallbackInfo ci) {
        if (player == null) return;

        OffhandPlus offhandPlus = Modules.get().get(OffhandPlus.class);
        if (offhandPlus != null && offhandPlus.isActive() && offhandPlus.isSwapKeyHeld()) {
            ItemStack offhand = player.getOffHandStack();

            if (offhand.isOf(Items.SPLASH_POTION) || offhand.isOf(Items.LINGERING_POTION)) {
                ci.cancel();
            }
        }
    }
}
