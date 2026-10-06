package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.utils.sound.SoundUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.misc.Notifier;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Notifier.class)
public class NotifierMixin {

    @Inject(method = "onTick", at = @At("HEAD"))
    private void onTickDeathCheck(TickEvent.Post event, CallbackInfo ci) {
        SoundUtils.checkAndPlayDeathSound();
    }

    @WrapOperation(
        method = "onEntityAdded(Lmeteordevelopment/meteorclient/events/entity/EntityAddedEvent;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/world/ClientWorld;playSoundFromEntity(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"
        )
    )
    private void redirectVrEnterSound(ClientWorld instance, Entity except, Entity entity, SoundEvent sound, SoundCategory category, float volume, float pitch, Operation<Void> original) {
        SoundUtils.playVrEnterSound(instance, except, entity, sound, category, volume, pitch);
    }

    @WrapOperation(
        method = "onEntityRemoved(Lmeteordevelopment/meteorclient/events/entity/EntityRemovedEvent;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/world/ClientWorld;playSoundFromEntity(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"
        )
    )
    private void redirectVrLeaveSound(ClientWorld instance, Entity except, Entity entity, SoundEvent sound, SoundCategory category, float volume, float pitch, Operation<Void> original) {
        SoundUtils.playVrLeaveSound(instance, except, entity, sound, category, volume, pitch);
    }
}
