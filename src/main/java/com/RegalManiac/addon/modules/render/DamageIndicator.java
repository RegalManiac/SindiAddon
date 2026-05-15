package com.RegalManiac.addon.modules.render;

import com.RegalManiac.addon.utils.FakePlayerUtils;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerManager;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class DamageIndicator extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Which entities to show damage for.")
        .defaultValue(Collections.singleton(EntityType.PLAYER))
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the damage text.")
        .defaultValue(new SettingColor(255, 25, 25))
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The scale of the damage text.")
        .defaultValue(1.5)
        .min(0.5)
        .build()
    );

    private final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Doesn't show your own damage.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> randomOffset = sgGeneral.add(new BoolSetting.Builder()
        .name("random-offset")
        .description("Randomly offsets the damage to prevent stacking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> yOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-offset")
        .description("The vertical offset above the entity's head.")
        .defaultValue(0.7)
        .min(0.0)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Double> duration = sgGeneral.add(new DoubleSetting.Builder()
        .name("duration")
        .description("How long the damage text stays on screen (in seconds).")
        .defaultValue(1.0)
        .min(0.1)
        .build()
    );

    private final Map<Integer, Float> healthMap = new HashMap<>();
    private final Map<Integer, Long> lastFakeAttackTimes = new HashMap<>();
    private final List<DamagePop> pops = new CopyOnWriteArrayList<>();

    public DamageIndicator() {
        super(Categories.Render, "damage-indicator", "Displays damage dealt to entities.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null) return;

        pops.removeIf(pop -> System.currentTimeMillis() - pop.startTime > duration.get() * 1000);

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity living && !(living instanceof FakePlayerEntity)) {
                checkDamage(living);
            }
        }

        drawPops(event);
    }

    @EventHandler(priority = 200)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null) return;

        if (event.packet instanceof ExplosionS2CPacket explosion) {
            Vec3d expPos = new Vec3d(explosion.center().x, explosion.center().y, explosion.center().z);
            for (FakePlayerEntity fake : FakePlayerManager.getFakePlayers()) {
                float damage = DamageUtils.explosionDamage(
                    fake, fake.getEntityPos(), fake.getBoundingBox(), expPos, 6.0F, DamageUtils.HIT_FACTORY
                );
                if (damage > 0.1f) pops.add(new DamagePop(damage, fake, System.currentTimeMillis()));
            }
        }

        if (event.packet instanceof ExplosionS2CPacket || event.packet instanceof EntityStatusS2CPacket) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof LivingEntity living && !(living instanceof FakePlayerEntity)) {
                    checkDamage(living);
                }
            }
        }
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (event.entity instanceof FakePlayerEntity fake) {
            long now = System.currentTimeMillis();

            if (now - lastFakeAttackTimes.getOrDefault(fake.getId(), 0L) < 100) {
                return;
            }
            lastFakeAttackTimes.put(fake.getId(), now);

            float rawDamage = FakePlayerUtils.getHitDamage(mc.player, fake);
            float finalDamage = FakePlayerUtils.calcDamageReduction(fake, rawDamage, mc.world.getDamageSources().playerAttack(mc.player));

            if (finalDamage > 0.1f) {
                pops.add(new DamagePop(finalDamage, fake, now));
            }
        }
    }

    private void checkDamage(LivingEntity entity) {
        if (entity == mc.player && ignoreSelf.get()) return;
        if (!entities.get().contains(entity.getType()) && !(entity instanceof net.minecraft.client.network.OtherClientPlayerEntity)) return;

        float currentHp = entity.getHealth() + entity.getAbsorptionAmount();
        float lastHp = healthMap.getOrDefault(entity.getId(), currentHp);

        if (currentHp < lastHp) {
            float damage = lastHp - currentHp;
            if (damage > 0.1f) {
                pops.add(new DamagePop(damage, entity, System.currentTimeMillis()));
            }
        }

        healthMap.put(entity.getId(), currentHp);
    }

    private void drawPops(Render2DEvent event) {
        if (pops.isEmpty()) return;
        TextRenderer tr = TextRenderer.get();
        org.joml.Vector3d renderPos = new org.joml.Vector3d();

        for (DamagePop pop : pops) {
            long time = System.currentTimeMillis() - pop.startTime;
            double progress = time / (duration.get() * 1000.0);

            meteordevelopment.meteorclient.utils.Utils.set(renderPos, pop.entity, event.tickDelta);

            double offX = randomOffset.get() ? pop.offsetX : 0;
            double offZ = randomOffset.get() ? pop.offsetZ : 0;

            renderPos.add(offX, pop.entity.getHeight() + yOffset.get() + (progress * 0.8), offZ);

            if (NametagUtils.to2D(renderPos, scale.get())) {
                NametagUtils.begin(renderPos);
                String text = String.format("%.1f", pop.damage);
                int alpha = (int) (color.get().a * (1.0 - progress));
                tr.beginBig();
                tr.render(text, -(tr.getWidth(text) / 2.0), -tr.getHeight(), new Color(color.get().r, color.get().g, color.get().b, Math.max(0, alpha)), true);
                tr.end();
                NametagUtils.end();
            }
        }
    }

    private static class DamagePop {
        float damage;
        Entity entity;
        long startTime;
        double offsetX, offsetZ;

        public DamagePop(float damage, Entity entity, long startTime) {
            this.damage = damage;
            this.entity = entity;
            this.startTime = startTime;
            this.offsetX = ThreadLocalRandom.current().nextDouble(-0.7, 0.7);
            this.offsetZ = ThreadLocalRandom.current().nextDouble(-0.7, 0.7);
        }
    }
}
