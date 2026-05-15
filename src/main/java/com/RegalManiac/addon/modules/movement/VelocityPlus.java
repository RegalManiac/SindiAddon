package com.RegalManiac.addon.modules.movement;

import com.RegalManiac.addon.mixin.accessors.BundleS2CPacketAccessor;
import com.RegalManiac.addon.mixin.accessors.ExplosionS2CPacketAccessor;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.EntityVelocityUpdateS2CPacketAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VelocityPlus extends Module {

    private enum Mode { VANILLA, WALLS, GRIM }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .defaultValue(Mode.WALLS)
        .build()
    );

    public final Setting<Boolean> cancel = sgGeneral.add(new BoolSetting.Builder()
        .name("cancel")
        .description("Completely cancel velocity.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> horizontalPercent = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal")
        .defaultValue(0.0)
        .min(0.0).max(100.0)
        .visible(() -> !cancel.get())
        .build()
    );

    public final Setting<Double> verticalPercent = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical")
        .defaultValue(0.0)
        .min(0.0).max(100.0)
        .visible(() -> !cancel.get())
        .build()
    );

    public final Setting<Boolean> concealMotion = sgGeneral.add(new BoolSetting.Builder()
        .name("conceal")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> entityPush = sgGeneral.add(new BoolSetting.Builder()
        .name("entity-push")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> blockPush = sgGeneral.add(new BoolSetting.Builder()
        .name("block-push")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> fishingRod = sgGeneral.add(new BoolSetting.Builder()
        .name("fishing-rod")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> onlyPhased = sgGeneral.add(new BoolSetting.Builder()
        .name("only-phased")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.GRIM)
        .build()
    );

    public final Setting<Boolean> onlyWhenHeadCovered = sgGeneral.add(new BoolSetting.Builder()
        .name("only-covered-head")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.GRIM)
        .build()
    );

    private boolean pendingConcealment = false;
    private boolean pendingVelocity = false;
    private long lastSetbackTime = 0;
    private float serverYaw = 0f;
    private float serverPitch = 0f;

    public VelocityPlus() {
        super(Categories.Movement, "velocity-+", "Reduces incoming velocity effects (Ported from Nami).");
    }

    @Override
    public void onActivate() {
        pendingVelocity = false;
        pendingConcealment = false;
    }

    @Override
    public void onDeactivate() {
        if (pendingVelocity && mode.get() == Mode.GRIM) {
            sendRotationFix();
        }
        pendingVelocity = false;
        pendingConcealment = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket packet) {
            if (packet.changesLook()) {
                serverYaw = packet.getYaw(serverYaw);
                serverPitch = packet.getPitch(serverPitch);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreTick(TickEvent.Pre event) {
        if (!pendingVelocity) return;
        if (mode.get() == Mode.GRIM) {
            sendRotationFix();
        }
        pendingVelocity = false;
        pendingConcealment = false;
    }

    @EventHandler(priority = EventPriority.MEDIUM)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;
        Packet<?> packet = event.packet;

        if (packet instanceof PlayerPositionLookS2CPacket) {
            lastSetbackTime = System.currentTimeMillis();
            if (concealMotion.get()) {
                pendingConcealment = true;
            }
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket vel) {
            handleVelocityPacket(event, vel);
        } else if (packet instanceof ExplosionS2CPacket explosion) {
            handleExplosionPacket(event, explosion);
        } else if (packet instanceof BundleS2CPacket bundle) {
            handleBundlePacket(event, bundle);
        } else if (packet instanceof EntityStatusS2CPacket status
            && status.getStatus() == EntityStatuses.PULL_HOOKED_ENTITY
            && fishingRod.get()) {
            handleFishHookPacket(event, status);
        }
    }

    private void handleVelocityPacket(PacketEvent.Receive event, EntityVelocityUpdateS2CPacket packet) {
        if (packet.getEntityId() != mc.player.getId()) return;

        if (pendingConcealment && packet.getVelocity().x == 0 && packet.getVelocity().y == 0 && packet.getVelocity().z == 0) {
            pendingConcealment = false;
            return;
        }

        switch (mode.get()) {
            case VANILLA -> processVelocityVanilla(event, packet);
            case WALLS -> processVelocityWalls(event, packet);
            case GRIM -> processVelocityGrim(event, packet);
        }
    }

    private void handleExplosionPacket(PacketEvent.Receive event, ExplosionS2CPacket packet) {
        switch (mode.get()) {
            case VANILLA -> processExplosionVanilla(event, packet);
            case WALLS -> processExplosionWalls(event, packet);
            case GRIM -> processExplosionGrim(event, packet);
        }
    }

    private void handleBundlePacket(PacketEvent.Receive event, BundleS2CPacket bundle) {
        List<Packet<?>> filtered = new ArrayList<>();

        for (Packet<?> packet : bundle.getPackets()) {
            if (packet instanceof ExplosionS2CPacket exp) {
                processBundleExplosion(filtered, exp, event);
            } else if (packet instanceof EntityVelocityUpdateS2CPacket vel) {
                processBundleVelocity(filtered, vel, event);
            } else {
                filtered.add(packet);
            }
        }

        ((BundleS2CPacketAccessor) bundle).sindiaddon$setPackets(filtered);
    }

    private void handleFishHookPacket(PacketEvent.Receive event, EntityStatusS2CPacket status) {
        Entity entity = status.getEntity(mc.world);
        if (entity instanceof FishingBobberEntity hook && hook.getPlayerOwner() == mc.player) {
            event.cancel();
        }
    }

    private void processVelocityVanilla(PacketEvent.Receive event, EntityVelocityUpdateS2CPacket packet) {
        if (cancel.get()) {
            event.cancel();
            return;
        }
        scaleVelocityPacket(packet);
    }

    private void processVelocityWalls(PacketEvent.Receive event, EntityVelocityUpdateS2CPacket packet) {
        if (!isPhased() || (onlyOnGround.get() && !mc.player.isOnGround())) return;
        if (cancel.get()) {
            event.cancel();
            return;
        }
        processVelocityVanilla(event, packet);
    }

    private void processVelocityGrim(PacketEvent.Receive event, EntityVelocityUpdateS2CPacket packet) {
        if (!hasElapsedSinceSetback(100)) return;
        if (onlyPhased.get() && !isPhased()) return;

        if (onlyWhenHeadCovered.get()) {
            BlockPos pos = mc.player.getBlockPos();
            BlockPos target = pos.up(mc.player.isCrawling() ? 1 : 2);
            if (mc.world.getBlockState(target).isAir()) return;
        }

        processVelocityVanilla(event, packet);
        pendingVelocity = true;
    }

    private void processExplosionVanilla(PacketEvent.Receive event, ExplosionS2CPacket packet) {
        if (cancel.get()) {
            event.cancel();
            return;
        }
        scaleExplosionPacket(packet);
    }

    private void processExplosionWalls(PacketEvent.Receive event, ExplosionS2CPacket packet) {
        if (!isPhased()) return;
        if (cancel.get()) {
            event.cancel();
            return;
        }
        processExplosionVanilla(event, packet);
    }

    private void processExplosionGrim(PacketEvent.Receive event, ExplosionS2CPacket packet) {
        if (!hasElapsedSinceSetback(100)) return;
        if (onlyPhased.get() && !isPhased()) return;

        if (onlyWhenHeadCovered.get()) {
            BlockPos pos = mc.player.getBlockPos();
            BlockPos target = pos.up(mc.player.isCrawling() ? 1 : 2);
            if (mc.world.getBlockState(target).isAir()) return;
        }

        processExplosionVanilla(event, packet);
        pendingVelocity = true;
    }

    private void processBundleExplosion(List<Packet<?>> filtered, ExplosionS2CPacket packet, PacketEvent.Receive event) {
        switch (mode.get()) {
            case VANILLA -> {
                if (cancel.get()) { event.cancel(); return; }
                scaleExplosionPacket(packet);
            }
            case WALLS -> {
                if (!isPhased()) { filtered.add(packet); return; }
                if (cancel.get()) { event.cancel(); return; }
                scaleExplosionPacket(packet);
            }
            case GRIM -> {
                if (!hasElapsedSinceSetback(100) || (onlyPhased.get() && !isPhased())) {
                    filtered.add(packet);
                    return;
                }
                if (onlyWhenHeadCovered.get()) {
                    BlockPos target = mc.player.getBlockPos().up(mc.player.isCrawling() ? 1 : 2);
                    if (mc.world.getBlockState(target).isAir()) {
                        filtered.add(packet);
                        return;
                    }
                }
                if (cancel.get()) { event.cancel(); return; }
                pendingVelocity = true;
                return;
            }
        }
        filtered.add(packet);
    }

    private void processBundleVelocity(List<Packet<?>> filtered, EntityVelocityUpdateS2CPacket packet, PacketEvent.Receive event) {
        if (packet.getEntityId() != mc.player.getId()) {
            filtered.add(packet);
            return;
        }

        switch (mode.get()) {
            case VANILLA -> {
                if (cancel.get()) { event.cancel(); return; }
                scaleVelocityPacket(packet);
            }
            case WALLS -> {
                if (!isPhased() || (onlyOnGround.get() && !mc.player.isOnGround())) {
                    filtered.add(packet);
                    return;
                }
                if (cancel.get()) { event.cancel(); return; }
                scaleVelocityPacket(packet);
            }
            case GRIM -> {
                if (!hasElapsedSinceSetback(100) || (onlyPhased.get() && !isPhased())) {
                    filtered.add(packet);
                    return;
                }
                if (onlyWhenHeadCovered.get()) {
                    BlockPos target = mc.player.getBlockPos().up(mc.player.isCrawling() ? 1 : 2);
                    if (mc.world.getBlockState(target).isAir()) {
                        filtered.add(packet);
                        return;
                    }
                }
                pendingVelocity = true;
                return;
            }
        }
        filtered.add(packet);
    }

    private void sendRotationFix() {
        float f = (float) ((Math.random() * 2.0 - 1.0) * 0.001f);
        float f2 = MathHelper.clamp(serverPitch + f, -90.0F, 90.0F);

        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
            mc.player.getX(), mc.player.getY(), mc.player.getZ(),
            serverYaw, f2, mc.player.isOnGround(), mc.player.horizontalCollision
        ));
    }

    private void scaleVelocityPacket(EntityVelocityUpdateS2CPacket packet) {
        Vec3d v = packet.getVelocity();
        Vec3d scaled = new Vec3d(
            v.x * (horizontalPercent.get() / 100.0),
            v.y * (verticalPercent.get() / 100.0),
            v.z * (horizontalPercent.get() / 100.0)
        );
        ((EntityVelocityUpdateS2CPacketAccessor) packet).meteor$setVelocity(scaled);
    }

    private void scaleExplosionPacket(ExplosionS2CPacket packet) {
        ExplosionS2CPacketAccessor accessor = (ExplosionS2CPacketAccessor) (Object) packet;
        accessor.getPlayerKnockback().ifPresent(original -> {
            Vec3d scaled = new Vec3d(
                original.x * (horizontalPercent.get() / 100.0),
                original.y * (verticalPercent.get() / 100.0),
                original.z * (horizontalPercent.get() / 100.0)
            );
            accessor.setPlayerKnockback(Optional.of(scaled));
        });
    }

    private boolean isPhased() {
        Box box = mc.player.getBoundingBox();
        int minX = MathHelper.floor(box.minX);
        int minY = MathHelper.floor(box.minY);
        int minZ = MathHelper.floor(box.minZ);
        int maxX = MathHelper.floor(box.maxX);
        int maxY = MathHelper.floor(box.maxY);
        int maxZ = MathHelper.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mc.world.getBlockState(pos).isReplaceable()) return true;
                }
            }
        }
        return false;
    }

    private boolean hasElapsedSinceSetback(long ms) {
        return System.currentTimeMillis() - lastSetbackTime >= ms;
    }
}
