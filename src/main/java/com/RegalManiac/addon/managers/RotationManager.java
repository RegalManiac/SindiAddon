package com.RegalManiac.addon.managers;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class RotationManager {

    private static final List<Rotation> rotations = new CopyOnWriteArrayList<>();
    private static boolean initialized = false;
    private static float preYaw, prePitch;
    private static boolean isSpoofing = false;
    private static float serverYaw, serverPitch;
    private static int wireFreshTicks = 0;
    private static float wireNudge = 0.0F;

    private static final boolean[][] INPUT_DIRS = new boolean[][]{
        {true, false, false, false},
        {true, false, false, true},
        {false, false, false, true},
        {false, true, false, true},
        {false, true, false, false},
        {false, true, true, false},
        {false, false, true, false},
        {true, false, true, false}
    };

    public static void init() {
        if (!initialized) {
            MeteorClient.EVENT_BUS.subscribe(RotationManager.class);
            initialized = true;
        }
    }

    public static void rotate(Rotation rotation) {
        if (rotation == null) return;
        rotations.add(rotation);
    }

    public static Rotation getCurrentRotation() {
        return rotations.stream()
            .filter(Objects::nonNull)
            .filter(r -> r.getTime() >= 0)
            .max(Comparator.comparingInt(Rotation::getPriority))
            .orElse(null);
    }

    public static Rotation getPlayerRotation() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return new Rotation(0, 0, 0, 0);
        return new Rotation(0, 0, player.getYaw(), player.getPitch());
    }

    @EventHandler
    private static void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket packet && packet.changesLook()) {
            serverYaw = packet.getYaw(serverYaw);
            serverPitch = packet.getPitch(serverPitch);
        }
    }

    @EventHandler
    private static void onTick(TickEvent.Pre event) {
        if (wireFreshTicks > 0) {
            wireFreshTicks--;
            wireNudge = wireNudge > 0.0F ? -0.01F : 0.01F;
        } else {
            wireNudge = 0.0F;
        }

        rotations.forEach(r -> {
            if (r != null) r.tickDown();
        });
        rotations.removeIf(r -> r == null || r.getTime() < 0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private static void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
        Rotation current = getCurrentRotation();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (current != null && player != null) {
            preYaw = player.getYaw();
            prePitch = player.getPitch();

            player.setYaw(current.getYaw());
            player.setPitch(current.getPitch());

            Rotations.setCamRotation(current.getYaw(), current.getPitch());
            Rotations.rotating = true;
            isSpoofing = true;

            current.runCallback();
        } else {
            Rotations.rotating = false;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private static void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
        if (isSpoofing) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.setYaw(preYaw);
                player.setPitch(prePitch);
            }
            isSpoofing = false;
        }
    }

    public static int getInputSteps() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;
        Rotation current = getCurrentRotation();
        if (current == null) return 0;
        float delta = MathHelper.wrapDegrees(mc.player.getYaw() - current.getYaw());
        return Math.round(delta / 45.0F);
    }

    public static void applyMovementFix(Input in) {
        int k = getInputSteps();
        if (k == 0) return;

        PlayerInput pi = in.playerInput;

        int s = (pi.left() ? 1 : 0) - (pi.right() ? 1 : 0);
        int f = (pi.forward() ? 1 : 0) - (pi.backward() ? 1 : 0);
        if (s == 0 && f == 0) return;

        int idx = 0;
        for (int i = 0; i < 8; i++) {
            int ds = (INPUT_DIRS[i][2] ? 1 : 0) - (INPUT_DIRS[i][3] ? 1 : 0);
            int df = (INPUT_DIRS[i][0] ? 1 : 0) - (INPUT_DIRS[i][1] ? 1 : 0);
            if (ds == s && df == f) {
                idx = i;
                break;
            }
        }

        boolean[] d = INPUT_DIRS[Math.floorMod(idx + k, 8)];
        boolean fwd = d[0];
        boolean back = d[1];
        boolean left = d[2];
        boolean right = d[3];

        in.playerInput = new PlayerInput(fwd, back, left, right, pi.jump(), pi.sneak(), pi.sprint());

        float movementSideways = (left == right) ? 0.0F : (left ? 1.0F : -1.0F);
        float movementForward = (fwd == back) ? 0.0F : (fwd ? 1.0F : -1.0F);
        float mult = (float) Math.sqrt(in.movementVector.x * in.movementVector.x + in.movementVector.y * in.movementVector.y);

        in.movementVector = new Vec2f(movementSideways * mult, movementForward * mult);
    }

    public static float getSentYaw() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        Rotation current = getCurrentRotation();
        float baseYaw = current != null ? current.getYaw() : (player != null ? player.getYaw() : serverYaw);
        return baseYaw + wireNudge;
    }

    public static float getSentPitch() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        Rotation current = getCurrentRotation();
        return current != null ? current.getPitch() : (player != null ? player.getPitch() : serverPitch);
    }

    public static boolean isWireFresh() {
        return wireNudge != 0.0F;
    }

    public record RotationDelta(float deltaYaw, float deltaPitch) {
        public double length() {
            return Math.hypot(deltaYaw, deltaPitch);
        }
    }

    public static class Rotation {
        private final int priority;
        private int time;
        private final float yaw;
        private final float pitch;
        private Runnable callback;

        public Rotation(int priority, int time, float yaw, float pitch, Runnable callback) {
            this.priority = priority;
            this.time = time;
            this.yaw = yaw;
            this.pitch = pitch;
            this.callback = callback;
        }

        public Rotation(int priority, int time, float yaw, float pitch) {
            this(priority, time, yaw, pitch, null);
        }

        public int getPriority() { return priority; }
        public int getTime() { return time; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public Runnable getCallback() { return callback; }

        public void tickDown() {
            if (time >= 0) time--;
        }

        private float angleDifference(float a, float b) {
            return MathHelper.wrapDegrees(a - b);
        }

        public RotationDelta rotationDeltaTo(Rotation other) {
            return new RotationDelta(
                angleDifference(other.yaw, yaw),
                angleDifference(other.pitch, pitch)
            );
        }

        public Rotation normalize(int time) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options == null) return this;

            double f = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
            double gcd = f * f * f * 8.0 * 0.15F;

            var currentRotation = RotationManager.getCurrentRotation();
            if (currentRotation == null) currentRotation = getPlayerRotation();

            var diff = currentRotation.rotationDeltaTo(this);

            int g1 = (int) (Math.round(diff.deltaYaw() / gcd) * gcd);
            int g2 = (int) (Math.round(diff.deltaPitch() / gcd) * gcd);

            float newYaw = currentRotation.yaw + (float) g1;
            float newPitch = currentRotation.pitch + (float) g2;

            return new Rotation(priority, time, newYaw, MathHelper.clamp(newPitch, -90, 90), callback);
        }

        public Vec3d getRotationVec() {
            float pitchRad = pitch * MathHelper.RADIANS_PER_DEGREE;
            float yawRad = -yaw * MathHelper.RADIANS_PER_DEGREE;

            float cosPitch = MathHelper.cos(pitchRad);
            float sinPitch = MathHelper.sin(pitchRad);
            float cosYaw = MathHelper.cos(yawRad);
            float sinYaw = MathHelper.sin(yawRad);

            return new Vec3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
        }

        public void runCallback() {
            if (callback != null) {
                Runnable action = callback;
                callback = null;
                action.run();
            }
        }
    }
}
