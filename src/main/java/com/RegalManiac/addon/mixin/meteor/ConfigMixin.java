package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.SindiAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.config.Config;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Config.class, remap = false)
public class ConfigMixin {
    @Shadow @Final public Settings settings;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        SettingGroup sgSindi = settings.createGroup("SindiAddon");

        SindiAddon.moduleSounds = sgSindi.add(new BoolSetting.Builder()
            .name("custom-module-sound")
            .description("Plays sounds when toggling modules.")
            .defaultValue(true)
            .build()
        );

        SindiAddon.chatSounds = sgSindi.add(new BoolSetting.Builder()
            .name("custom-chat-sound")
            .description("Plays sounds for chat messages (info, warning, error).")
            .defaultValue(true)
            .build()
        );

        SindiAddon.visualRangeSounds = sgSindi.add(new BoolSetting.Builder()
            .name("custom-visual-range-sound")
            .description("Use custom sounds for Visual Range alerts.")
            .defaultValue(true)
            .build()
        );

        SindiAddon.deathSounds = sgSindi.add(new BoolSetting.Builder()
            .name("custom-death-sound")
            .description("Plays custom sound upon death.")
            .defaultValue(true)
            .build()
        );
    }
}
