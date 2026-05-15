package com.RegalManiac.addon.modules.misc;

import meteordevelopment.meteorclient.events.entity.player.ItemUseCrosshairTargetEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;

/**
 * made by NyaHub
 */
public class RaidfarmAutomation extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder() // delay in minutes
        .name("delay")
        .description("Bottle drink delay (minutes).")
        .defaultValue(5.0d)
        .range(0.5d, 10.0d)
        .build()
    );

    private  final  Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder().name("Debug").description("Output log for all actions").defaultValue(true).build());

    public enum State{
        DISABLED,
        IDLE,
        DRINK,
        DRINKING,
        START_WAIT
    }

    public State currentState = State.DISABLED;
    public Integer timer = 0;
    private int oldSlot = -1;
    private int total = 0;

    public RaidfarmAutomation() {
        super(Categories.Misc, "raidfarm-automation", "An raid farm automation tool. Made by NyaHub.");
    }

    @Override
    public void onActivate() {
        currentState = State.START_WAIT;
        timer = 0;
        info("Started");
    }

    @Override
    public void onDeactivate() {
        currentState = State.DISABLED;
        stopDrinking();
        info("Stoped");
    }

    @EventHandler
    private void onItemUseCrosshairTarget(ItemUseCrosshairTargetEvent event) {
        if (currentState == State.DRINKING || currentState == State.DRINK) event.target = null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        findBottle();

        switch (currentState){
            case START_WAIT -> {
                timer += 1;
                if (timer >  20){
                    if(debug.get()) info("Start drinking");
                    currentState = State.DRINK;
                    timer = 0;
                }
            }
            case IDLE -> {
                timer += 1;
                if(timer > delay.get() * 60 * 20){
                    currentState = State.DRINK;
                    timer = 0;
                }
            }
            case DRINK -> {
                oldSlot = mc.player.getInventory().getSelectedSlot();
                executeDrinkAction();
                currentState = State.DRINKING;
                timer = 0;
            }
            case DRINKING -> {
                if (hasEffect()) {
                    if (debug.get()) info("Bad Omen found, stopping.");
                    stopDrinking();
                    return;
                }
                if (!mc.player.isUsingItem()) {
                    executeDrinkAction();
                } else {
                    mc.options.useKey.setPressed(true);
                }
            }
        }
    }

    private boolean hasEffect() {
        return mc.player != null && (mc.player.hasStatusEffect(StatusEffects.BAD_OMEN) || mc.player.hasStatusEffect(StatusEffects.RAID_OMEN));
    }

    private void executeDrinkAction() {
        FindItemResult item = findBottle();
        if (item.found()) {
            if (mc.player.getInventory().getSelectedSlot() != item.slot()) {
                InvUtils.swap(item.slot(), false);
            }

            mc.options.useKey.setPressed(true);

            if (mc.interactionManager != null) {
                mc.interactionManager.interactItem(mc.player, mc.player.getActiveHand());
            }
        } else {
            error("No bottles found!");
            stopDrinking();
            toggle();
        }
    }

    private void stopDrinking() {
        mc.options.useKey.setPressed(false);
        if (mc.interactionManager != null) {
            mc.interactionManager.stopUsingItem(mc.player);
        }

        if (oldSlot != -1) {
            InvUtils.swap(oldSlot, false);
            oldSlot = -1;
        }
        currentState = State.IDLE;
        timer = 0;
    }

    private FindItemResult findBottle(){
        FindItemResult item = InvUtils.findInHotbar(Items.OMINOUS_BOTTLE);
        if(item.found()) total = item.count();
        else total = 0;
        return item;
    }

    public String getInfoString() {
        return String.format("total: %d, status: %s", total, getStatusString());
    }

    private String getStatusString(){
        if(currentState == State.IDLE) {
            return formatTimeLeft(delay.get(), timer);//String.format("%.1f sec", (delay.get() * 60) - (timer / 20));
        }
        if(currentState == State.START_WAIT){
            return "wait";
        }
        return "drinking";
    }

    public static String formatTimeLeft(double minutesLeft, int ticksPassed) {
        double totalSecondsLeft = minutesLeft * 60 - ticksPassed / 20;

        if (totalSecondsLeft <= 0) {
            return "0:00";
        } else {
            int minutes = (int) (totalSecondsLeft / 60);
            int seconds = (int) (totalSecondsLeft % 60);
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}
