package com.RegalManiac.addon.mixin.meteor;

import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.player.AirPlace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AirPlace.class, remap = false)
public class AirPlaceMixin {

    @Unique
    private final MinecraftClient mc = MinecraftClient.getInstance();

    @Unique
    private boolean isBlock(ItemStack stack) {
        return stack != null && stack.getItem() instanceof BlockItem;
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void onTick(TickEvent.Pre event, CallbackInfo ci) {
        if (mc.player == null || !isBlock(mc.player.getMainHandStack())) {
            ci.cancel();
        }
    }

    @Inject(method = "onInteractItem", at = @At("HEAD"), cancellable = true)
    private void onInteractItem(InteractItemEvent event, CallbackInfo ci) {
        if (mc.player == null || !isBlock(mc.player.getStackInHand(event.hand))) {
            ci.cancel();
        }
    }

    @Inject(method = "onRender", at = @At("HEAD"), cancellable = true)
    private void onRender(CallbackInfo ci) {
        if (mc.player == null || !isBlock(mc.player.getMainHandStack())) {
            ci.cancel();
        }
    }
}
