package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.managers.LobbyManager;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.combat.AutoLog;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutoLog.class, remap = false)
public abstract class AutoLogMixin {
    @Final
    @Shadow private Setting<Boolean> toggleOff;
    @Shadow private void disconnect(String reason) {}

    @Unique private Setting<Boolean> logAtYToggle;
    @Unique private Setting<Integer> yLevel;

    @Unique private int postLobbyTimer = 0;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        AutoLog autoLog = (AutoLog) (Object) this;

        logAtYToggle = autoLog.settings.getDefaultGroup().add(new BoolSetting.Builder()
            .name("log-at-y")
            .description("Disconnect from the server when reaching a specific height.")
            .defaultValue(false)
            .build()
        );

        yLevel = autoLog.settings.getDefaultGroup().add(new IntSetting.Builder()
            .name("y-level")
            .description("The Y-level at or below which to disconnect.")
            .defaultValue(120)
            .sliderRange(-64, 320)
            .visible(() -> logAtYToggle != null && logAtYToggle.get())
            .build()
        );
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void onTickInject(TickEvent.Post event, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            postLobbyTimer = 0;
            return;
        }

        if (LobbyManager.isInLobby()) {
            postLobbyTimer = 30;
            ci.cancel();
            return;
        }

        if (postLobbyTimer > 0) {
            postLobbyTimer--;
            ci.cancel();
            return;
        }

        if (logAtYToggle != null && logAtYToggle.get()) {
            if (mc.player.age > 60 && mc.player.getY() <= yLevel.get()) {
                disconnect("Logged out at Y: " + (int) mc.player.getY());
                if (toggleOff.get()) ((AutoLog) (Object) this).toggle();
                ci.cancel();
            }
        }
    }
}
