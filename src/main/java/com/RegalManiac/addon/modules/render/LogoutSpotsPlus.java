package com.RegalManiac.addon.modules.render;

import com.RegalManiac.addon.mixin.accessors.PlayerLikeEntityAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    // General
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder().name("scale").description("The scale.").defaultValue(1).min(0).build());
    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder().name("ignore-friends").description("Ignoring friend's logouts").defaultValue(false).build());

    // Render
    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>().name("render-mode").description("How to render the logout spot.").defaultValue(RenderMode.Box).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").description("How the shapes are rendered.").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(255, 0, 255, 55)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(255, 0, 255)).build());
    private final Setting<SettingColor> nameColor = sgRender.add(new ColorSetting.Builder().name("name-color").defaultValue(new SettingColor(255, 255, 255)).build());
    private final Setting<SettingColor> friendColor = sgRender.add(new ColorSetting.Builder().name("friend-color").description("The color of the friend's name.").defaultValue(new SettingColor(85, 255, 255)).build());
    private final Setting<SettingColor> nameBackgroundColor = sgRender.add(new ColorSetting.Builder().name("name-background-color").defaultValue(new SettingColor(0, 0, 0, 75)).build());

    // Chat Settings
    private final Setting<Boolean> logCoordinates = sgChat.add(new BoolSetting.Builder().name("log-coords").description("Send coordinates in chat when a player logs out.").defaultValue(true).build());
    private final Setting<Boolean> notifyOnJoin = sgChat.add(new BoolSetting.Builder().name("notify-on-join").description("Remind offline target coords when you join the server.").defaultValue(true).build());
    private final Setting<Boolean> selfInfoOnJoin = sgChat.add(new BoolSetting.Builder().name("self-info-on-join").description("Show your HP and totems popped from last session when joining.").defaultValue(true).build());


    private static boolean shouldMessage = false;
    private static String lastServerIp = "";
    private static long lastMessageTime = 0;
    private String lastWorldName = "";
    private int lastAge = -1;
    private static final java.util.Map<String, SessionData> sessionCache = new java.util.HashMap<>();

    private static final List<Entry> players = new ArrayList<>();
    private final List<PlayerListEntry> lastPlayerList = new ArrayList<>();
    private final List<PlayerEntity> lastPlayers = new ArrayList<>();
    private final Object2IntMap<UUID> totemPops = new Object2IntOpenHashMap<>();

    private static final String[] REGISTER_KEYWORDS = {
        "/register", "/reg", "register", "зарегистрируйтесь", "/рег", "создайте пароль"
    };
    private static final String[] LOGIN_KEYWORDS = {
        "/login", "/l ", "login", "авторизуйтесь", "войдите", "/логин", "пароль"
    };
    private static final String[] AUTH_PROMPT_INDICATORS = {
        "please", "type", "use", "welcome", "введите", "используйте"
    };

    private boolean inLobby = false;


    public LogoutSpotsPlus() {
        super(Categories.Render, "logout-spots-+", "Displays a box where another player has logged out at.");
    }

    private static class SessionData {
        float hp = -1;
        int pops = 0;
        int totems = 0;
        double x, y, z;
        boolean saved = false;
    }

    @Override
    public void onActivate() {
        inLobby = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            inLobby = false;
            return;
        }

        if (inLobby) return;

        String currentIp = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "singleplayer";
        lastServerIp = currentIp;

        SessionData session = sessionCache.computeIfAbsent(currentIp, k -> new SessionData());

        boolean isAtSpawn = Math.abs(mc.player.getX()) < 5.0 && Math.abs(mc.player.getZ()) < 5.0;
        String currentWorld = mc.world.getRegistryKey().getValue().toString();

        if (mc.player.age < lastAge || !currentWorld.equals(lastWorldName)) {
            if (session.saved && isAtSpawn) {
                shouldMessage = true;
            }
        }

        if (!isAtSpawn && mc.player.age > 60) {
            session.hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            session.pops = totemPops.getInt(mc.player.getUuid());
            session.totems = getTotemCount();
            session.x = mc.player.getX();
            session.y = mc.player.getY();
            session.z = mc.player.getZ();
            session.saved = true;
        }

        lastAge = mc.player.age;
        lastWorldName = currentWorld;

        handleEnemyLogouts();
        if (mc.getNetworkHandler() != null) {
            players.removeIf(entry -> mc.getNetworkHandler().getPlayerList().stream().anyMatch(p -> p.getProfile().id().equals(entry.uuid)));
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (shouldMessage && mc.player != null && mc.player.age > 20) {
            if (System.currentTimeMillis() - lastMessageTime > 5000) {
                sendInstantMessage();
                lastMessageTime = System.currentTimeMillis();
            }
            shouldMessage = false;
        }

        for (Entry p : players) {
            if (mc.world != null && mc.world.getRegistryKey() == p.dimension) p.render3D(event);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        for (Entry player : players) {
            if (mc.world != null && mc.world.getRegistryKey() == player.dimension) player.render2D();
        }
    }

    private String getHpColor(int health) {
        if (health >= 20) return "§a";
        if (health >= 10) return "§e";
        return "§c";
    }

    private void sendInstantMessage() {
        if (mc.inGameHud == null || mc.inGameHud.getChatHud() == null) return;

        SessionData session = sessionCache.get(lastServerIp);

        if (selfInfoOnJoin.get() && session != null && session.saved) {
            String hpCol = getHpColor(Math.round(session.hp));
            ChatUtils.sendMsg(Text.literal(
                "§bSelf: §f" + (int)session.x + " " + (int)session.y + " " + (int)session.z + " " +
                    "§7| HP: " + hpCol + String.format("%.1f", session.hp) + " " +
                    "§7| Pops: §e" + session.pops + " " +
                    "§7| Totems: §e" + session.totems
            ));
        }

        if (notifyOnJoin.get() && !players.isEmpty()) {
            for (Entry e : players) {
                String hpCol = getHpColor(e.health);
                ChatUtils.sendMsg(Text.literal(
                    "§c" + e.name + " §7logged at §f" + (int)e.x + " " + (int)e.y + " " + (int)e.z + " " +
                        "§7| HP: " + hpCol + e.health + " " +
                        "§7| Pops: §e" + e.totems
                ));
            }
        }
    }

    private void handleEnemyLogouts() {
        if (mc.getNetworkHandler() == null) return;

        if (mc.getNetworkHandler().getPlayerList().size() < lastPlayerList.size()) {
            for (PlayerListEntry entry : lastPlayerList) {
                if (mc.getNetworkHandler().getPlayerList().stream().anyMatch(p -> p.getProfile().id().equals(entry.getProfile().id()))) continue;

                for (PlayerEntity player : lastPlayers) {
                    if (player.getUuid().equals(entry.getProfile().id()) && !player.getUuid().equals(mc.player.getUuid())) {
                        if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;

                        int pops = totemPops.getOrDefault(player.getUuid(), 0);
                        Entry newEntry = new Entry(player, pops);
                        players.removeIf(p -> p.uuid.equals(newEntry.uuid));
                        players.add(newEntry);

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

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (event.entity instanceof PlayerEntity) players.removeIf(p -> p.uuid.equals(event.entity.getUuid()));
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString().replaceAll("§[0-9a-fk-or]", "").toLowerCase().trim();

        boolean foundLogin = false;
        boolean foundRegister = false;

        for (String key : REGISTER_KEYWORDS) {
            if (msg.contains(key)) {
                foundRegister = true;
                break;
            }
        }

        if (!foundRegister) {
            for (String key : LOGIN_KEYWORDS) {
                if (msg.contains(key)) {
                    foundLogin = true;
                    break;
                }
            }
        }

        boolean hasContext = false;
        for (String context : AUTH_PROMPT_INDICATORS) {
            if (msg.contains(context)) {
                hasContext = true;
                break;
            }
        }

        boolean isCommand = msg.contains("/") || msg.contains("!");

        if ((foundRegister || foundLogin) && (hasContext || isCommand)) {
            inLobby = true;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof CommandExecutionC2SPacket packet) {
            String cmd = packet.command().toLowerCase();
            if (cmd.startsWith("login") || cmd.startsWith("l ") || cmd.startsWith("reg")) {
                inLobby = false;
            }
        } else if (event.packet instanceof ChatMessageC2SPacket packet) {
            String msg = packet.chatMessage().toLowerCase();
            if (msg.startsWith("/login") || msg.startsWith("/l ") || msg.startsWith("/reg")) {
                inLobby = false;
            }
        }
    }

    @Override
    public String getInfoString() { return Integer.toString(players.size()); }

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

            this.halfWidth = entity.getWidth() / 2;
            this.x = entity.getX() - halfWidth;
            this.y = entity.getY();
            this.z = entity.getZ() - halfWidth;

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

            entity.setNoGravity(true);
            entity.setVelocity(0, 0, 0);
            entity.getDataTracker().set(PlayerLikeEntityAccessor.getPlayerModeCustomizationId(), (byte) 0);
        }

        public void render3D(Render3DEvent event) {
            double renderX = x + halfWidth;
            double renderY = y;
            double renderZ = z + halfWidth;

            entity.setPos(renderX, renderY, renderZ);
            entity.lastX = renderX;
            entity.lastY = renderY;
            entity.lastZ = renderZ;
            entity.lastRenderX = renderX;
            entity.lastRenderY = renderY;
            entity.lastRenderZ = renderZ;

            entity.setYaw(this.yaw);
            entity.setPitch(this.pitch);
            entity.setHeadYaw(this.headYaw);
            entity.setBodyYaw(this.bodyYaw);

            entity.lastYaw = this.yaw;
            entity.lastPitch = this.pitch;
            entity.lastHeadYaw = this.headYaw;

            entity.fallDistance = 0.0f;
            entity.distanceTraveled = 0.0f;
            entity.speed = 0.0f;
            entity.age = 0;
            entity.limbAnimator.setSpeed(0.0f);
            entity.handSwingProgress = 0.0f;

            if (renderMode.get() == RenderMode.Box) {
                event.renderer.box(x, y, z, x + xWidth, y + height, z + zWidth, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            } else if (renderMode.get() == RenderMode.Ghost) {
                net.minecraft.item.ItemStack chest = entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
                entity.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, net.minecraft.item.ItemStack.EMPTY);

                meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer.render(
                    event, entity, scale.get(), sideColor.get(), lineColor.get(), shapeMode.get()
                );

                entity.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, chest);
            }

        }

        public void render2D() {
            if (!PlayerUtils.isWithinCamera(x, y, z, mc.options.getViewDistance().getValue() * 16)) return;

            TextRenderer text = TextRenderer.get();
            double scaleValue = LogoutSpotsPlus.this.scale.get();
            pos.set(x + halfWidth, y + height + 0.5, z + halfWidth);

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
