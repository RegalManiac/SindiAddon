package com.RegalManiac.addon.modules.movement;

import com.RegalManiac.addon.events.JumpInputEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class ElytraUtils extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> forceElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("force-elytra")
        .description("Equips elytra immediately when the module is enabled.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoGlide = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-gliding")
        .description("Automatically jump, deploy elytra and fly.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> boostDelay = sgGeneral.add(new IntSetting.Builder()
        .name("firework-delay")
        .description("Delay between firework uses in ticks.")
        .defaultValue(20)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> elytraReplace = sgGeneral.add(new BoolSetting.Builder()
        .name("elytra-replace")
        .description("Automatically replaces elytra before it breaks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> replaceDurability = sgGeneral.add(new IntSetting.Builder()
        .name("replace-durability")
        .description("The durability at which the elytra will be swapped.")
        .defaultValue(10)
        .min(1)
        .sliderMax(50)
        .visible(elytraReplace::get)
        .build()
    );

    private final Setting<Boolean> minSpeedToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("min-speed-fire")
        .description("Only use fireworks when speed drops below the specified value.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> minSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-speed")
        .description("The minimum speed to maintain.")
        .defaultValue(1.5)
        .min(0.1)
        .sliderMax(3.0)
        .visible(minSpeedToggle::get)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Enable rotation locking.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> rotationMinY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to start rotating.")
        .defaultValue(30)
        .sliderRange(-64, 320)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Boolean> yawToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("yaw-lock")
        .description("Lock your yaw rotation.")
        .defaultValue(false)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Double> yawAngle = sgGeneral.add(new DoubleSetting.Builder()
        .name("yaw-angle")
        .description("Yaw angle in degrees.")
        .defaultValue(0)
        .sliderMax(360)
        .visible(() -> rotate.get() && yawToggle.get())
        .build()
    );

    private final Setting<Boolean> pitchToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("pitch-lock")
        .description("Lock your pitch rotation.")
        .defaultValue(false)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Double> pitchAngle = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch-angle")
        .description("Pitch angle in degrees.")
        .defaultValue(0)
        .range(-90, 90)
        .sliderRange(-90, 90)
        .visible(() -> rotate.get() && pitchToggle.get())
        .build()
    );

    private enum Phase {
        EQUIP,
        JUMP,
        WAIT_FALL,
        DEPLOY,
        FLY
    }

    private Phase phase = Phase.FLY;
    public boolean wasPressedByModule = false;
    private int jumpTicks = 0;

    private int fireworkTimer = 0;
    private boolean shouldFirework = false;

    public ElytraUtils() {
        super(Categories.Movement, "elytra-utils", "Various utilities for elytra flight.");
    }

    public boolean isTakingOff() {
        return autoGlide.get() && (phase == Phase.JUMP || phase == Phase.WAIT_FALL || phase == Phase.DEPLOY);
    }

    @Override
    public void onActivate() {
        fireworkTimer = 0;
        shouldFirework = false;
        wasPressedByModule = false;
        jumpTicks = 0;
        phase = autoGlide.get() ? Phase.EQUIP : Phase.FLY;

        if (forceElytra.get() && mc.player != null) {
            ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestStack.getItem() != Items.ELYTRA) {
                FindItemResult elytra = InvUtils.find(itemStack -> itemStack.getItem() == Items.ELYTRA);
                if (elytra.found()) {
                    InvUtils.move().from(elytra.slot()).toArmor(2);
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null && wasPressedByModule) {
            mc.options.jumpKey.setPressed(false);
        }
        wasPressedByModule = false;
        jumpTicks = 0;
        phase = Phase.FLY;
    }

    @EventHandler
    private void onJumpInput(JumpInputEvent event) {
        if (isTakingOff()) {
            event.setJumping(wasPressedByModule);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (fireworkTimer > 0) fireworkTimer--;

        if (forceElytra.get()) {
            ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestStack.getItem() != Items.ELYTRA) {
                FindItemResult elytra = InvUtils.find(itemStack -> itemStack.getItem() == Items.ELYTRA);
                if (elytra.found()) {
                    InvUtils.move().from(elytra.slot()).toArmor(2);
                }
            }
        }

        if (rotate.get() && mc.player.getY() >= rotationMinY.get()) {
            if (yawToggle.get()) {
                float yaw = yawAngle.get().floatValue();
                mc.player.setYaw(yaw);
                mc.player.headYaw = yaw;
                mc.player.bodyYaw = yaw;
            }
            if (pitchToggle.get()) {
                mc.player.setPitch(pitchAngle.get().floatValue());
            }
        }

        if (elytraReplace.get()) {
            ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestStack.getItem() == Items.ELYTRA) {
                int currentDurability = chestStack.getMaxDamage() - chestStack.getDamage();
                if (currentDurability <= replaceDurability.get()) {
                    FindItemResult newElytra = InvUtils.find(itemStack ->
                        itemStack.getItem() == Items.ELYTRA &&
                            (itemStack.getMaxDamage() - itemStack.getDamage()) > replaceDurability.get()
                    );
                    if (newElytra.found()) InvUtils.move().from(newElytra.slot()).toArmor(2);
                }
            }
        }

        checkAutoGlide();

        if (mc.player.isGliding()) {
            double currentSpeed = mc.player.getVelocity().length();
            boolean isFastEnough = minSpeedToggle.get() && currentSpeed >= minSpeed.get();
            if (!isFastEnough && fireworkTimer <= 0) {
                shouldFirework = true;
            }
        }
    }

    private void checkAutoGlide() {
        if (!autoGlide.get() || mc.player == null) {
            phase = Phase.FLY;
            return;
        }

        if (mc.player.isGliding()) {
            if (phase != Phase.FLY) {
                if (wasPressedByModule) {
                    mc.options.jumpKey.setPressed(false);
                    wasPressedByModule = false;
                }
                phase = Phase.FLY;
                shouldFirework = true;
                jumpTicks = 0;
            }
            return;
        }

        if (mc.player.isTouchingWater() || mc.player.isInLava() || mc.player.isClimbing()) {
            return;
        }

        if (phase == Phase.FLY) {
            phase = Phase.EQUIP;
            jumpTicks = 0;
            if (wasPressedByModule) {
                mc.options.jumpKey.setPressed(false);
                wasPressedByModule = false;
            }
        }

        switch (phase) {
            case EQUIP -> tickEquip();
            case JUMP -> tickJumpSequence();
            case WAIT_FALL -> tickWaitFall();
            case DEPLOY -> tickDeploy();
            case FLY -> {}
        }
    }

    private void tickEquip() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.isOf(Items.ELYTRA)) {
            phase = Phase.JUMP;
            return;
        }

        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;

        FindItemResult elytra = InvUtils.find(Items.ELYTRA);
        if (!elytra.found()) {
            phase = Phase.FLY;
            return;
        }

        int elytraSlot = elytra.slot();
        int containerSlot = elytraSlot < 9 ? elytraSlot + 36 : elytraSlot;

        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, containerSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, containerSlot, 0, SlotActionType.PICKUP, mc.player);

        phase = Phase.JUMP;
    }

    private void tickJumpSequence() {
        if (jumpTicks == 0) {
            if (mc.player.isOnGround()) {
                mc.options.jumpKey.setPressed(true);
                wasPressedByModule = true;
                jumpTicks = 1;
            } else {
                mc.options.jumpKey.setPressed(true);
                wasPressedByModule = true;
                jumpTicks = 10;
            }
        } else if (jumpTicks == 1) {
            mc.options.jumpKey.setPressed(false);
            wasPressedByModule = false;
            jumpTicks = 2;
        } else if (jumpTicks >= 2 && jumpTicks < 10) {
            if (!mc.player.isOnGround()) {
                mc.options.jumpKey.setPressed(true);
                wasPressedByModule = true;
                jumpTicks = 10;
            } else {
                jumpTicks++;
            }
        } else if (jumpTicks == 10) {
            mc.options.jumpKey.setPressed(false);
            wasPressedByModule = false;
            phase = Phase.WAIT_FALL;
            jumpTicks = 11;
        } else if (jumpTicks > 10) {
            jumpTicks++;
            if (jumpTicks > 30) {
                jumpTicks = 0;
            }
        }
    }

    private void tickWaitFall() {
        if (mc.player.isOnGround()) {
            phase = Phase.JUMP;
            return;
        }

        mc.options.jumpKey.setPressed(false);
        wasPressedByModule = false;

        if (mc.player.getVelocity().y < -0.05) {
            phase = Phase.DEPLOY;
        }
    }

    private void tickDeploy() {
        if (!wasPressedByModule) {
            mc.options.jumpKey.setPressed(true);
            wasPressedByModule = true;
        } else {
            mc.options.jumpKey.setPressed(false);
            wasPressedByModule = false;
            phase = Phase.WAIT_FALL;
        }
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
        if (!firework.found()) {
            ChatUtils.info("No fireworks available! Disabling.");
            toggle();
            return;
        }

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        int fireworkSlot = firework.slot();

        if (fireworkSlot == currentSlot) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            fireworkTimer = boostDelay.get();
            return;
        }

        int containerSlot = fireworkSlot < 9 ? fireworkSlot + 36 : fireworkSlot;

        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, containerSlot, currentSlot, SlotActionType.SWAP, mc.player);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, containerSlot, currentSlot, SlotActionType.SWAP, mc.player);

        fireworkTimer = boostDelay.get();
    }
}
