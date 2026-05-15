package com.RegalManiac.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.List;

@Mixin(value = Modules.class, remap = false)
public class ModulesMixin {

    @Inject(method = "getGroup", at = @At("RETURN"), cancellable = true)
    private void onGetGroup(Category category, CallbackInfoReturnable<List<Module>> cir) {
        List<Module> modules = cir.getReturnValue();

        if (modules.size() > 1) {
            modules.sort(Comparator.comparing(m -> m.name.toLowerCase()));
        }

        cir.setReturnValue(modules);
    }
}
