package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.SindiAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Module.class, remap = false)
public abstract class ModuleMixin {

    @Shadow
    public abstract boolean isActive();

    @Unique
    private static final SoundEvent MODULE_ON = SoundEvent.of(Identifier.of("sindiaddon", "enable"));

    @Unique
    private static final SoundEvent MODULE_OFF = SoundEvent.of(Identifier.of("sindiaddon", "disable"));

    @Inject(method = "toggle", at = @At("TAIL"), remap = false)
    private void onToggleTail(CallbackInfo ci) {
        if (SindiAddon.moduleSounds != null && !SindiAddon.moduleSounds.get()) return;
        if (canPlaySound()) {
            SoundEvent sound = isActive() ? MODULE_ON : MODULE_OFF;
            playSound(sound);
        }
    }

    @Unique
    private boolean canPlaySound() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.world == null) return false;
        if (mc.player.age < 20) return false;
        if (mc.getNetworkHandler() == null) return false;
        return true;
    }

    @Unique
    private void playSound(SoundEvent sound) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getSoundManager().play(PositionedSoundInstance.master(sound, 1.0F));
    }
}
