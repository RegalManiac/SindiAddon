package com.RegalManiac.addon.modules.render;

import com.RegalManiac.addon.managers.LobbyManager;
import com.RegalManiac.addon.mixin.accessors.PlayerLikeEntityAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;

import java.util.*;

public class LogoutSpotsPlus extends Module {
    private static final Color GREEN = new Color(25, 225, 25);
    private static final Color ORANGE = new Color(225, 105, 25);
    private static final Color RED = new Color(225, 25, 25);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgChat = settings.createGroup("Chat Notifications");

    public enum RenderMode {
        Box,
        Ghost
    }

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder().name("scale").description("The scale.").defaultValue(1).min(0).build());
    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder().name("ignore-friends").description("Ignoring friend's logouts").defaultValue(false).build());

    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>().name("render-mode").description("How to render the logout spot.").defaultValue(RenderMode.Box).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").description("How the shapes are rendered.").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(255, 0, 255, 55)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(255, 0, 255)).build());
    private final Setting<SettingColor> nameColor = sgRender.add(new ColorSetting.Builder().name("name-color").defaultValue(new SettingColor(255, 255, 255)).build());
    private final Setting<SettingColor> friendColor = sgRender.add(new ColorSetting.Builder().name("friend-color").description("The color of the friend's name.").defaultValue(new SettingColor(85, 255, 255)).build());
    private final Setting<SettingColor> nameBackgroundColor = sgRender.add(new ColorSetting.Builder().name("name-background-color").defaultValue(new SettingColor(0, 0, 0, 75)).build());

    private final Setting<Boolean> logCoordinates = sgChat.add(new BoolSetting.Builder().name("log-coords").description("Send coordinates in chat when a player logs out in the world.").defaultValue(true).build());
    private final Setting<Boolean> notifyOnJoin = sgChat.add(new BoolSetting.Builder().name("notify-on-join").description("Remind offline target coords in lobby when you join/reconnect.").defaultValue(true).build());
    private final Setting<Boolean> selfInfoOnJoin = sgChat.add(new BoolSetting.Builder().name("self-info-on-join").description("Show your HP, totems and coords in lobby from last session.").defaultValue(true).build());

    private static String currentIp = "";
    private boolean hasSentLobbyInfo = false;

    private static class ServerSession {
        final SessionData selfData = new SessionData();
        final List<Entry> logoutPlayers = new ArrayList<>();
    }

    private static class SessionData {
        float hp = -1;
        int pops = 0;
        int totems = 0;
        double x, y, z;
        boolean saved = false;
    }

    private static final Map<String, ServerSession> serverSessions = new HashMap<>();

    private final List<PlayerListEntry> lastPlayerList = new ArrayList<>();
    private final List<PlayerEntity> lastPlayers = new ArrayList<>();
    private final Object2IntMap<UUID> totemPops = new Object2IntOpenHashMap<>();

    public LogoutSpotsPlus() {
        super(Categories.Render, "logout-spots-+", "Displays a box where another player has logged out at.");
    }

    private ServerSession getCurrentSession() {
        if (currentIp.isEmpty()) return null;
        return serverSessions.computeIfAbsent(currentIp, k -> new ServerSession());
    }

    private boolean isInMainWorld() {
        if (mc.player == null || mc.world == null) return false;
        if (LobbyManager.isInLobby()) return false;
        return mc.player.age > 40;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        String activeIp = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address.toLowerCase() : "singleplayer";

        if (!activeIp.equals(currentIp)) {
            currentIp = activeIp;
            hasSentLobbyInfo = false;
        }

        ServerSession session = getCurrentSession();
        if (session == null) return;

        if (LobbyManager.isInLobby()) {
            if (!hasSentLobbyInfo) {
                sendLobbyMessages(session);
                hasSentLobbyInfo = true;
            }
            return;
        }

        hasSentLobbyInfo = false;

        if (!isInMainWorld()) return;
        session.selfData.hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        session.selfData.pops = totemPops.getInt(mc.player.getUuid());
        session.selfData.totems = getTotemCount();
        session.selfData.x = mc.player.getX();
        session.selfData.y = mc.player.getY();
        session.selfData.z = mc.player.getZ();
        session.selfData.saved = true;
        handleEnemyLogouts(session);

        if (mc.getNetworkHandler() != null) {
            session.logoutPlayers.removeIf(entry -> mc.getNetworkHandler().getPlayerList().stream()
                .anyMatch(p -> p.getProfile().id().equals(entry.uuid)));
        }
    }

    private void sendLobbyMessages(ServerSession session) {
        if (mc.inGameHud == null || mc.inGameHud.getChatHud() == null) return;

        if (selfInfoOnJoin.get() && session.selfData.saved) {
            String hpCol = getHpColor(Math.round(session.selfData.hp));
            ChatUtils.sendMsg(Text.literal(
                "§b[Self] §f" + (int)session.selfData.x + " " + (int)session.selfData.y + " " + (int)session.selfData.z + " " +
                    "§7| HP: " + hpCol + String.format("%.1f", session.selfData.hp) + " " +
                    "§7| Pops: §e" + session.selfData.pops + " " +
                    "§7| Totems: §e" + session.selfData.totems
            ));
        }

        if (notifyOnJoin.get() && !session.logoutPlayers.isEmpty()) {
            for (Entry e : session.logoutPlayers) {
                String hpCol = getHpColor(e.health);
                ChatUtils.sendMsg(Text.literal(
                    "§c[Players] " + e.name + " §7logged at §f" + (int)e.x + " " + (int)e.y + " " + (int)e.z + " " +
                        "§7| HP: " + hpCol + e.health + " " +
                        "§7| Pops: §e" + e.totems
                ));
            }
        }
    }

    private void handleEnemyLogouts(ServerSession session) {
        if (mc.getNetworkHandler() == null) return;

        if (mc.getNetworkHandler().getPlayerList().size() < lastPlayerList.size()) {
            for (PlayerListEntry entry : lastPlayerList) {
                if (mc.getNetworkHandler().getPlayerList().stream().anyMatch(p -> p.getProfile().id().equals(entry.getProfile().id()))) continue;

                for (PlayerEntity player : lastPlayers) {
                    if (player.getUuid().equals(entry.getProfile().id()) && !player.getUuid().equals(mc.player.getUuid())) {
                        if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;

                        int pops = totemPops.getOrDefault(player.getUuid(), 0);
                        Entry newEntry = new Entry(player, pops);
                        session.logoutPlayers.removeIf(p -> p.uuid.equals(newEntry.uuid));
                        session.logoutPlayers.add(newEntry);

                        if (logCoordinates.get()) {
                            String hpCol = getHpColor(newEntry.health);
                            ChatUtils.sendMsg(Text.literal(
                                "§c" + newEntry.name + " §7logged at §f" + (int)newEntry.x + " " + (int)newEntry.y + " " + (int)newEntry.z + " " +
                                    "§7| HP: " + hpCol + newEntry.health + " " +
                                    "§7| Pops: §e" + pops
                            ));
                        }
                    }
                }
            }
        }
        lastPlayerList.clear();
        lastPlayerList.addAll(mc.getNetworkHandler().getPlayerList());
        updateLastPlayers();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isInMainWorld()) return;

        ServerSession session = getCurrentSession();
        if (session == null) return;

        for (Entry p : session.logoutPlayers) {
            if (mc.world != null && mc.world.getRegistryKey() == p.dimension) p.render3D(event);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!isInMainWorld()) return;

        ServerSession session = getCurrentSession();
        if (session == null) return;

        for (Entry player : session.logoutPlayers) {
            if (mc.world != null && mc.world.getRegistryKey() == player.dimension) player.render2D();
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!isInMainWorld()) return;

        if (event.entity instanceof PlayerEntity) {
            ServerSession session = getCurrentSession();
            if (session != null) {
                session.logoutPlayers.removeIf(p -> p.uuid.equals(event.entity.getUuid()));
            }
        }
    }

    private String getHpColor(int health) {
        if (health >= 20) return "§a";
        if (health >= 10) return "§e";
        return "§c";
    }

    private void updateLastPlayers() {
        lastPlayers.clear();
        if (mc.world != null) {
            for (Entity e : mc.world.getEntities()) if (e instanceof PlayerEntity) lastPlayers.add((PlayerEntity) e);
        }
    }

    private int getTotemCount() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) count++;
        }
        return count;
    }

    @Override
    public String getInfoString() {
        ServerSession session = getCurrentSession();
        return session != null ? Integer.toString(session.logoutPlayers.size()) : "0";
    }

    private static final Vector3d pos = new Vector3d();

    private class Entry {
        public final double x, y, z;
        public final double xWidth, zWidth, halfWidth, height;
        public final UUID uuid;
        public final String name;
        public final int health, maxHealth, totems;
        public final boolean isFriend;
        public final RegistryKey<World> dimension;
        public final PlayerEntity entity;
        public final float yaw, pitch, headYaw, bodyYaw;

        public Entry(PlayerEntity entity, int totems) {
            this.entity = entity;

            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();

            this.halfWidth = entity.getWidth() / 2;
            this.xWidth = entity.getBoundingBox().getLengthX();
            this.zWidth = entity.getBoundingBox().getLengthZ();
            this.height = entity.getBoundingBox().getLengthY();

            this.uuid = entity.getUuid();
            this.name = entity.getName().getString();
            this.health = Math.round(entity.getHealth() + entity.getAbsorptionAmount());
            this.maxHealth = Math.round(entity.getMaxHealth() + entity.getAbsorptionAmount());
            this.totems = totems;
            this.isFriend = Friends.get().isFriend(entity);
            this.dimension = entity.getEntityWorld().getRegistryKey();

            this.yaw = entity.getYaw();
            this.pitch = entity.getPitch();
            this.headYaw = entity.getHeadYaw();
            this.bodyYaw = entity.getBodyYaw();

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                entity.equipStack(slot, ItemStack.EMPTY);
            }
            entity.getDataTracker().set(PlayerLikeEntityAccessor.getPlayerModeCustomizationId(), (byte) 0);
        }

        public void render3D(Render3DEvent event) {
            entity.setPos(x, y, z);
            entity.lastX = x;
            entity.lastY = y;
            entity.lastZ = z;
            entity.lastRenderX = x;
            entity.lastRenderY = y;
            entity.lastRenderZ = z;

            entity.setYaw(this.yaw);
            entity.setPitch(this.pitch);
            entity.setHeadYaw(this.headYaw);
            entity.setBodyYaw(this.bodyYaw);

            entity.lastYaw = this.yaw;
            entity.lastPitch = this.pitch;
            entity.lastHeadYaw = this.headYaw;
            entity.lastBodyYaw = this.bodyYaw;

            entity.setVelocity(Vec3d.ZERO);
            entity.setNoGravity(true);
            entity.setOnGround(true);
            entity.fallDistance = 0.0f;
            entity.distanceTraveled = 0.0f;
            entity.speed = 0.0f;
            entity.age = 0;
            entity.hurtTime = 0;
            entity.maxHurtTime = 0;
            entity.deathTime = 0;
            entity.limbAnimator.setSpeed(0.0f);
            entity.handSwingProgress = 0.0f;
            entity.handSwingTicks = 0;

            if (renderMode.get() == RenderMode.Box) {
                event.renderer.box(x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            } else if (renderMode.get() == RenderMode.Ghost) {
                meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer.render(
                    event, entity, scale.get(), sideColor.get(), lineColor.get(), shapeMode.get()
                );
            }
        }

        public void render2D() {
            if (!PlayerUtils.isWithinCamera(x, y, z, mc.options.getViewDistance().getValue() * 16)) return;

            TextRenderer text = TextRenderer.get();
            double scaleValue = LogoutSpotsPlus.this.scale.get();
            pos.set(x, y + height + 0.5, z);

            if (!NametagUtils.to2D(pos, scaleValue)) return;
            NametagUtils.begin(pos);

            double healthPercentage = (double) health / maxHealth;
            Color healthColor = (healthPercentage <= 0.333) ? RED : (healthPercentage <= 0.666) ? ORANGE : GREEN;
            Color nameColorToUse = isFriend ? friendColor.get() : nameColor.get();
            Color gray = new Color(200, 200, 200);

            String hpLine = health + " HP";
            String popLine = totems + " Pops";

            double nameW = text.getWidth(name);
            double statsW = text.getWidth(hpLine + "  " + popLine);
            double maxW = Math.max(nameW, statsW);

            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(-(maxW / 2.0) - 2, 0, maxW + 4, text.getHeight() * 2 + 2, nameBackgroundColor.get());
            Renderer2D.COLOR.render();

            text.beginBig();
            text.render(name, -(nameW / 2.0), 0, nameColorToUse);
            double statsX = -(statsW / 2.0);
            text.render(hpLine, statsX, text.getHeight() + 1, healthColor);
            text.render(popLine, statsX + text.getWidth(hpLine + "  "), text.getHeight() + 1, gray);
            text.end();

            NametagUtils.end();
        }
    }
}
