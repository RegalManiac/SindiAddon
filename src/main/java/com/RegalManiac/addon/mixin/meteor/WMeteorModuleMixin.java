package com.RegalManiac.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorModule;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = WMeteorModule.class, remap = false)
public abstract class WMeteorModuleMixin {

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static String onInit(String title, Module module) {
        if (module.getClass().getName().startsWith("com.RegalManiac.addon")) {
            if (!Config.get().customFont.get()) {
                return "§4☠ §r" + title;
            }
        }
        return title;
    }
}
