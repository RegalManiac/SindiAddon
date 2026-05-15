package com.RegalManiac.addon.modules.player;

import com.RegalManiac.addon.events.TotemPopEvent;
import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.RegalManiac.addon.utils.FakePlayerUtils;

public class FakePlayerPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> copyInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("Copy-Inventory")
        .description("Copy player inventory")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> record = this.sgGeneral.add(new BoolSetting.Builder()
        .name("Record")
        .description("Record player")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> play = this.sgGeneral.add(new BoolSetting.Builder()
        .name("Play")
        .description("Allow movement")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoTotem = this.sgGeneral.add(new BoolSetting.Builder()
        .name("AutoTotem")
        .description("Auto-totem")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> regenDelay = sgGeneral.add(new IntSetting.Builder()
        .name("Regen-Delay")
        .description("Seconds before fully healing after taking damage.")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private static final Identifier CUSTOM_SKIN_ID = Identifier.of("sindiaddon", "textures/player/skin.png");
    private final List<PlayerState> positions = new ArrayList<>();
    private OtherClientPlayerEntity fakePlayer;
    private int movementTick;
    private int deathTime;
    private RegistryKey<World> spawnDimension;
    private int iFrames = 0;
    private float lastHurtDamage = 0.0F;
    private int ticksSinceLastHurt = 0;

    public FakePlayerPlus() {
        super(Categories.Player, "Fake-Player-+", "Spawns a client-side fake player for testing usages.");
    }

    public void onActivate() {
        assert this.mc.world != null;
        this.spawnDimension = this.mc.world.getRegistryKey();
        this.iFrames = 0;
        this.lastHurtDamage = 0.0F;
        this.ticksSinceLastHurt = 0;

        this.fakePlayer = new OtherClientPlayerEntity(this.mc.world, new GameProfile(UUID.fromString("66123666-6666-6666-6666-666666666600"), "Sindi")) {
            @Override
            public SkinTextures getSkin() {
                AssetInfo.TextureAsset skinAsset = new AssetInfo.SkinAssetInfo(CUSTOM_SKIN_ID, "");
                return new SkinTextures(skinAsset, null, null, PlayerSkinType.WIDE, true);
            }
        };

        this.fakePlayer.copyPositionAndRotation(this.mc.player);
        this.fakePlayer.setHeadYaw(this.mc.player.getHeadYaw());
        this.fakePlayer.setBodyYaw(this.mc.player.getBodyYaw());

        if (this.copyInventory.get()) {
            this.fakePlayer.getInventory().clone(this.mc.player.getInventory());

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                this.fakePlayer.equipStack(slot, this.mc.player.getEquippedStack(slot).copy());
            }
        }

        this.mc.world.addEntity(this.fakePlayer);
    }

    @EventHandler(priority = 200)
    public void onPacketReceive(Receive e) {
        if (e.packet instanceof ExplosionS2CPacket explosion && this.fakePlayer != null) {
            float finalDamage = (float) DamageUtils.explosionDamage(
                this.fakePlayer,
                this.fakePlayer.getEntityPos(),
                this.fakePlayer.getBoundingBox(),
                new Vec3d(explosion.center().x, explosion.center().y, explosion.center().z),
                6.0F,
                DamageUtils.HIT_FACTORY
            );

            applyDamageToFakePlayer(finalDamage, this.mc.world.getDamageSources().explosion(null), true);
        }
    }

    @EventHandler
    public void onTick(Pre event) {
        if (this.mc.world == null || this.mc.world.getRegistryKey() != spawnDimension) {
            this.toggle();
            return;
        }

        if (this.iFrames > 0) {
            this.iFrames--;
        }

        if (this.fakePlayer != null && !this.fakePlayer.isDead()) {
            this.ticksSinceLastHurt++;
            if (this.ticksSinceLastHurt >= this.regenDelay.get() * 20) {
                if (this.fakePlayer.getHealth() < this.fakePlayer.getMaxHealth()) {
                    this.fakePlayer.setHealth(this.fakePlayer.getMaxHealth());
                }
            }
        }

        if (this.record.get()) {
            this.positions.add(new PlayerState(
                this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ(),
                this.mc.player.getYaw(), this.mc.player.getPitch(),
                this.mc.player.getHeadYaw(), this.mc.player.getBodyYaw(),
                this.mc.player.getPose()
            ));
        } else {
            if (this.fakePlayer != null) {
                if (this.play.get() && !this.positions.isEmpty()) {
                    this.movementTick++;
                    if (this.movementTick >= this.positions.size()) {
                        this.movementTick = 0;
                        return;
                    }

                    PlayerState p = this.positions.get(this.movementTick);

                    this.fakePlayer.setYaw(p.yaw);
                    this.fakePlayer.setPitch(p.pitch);
                    this.fakePlayer.setHeadYaw(p.headYaw);
                    this.fakePlayer.setBodyYaw(p.bodyYaw);
                    this.fakePlayer.setPose(p.pose);

                    this.fakePlayer.updateTrackedPosition(p.x, p.y, p.z);
                    this.fakePlayer.updateTrackedPositionAndAngles(new Vec3d(p.x, p.y, p.z), p.yaw, p.pitch);
                } else {
                    this.movementTick = 0;
                }

                if (this.autoTotem.get() && this.fakePlayer.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                    this.fakePlayer.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                }

                if (this.fakePlayer.isDead()) {
                    this.deathTime++;
                    if (this.deathTime > 10) {
                        this.toggle();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onAttack(AttackEntityEvent e) {
        if (this.fakePlayer != null && e.entity == this.fakePlayer) {
            float damage = FakePlayerUtils.getHitDamage(this.mc.player, this.fakePlayer);
            FakePlayerUtils.playHitSound(this.mc.player, this.fakePlayer, damage);
            applyDamageToFakePlayer(damage, this.mc.world.getDamageSources().playerAttack(this.mc.player), false);
        }
    }

    private void applyDamageToFakePlayer(float baseDamage, DamageSource source, boolean isDamageAlreadyReduced) {
        if (this.fakePlayer == null || this.fakePlayer.isDead() || baseDamage <= 0) return;
        this.ticksSinceLastHurt = 0;

        float damage = baseDamage;

        if (!isDamageAlreadyReduced) {
            damage = FakePlayerUtils.calcDamageReduction(this.fakePlayer, baseDamage, source);
        }

        if (this.iFrames > 10) {
            if (damage <= this.lastHurtDamage) return;
            damage = damage - this.lastHurtDamage;
            this.lastHurtDamage += damage;
        } else {
            this.lastHurtDamage = damage;
            this.iFrames = 20;
            this.fakePlayer.hurtTime = 10;
            this.fakePlayer.onDamaged(source);

            this.mc.world.playSound(
                this.mc.player,
                this.fakePlayer.getX(), this.fakePlayer.getY(), this.fakePlayer.getZ(),
                SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0F, 1.0F
            );
        }

        float absorption = this.fakePlayer.getAbsorptionAmount();
        if (damage > absorption) {
            damage -= absorption;
            this.fakePlayer.setAbsorptionAmount(0.0F);
        } else {
            this.fakePlayer.setAbsorptionAmount(absorption - damage);
            damage = 0.0F;
        }

        if (damage > 0.0F) {
            this.fakePlayer.setHealth(this.fakePlayer.getHealth() - damage);
        }

        if (this.fakePlayer.isDead() && this.fakePlayer.tryUseDeathProtector(source)) {
            this.fakePlayer.setHealth(1.0F);
            this.fakePlayer.clearStatusEffects();
            this.fakePlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
            this.fakePlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));
            this.fakePlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));

            MeteorClient.EVENT_BUS.post(new TotemPopEvent(this.fakePlayer, 1));
            new EntityStatusS2CPacket(this.fakePlayer, (byte) 35).apply(this.mc.player.networkHandler);

            this.mc.world.playSound(this.fakePlayer, this.fakePlayer.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) this.toggle();
    }

    @Override
    public void onDeactivate() {
        if (this.fakePlayer != null) {
            this.fakePlayer.setHealth(0.0F);
            this.mc.world.playSound(this.mc.player, this.fakePlayer.getBlockPos(), SoundEvents.ENTITY_PLAYER_DEATH, SoundCategory.PLAYERS, 1.0F, 1.0F);
            this.fakePlayer.onDeath(this.mc.world.getDamageSources().generic());
            OtherClientPlayerEntity corpse = this.fakePlayer;
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}

                if (this.mc.world != null) {
                    this.mc.execute(corpse::discard);
                }
            }).start();

            this.fakePlayer = null;
            this.positions.clear();
            this.deathTime = 0;
            this.spawnDimension = null;
        }
    }

    private record PlayerState(double x, double y, double z, float yaw, float pitch, float headYaw, float bodyYaw, EntityPose pose) {}
}
