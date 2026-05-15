package com.RegalManiac.addon.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

import java.util.Set;

public class AutoMountPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("The delay in ticks between mounting attempts.")
        .defaultValue(10)
        .min(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum distance to interact with an entity.")
        .defaultValue(4.0)
        .min(0.0)
        .sliderMax(6.0)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Faces the entity before mounting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreSneaking = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-sneaking")
        .description("Continue mounting even if you are sneaking.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to automatically mount.")
        .filter(EntityUtils::isRideable)
        .build()
    );

    private int timer = 0;

    public AutoMountPlus() {
        super(Categories.World, "auto-mount-+", "Automatically mounts selected entities.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {

        if (mc.player.hasVehicle()) {
            timer = 0;
            return;
        }

        if (!ignoreSneaking.get() && mc.player.isSneaking()) return;

        if (timer > 0) {
            timer--;
            return;
        }

        Entity target = null;
        double closest = range.get();

        for (Entity entity : mc.world.getEntities()) {
            if (!entities.get().contains(entity.getType())) continue;

            double distance = mc.player.distanceTo(entity);
            if (distance <= closest) {
                target = entity;
                closest = distance;
            }
        }

        if (target == null) return;

        if (rotate.get()) {
            Entity finalTarget = target;
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), 50, () -> interact(finalTarget));
        } else {
            interact(target);
        }

        timer = delay.get();
    }

    private void interact(Entity entity) {
        EntityHitResult hitResult = new EntityHitResult(entity, entity.getBoundingBox().getCenter());

        mc.interactionManager.interactEntityAtLocation(mc.player, entity, hitResult, Hand.MAIN_HAND);
        mc.interactionManager.interactEntity(mc.player, entity, Hand.MAIN_HAND);

        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
