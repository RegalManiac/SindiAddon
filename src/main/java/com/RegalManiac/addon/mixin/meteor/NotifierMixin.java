package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.SindiAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.misc.Notifier;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(Notifier.class)
public class NotifierMixin {

    @Unique
    private static final SoundEvent VR_ENTER = SoundEvent.of(Identifier.of("sindiaddon", "vrenter"));

    @Unique
    private static final SoundEvent VR_LEAVE = SoundEvent.of(Identifier.of("sindiaddon", "vrleave"));

    @Unique
    private static final SoundEvent DEATH_MUSIC = SoundEvent.of(Identifier.of("sindiaddon", "death"));

    @Unique
    private boolean playedDeathSound = false;

    public NotifierMixin() { super(); }

    @Inject(method = "onTick", at = @At("HEAD"))
    private void onTickDeathCheck(TickEvent.Post event, CallbackInfo ci) {
        if (mc.player == null) return;

        if (SindiAddon.deathSounds != null && !SindiAddon.deathSounds.get()) return;

        if ((mc.player.getHealth() <= 0 || mc.player.isDead()) && !playedDeathSound) {
            mc.getSoundManager().play(PositionedSoundInstance.master(DEATH_MUSIC, 1.0F));
            playedDeathSound = true;
        }
        else if (mc.player.getHealth() > 0 && playedDeathSound) {
            playedDeathSound = false;
        }
    }

    @ModifyArg(
        method = "onEntityAdded(Lmeteordevelopment/meteorclient/events/entity/EntityAddedEvent;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/world/ClientWorld;playSoundFromEntity(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"
        ),
        index = 2
    )
    private SoundEvent modifyVrEnterSound(SoundEvent original) {
        if (SindiAddon.visualRangeSounds != null && !SindiAddon.visualRangeSounds.get()) {
            return original;
        }
        return VR_ENTER;
    }

    @ModifyArg(
        method = "onEntityRemoved(Lmeteordevelopment/meteorclient/events/entity/EntityRemovedEvent;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/world/ClientWorld;playSoundFromEntity(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"
        ),
        index = 2
    )
    private SoundEvent modifyVrLeaveSound(SoundEvent original) {
        if (SindiAddon.visualRangeSounds != null && !SindiAddon.visualRangeSounds.get()) {
            return original;
        }
        return VR_LEAVE;
    }
}
