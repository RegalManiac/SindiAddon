package com.RegalManiac.addon.mixin.meteor;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.world.Nuker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Nuker.class, remap = false)
public abstract class NukerMixin {

    @Unique private Setting<Boolean> pauseOnKillaura;
    @Unique private Setting<Boolean> pauseOnEat;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        Nuker module = (Nuker) (Object) this;
        SettingGroup sgPaused = module.settings.createGroup("Paused");

        pauseOnKillaura = sgPaused.add(new BoolSetting.Builder()
            .name("paused-on-kill-aura")
            .description("Pauses nuker when Kill Aura has a target.")
            .defaultValue(true)
            .build()
        );

        pauseOnEat = sgPaused.add(new BoolSetting.Builder()
            .name("paused-on-eat")
            .description("Pauses nuker when AutoEat is eating.")
            .defaultValue(true)
            .build()
        );
    }

    @Inject(method = "onTickPre", at = @At("HEAD"), cancellable = true)
    private void onTickPre(TickEvent.Pre event, CallbackInfo ci) {
        if (pauseOnKillaura != null && pauseOnKillaura.get()) {
            KillAura ka = Modules.get().get(KillAura.class);
            if (ka != null && ka.isActive() && ka.getTarget() != null) {
                ci.cancel();
                return;
            }
        }

        if (pauseOnEat != null && pauseOnEat.get()) {
            AutoEat eat = Modules.get().get(AutoEat.class);
            if (eat != null && eat.isActive() && eat.eating) {
                ci.cancel();
                return;
            }
        }
    }
}
