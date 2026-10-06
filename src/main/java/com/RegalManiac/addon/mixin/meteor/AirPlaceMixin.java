package com.RegalManiac.addon.mixin.meteor;

import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.player.AirPlace;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AirPlace.class, remap = false, priority = 1001)
public class AirPlaceMixin extends Module {
    public AirPlaceMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Final
    @Shadow
    private final SettingGroup sgRange = settings.getGroup("Range");

    @Unique
    @SuppressWarnings("unchecked")
    private final Setting<Boolean> customRange = (Setting<Boolean>) sgRange.get("custom-range");

    @Unique
    @SuppressWarnings("unchecked")
    private final Setting<Double> range = (Setting<Double>) sgRange.get("range");

    @Unique
    private final Setting<Boolean> scrollRange = sgRange.add(new BoolSetting.Builder()
        .name("scroll-range")
        .description("Allows you to change range value using scroll wheel.")
        .defaultValue(false)
        .visible(customRange::get)
        .build()
    );

    @Unique
    private final Setting<Double> scrollSensitivity = sgRange.add(new DoubleSetting.Builder()
        .name("scroll-sensitivity")
        .description("Sensitivity of scrolling.")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(2)
        .visible(() -> customRange.get() && scrollRange.get())
        .build()
    );

    @Unique
    private final Setting<Double> minScrollRange = sgRange.add(new DoubleSetting.Builder()
        .name("minimum-scroll-range")
        .description("Minimum range you can scroll")
        .defaultValue(1)
        .min(0)
        .sliderMin(1)
        .sliderMax(6)
        .visible(() -> customRange.get() && scrollRange.get() && scrollSensitivity.get() > 0)
        .build()
    );

    @Unique
    private final Setting<Double> maxScrollRange = sgRange.add(new DoubleSetting.Builder()
        .name("maximum-scroll-range")
        .description("Maximum range you can scroll")
        .defaultValue(5)
        .min(0)
        .sliderMin(1)
        .sliderMax(6)
        .visible(() -> customRange.get() && scrollRange.get() && scrollSensitivity.get() > 0)
        .build()
    );

    @Unique
    private double rangeValue;

    @Override
    public void onActivate() {
        super.onActivate();
        rangeValue = range.get();
    }

    @Unique
    @EventHandler(priority = EventPriority.LOW)
    private void onMouseScroll(MouseScrollEvent event) {
        if (!customRange.get() || !scrollRange.get()) return;
        if (scrollSensitivity.get() <= 0 || mc.currentScreen != null) return;

        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem) &&
            !(mc.player.getMainHandStack().getItem() instanceof SpawnEggItem)) return;

        rangeValue += event.value * scrollSensitivity.get() * rangeValue;
        if (rangeValue < minScrollRange.get()) rangeValue = minScrollRange.get();
        else if (rangeValue > maxScrollRange.get()) rangeValue = maxScrollRange.get();
        range.set(rangeValue);

        event.cancel();
    }

    @Unique
    private boolean isBlock(ItemStack stack) {
        return stack != null && stack.getItem() instanceof BlockItem;
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void onTick(TickEvent.Pre event, CallbackInfo ci) {
        if (mc.player == null || !isBlock(mc.player.getMainHandStack())) {
            ci.cancel();
        }
    }

    @Inject(method = "onInteractItem", at = @At("HEAD"), cancellable = true)
    private void onInteractItem(InteractItemEvent event, CallbackInfo ci) {
        if (mc.player == null || !isBlock(mc.player.getStackInHand(event.hand))) {
            ci.cancel();
        }
    }

    @Inject(method = "onRender", at = @At("HEAD"), cancellable = true)
    private void onRender(CallbackInfo ci) {
        if (mc.player == null || !isBlock(mc.player.getMainHandStack())) {
            ci.cancel();
        }
    }
}
