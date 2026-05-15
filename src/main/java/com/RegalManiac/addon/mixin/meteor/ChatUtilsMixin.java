package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.SindiAddon;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChatUtils.class, remap = false)
public class ChatUtilsMixin {

    @Unique
    private static final SoundEvent SOUND_INFO = SoundEvent.of(Identifier.of("sindiaddon", "fuzz"));
    @Unique
    private static final SoundEvent SOUND_WARNING = SoundEvent.of(Identifier.of("sindiaddon", "klaxon"));
    @Unique
    private static final SoundEvent SOUND_ERROR = SoundEvent.of(Identifier.of("sindiaddon", "alarm"));

    @Inject(method = "formatMsg", at = @At("HEAD"), remap = false)
    private static void onFormatMsg(String message, Formatting defaultColor, CallbackInfoReturnable<MutableText> cir) {
        if (SindiAddon.chatSounds != null && !SindiAddon.chatSounds.get()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (message != null && message.startsWith("Toggled")) return;

        SoundEvent sound = null;

        if (defaultColor == Formatting.GRAY) sound = SOUND_INFO;
        else if (defaultColor == Formatting.YELLOW) sound = SOUND_WARNING;
        else if (defaultColor == Formatting.RED) sound = SOUND_ERROR;

        if (sound != null) {
            mc.getSoundManager().play(PositionedSoundInstance.master(sound, 1.0F));
        }
    }
}
