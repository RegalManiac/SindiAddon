package com.RegalManiac.addon.modules.movement;

import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class Pitch40 extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRotation = settings.createGroup("Rotation");
    private final SettingGroup sgFirework = settings.createGroup("Firework");

    public final Setting<Double> targetHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-height")
        .description("Minimum peak height. If peak falls below this, fireworks will be used on next climb.")
        .defaultValue(320.0)
        .sliderRange(-64, 320)
        .build()
    );

    public final Setting<Double> boundGap = sgGeneral.add(new DoubleSetting.Builder()
        .name("bound-gap")
        .description("The height difference between upper and lower bounds during normal gliding.")
        .defaultValue(60.0)
        .sliderRange(20, 120)
        .build()
    );

    public final Setting<Double> rotationSpeedUp = sgRotation.add(new DoubleSetting.Builder()
        .name("rotation-speed-up")
        .description("Head rotation speed when pitching up (degrees per tick).")
        .defaultValue(5.45)
        .min(0.1)
        .sliderRange(1, 20)
        .build()
    );

    public final Setting<Double> rotationSpeedDown = sgRotation.add(new DoubleSetting.Builder()
        .name("rotation-speed-down")
        .description("Head rotation speed when pitching down (degrees per tick).")
        .defaultValue(0.90)
        .min(0.1)
        .sliderRange(0.1, 5)
        .build()
    );

    public final Setting<Boolean> autoFirework = sgFirework.add(new BoolSetting.Builder()
        .name("auto-firework")
        .description("Automatically uses fireworks when climbing to target height.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> fireworkDelay = sgFirework.add(new IntSetting.Builder()
        .name("firework-delay")
        .description("Delay between firework uses (in ticks).")
        .defaultValue(50)
        .min(1)
        .sliderRange(1, 100)
        .visible(autoFirework::get)
        .build()
    );

    private double descentStartY = 0.0;
    private double lowerBounds = 0.0;
    private boolean pitchingUp = false;
    private boolean hasAscended = false;
    private boolean needBoost = false;
    private int fireworkTimer = 0;
    private boolean shouldFirework = false;

    public Pitch40() {
        super(Categories.Movement, "Pitch40", "Standalone Pitch40 flight logic for 2b2t.");
    }

    @Override
    public void onActivate() {
        fireworkTimer = 0;
        shouldFirework = false;

        if (mc.player != null) {
            double currentY = mc.player.getY();
            descentStartY = currentY;
            lowerBounds = descentStartY - boundGap.get();
            pitchingUp = true;
            hasAscended = false;
            needBoost = currentY < targetHeight.get();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (!mc.player.isGliding()) return;
        if (fireworkTimer > 0) fireworkTimer--;

        double currentY = mc.player.getY();
        float currentPitch = mc.player.getPitch();

        if (pitchingUp) {
            if (mc.player.getVelocity().y > 0) {
                hasAscended = true;
            }

            if (autoFirework.get() && currentY < (lowerBounds - 10.0)) {
                needBoost = true;
            }

            if (needBoost && autoFirework.get() && fireworkTimer <= 0 && currentY < targetHeight.get()) {
                shouldFirework = true;
            }

            boolean pitchIsUp = currentPitch <= -30.0f;
            boolean hitApex = hasAscended && mc.player.getVelocity().y <= 0;
            boolean reachedTarget = currentY >= targetHeight.get();

            if (pitchIsUp && (hitApex || reachedTarget)) {
                pitchingUp = false;
                descentStartY = currentY;
                lowerBounds = descentStartY - boundGap.get();
                needBoost = currentY < targetHeight.get();
            }
        } else {
            boolean hitLowerBound = currentY <= lowerBounds;
            boolean exceededGap = (descentStartY - currentY) >= boundGap.get();

            if (hitLowerBound || exceededGap) {
                pitchingUp = true;
                hasAscended = false;
            }
        }

        float targetPitch = pitchingUp ? -40.0f : 40.0f;
        float speed = pitchingUp ? rotationSpeedUp.get().floatValue() : rotationSpeedDown.get().floatValue();
        float newPitch = MathHelper.stepTowards(currentPitch, targetPitch, speed);
        mc.player.setPitch(newPitch);
    }

    @EventHandler
    private void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
        if (shouldFirework && mc.player != null) {
            useFireworkSilent();
            shouldFirework = false;
        }
    }

    private void useFireworkSilent() {
        FindItemResult firework = InvUtils.find(Items.FIREWORK_ROCKET);
        if (!firework.found()) return;

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        int fireworkSlot = firework.slot();

        if (fireworkSlot == currentSlot) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            fireworkTimer = fireworkDelay.get();
            return;
        }

        int containerSlot = fireworkSlot < 9 ? fireworkSlot + 36 : fireworkSlot;

        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, containerSlot, currentSlot, SlotActionType.SWAP, mc.player);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, containerSlot, currentSlot, SlotActionType.SWAP, mc.player);

        fireworkTimer = fireworkDelay.get();
    }
}
