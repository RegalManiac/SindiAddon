package com.RegalManiac.addon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import java.util.concurrent.ThreadLocalRandom;

public class AutoJumpPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> wsOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("ws-only")
        .description("Jumps only when moving forward or backward.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay")
        .description("Tick delay between jumps.")
        .defaultValue(1)
        .min(0)
        .sliderMax(5)
        .build()
    );

    private final Setting<Double> humanError = sgGeneral.add(new DoubleSetting.Builder()
        .name("human-error")
        .description("Random chance to skip a tick.")
        .defaultValue(0.05)
        .min(0)
        .max(0.5)
        .build()
    );

    private int delayLeft = 0;
    private boolean wasPressedByModule = false;

    public AutoJumpPlus() {
        super(Categories.Movement, "auto-jump-+", "Legit automatic jumping.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.options == null) return;

        if (!mc.player.isOnGround()) {
            if (wasPressedByModule) {
                mc.options.jumpKey.setPressed(false);
                wasPressedByModule = false;
            }
            return;
        }

        if (mc.player.isSneaking()) return;

        boolean isMovingWS = mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed();
        boolean isMovingAD = mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
        boolean shouldJump = wsOnly.get() ? isMovingWS : (isMovingWS || isMovingAD);

        if (!shouldJump) {
            if (wasPressedByModule) {
                mc.options.jumpKey.setPressed(false);
                wasPressedByModule = false;
            }
            return;
        }

        if (delayLeft > 0) {
            delayLeft--;
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() < humanError.get()) return;

        mc.options.jumpKey.setPressed(true);
        wasPressedByModule = true;
        delayLeft = minDelay.get();
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null && wasPressedByModule) {
            mc.options.jumpKey.setPressed(false);
        }
        wasPressedByModule = false;
    }
}
