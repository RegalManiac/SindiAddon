package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.utils.sound.SoundUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChatUtils.class, remap = false)
public class ChatUtilsMixin {

    @Inject(method = "formatMsg", at = @At("HEAD"), remap = false)
    private static void onFormatMsg(String message, Formatting defaultColor, CallbackInfoReturnable<MutableText> cir) {
        SoundUtils.playChatMessageSound(message, defaultColor);
    }
}
