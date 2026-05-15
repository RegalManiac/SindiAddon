package com.RegalManiac.addon.modules.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.io.*;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class SignScanner extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder().name("range").defaultValue(64).min(1).sliderMax(512).build());
    private final Setting<Boolean> notification = sgGeneral.add(new BoolSetting.Builder().name("notification").defaultValue(true).build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("render").defaultValue(false).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(255, 255, 255, 50)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private Map<String, Map<String, List<String>>> signDatabase = new HashMap<>();
    private final Map<String, String> editingCache = new HashMap<>();
    private final Map<String, Long> lastEditTimes = new HashMap<>();
    private static final File FILE = new File(MeteorClient.FOLDER, "SindiAddon/ScannedSigns.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private int ticksPassed = 0;
    private final List<BlockPos> signsToRender = new CopyOnWriteArrayList<>();

    public SignScanner() {
        super(Categories.World, "sign-scanner", "Scans, tracks changes, and saves sign text.");
    }

    @Override
    public void onActivate() {
        loadData();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ticksPassed++;
        if (ticksPassed < 20 || mc.world == null || mc.player == null) return;
        ticksPassed = 0;

        String serverId = getServerId();
        Map<String, List<String>> serverSigns = signDatabase.computeIfAbsent(serverId, k -> new HashMap<>());
        boolean foundNew = false;

        int chunkRange = (range.get() / 16) + 1;
        int pX = mc.player.getChunkPos().x;
        int pZ = mc.player.getChunkPos().z;
        double rSq = range.get() * range.get();

        List<BlockPos> currentTickSigns = new ArrayList<>();

        for (int x = pX - chunkRange; x <= pX + chunkRange; x++) {
            for (int z = pZ - chunkRange; z <= pZ + chunkRange; z++) {
                WorldChunk chunk = mc.world.getChunk(x, z);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof SignBlockEntity sign) {
                        BlockPos pos = sign.getPos();
                        if (mc.player.getBlockPos().getSquaredDistance(pos) > rSq) continue;

                        currentTickSigns.add(pos);

                        String posKey = pos.getX() + "," + pos.getY() + "," + pos.getZ();
                        String text = getFullSignText(sign);
                        if (text.isEmpty()) continue;

                        long currentTime = System.currentTimeMillis();
                        String cachedText = editingCache.get(posKey);

                        if (!text.equals(cachedText)) {
                            editingCache.put(posKey, text);
                            lastEditTimes.put(posKey, currentTime);
                            continue;
                        }

                        Long lastEdit = lastEditTimes.get(posKey);
                        if (lastEdit != null && (currentTime - lastEdit) < 1000) {
                            continue;
                        }

                        List<String> history = serverSigns.computeIfAbsent(posKey, k -> new ArrayList<>());
                        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                        String newEntry = text + " [" + date + "]";

                        if (history.isEmpty()) {
                            if (notification.get()) {
                                ChatUtils.info("Found sign at §a[%s]§7: §f%s", posKey, text);
                            }
                            history.add(newEntry);
                            foundNew = true;
                        } else {
                            String lastEntry = history.get(history.size() - 1);
                            String lastText = lastEntry;
                            if (lastEntry.length() > 22) {
                                lastText = lastEntry.substring(0, lastEntry.length() - 22);
                            }

                            if (!lastText.equals(text)) {
                                if (notification.get()) {
                                    ChatUtils.info("Sign changed at §a[%s]§7: §8%s §7-> §f%s", posKey, lastText, text);
                                }
                                history.add(newEntry);
                                foundNew = true;
                            }
                        }
                    }
                }
            }
        }

        signsToRender.clear();
        signsToRender.addAll(currentTickSigns);

        if (foundNew) saveData();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || mc.world == null || mc.player == null) return;

        for (BlockPos pos : signsToRender) {
            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    private String getFullSignText(SignBlockEntity sign) {
        StringBuilder sb = new StringBuilder();
        try {
            for (net.minecraft.text.Text line : sign.getFrontText().getMessages(false)) {
                String s = line.getString().trim();
                if (!s.isEmpty()) sb.append(s).append(" ");
            }
            for (net.minecraft.text.Text line : sign.getBackText().getMessages(false)) {
                String s = line.getString().trim();
                if (!s.isEmpty()) sb.append(s).append(" ");
            }
        } catch (Exception ignored) {}
        return sb.toString().trim();
    }

    private String getServerId() {
        String dim = "unknown";
        if (mc.world != null) {
            dim = mc.world.getRegistryKey().getValue().getPath();
        }

        if (mc.isInSingleplayer()) return "Singleplayer_" + dim;
        ServerInfo info = mc.getCurrentServerEntry();
        String ip = info != null ? info.address.replace(":", "_") : "Unknown";
        return ip + "_" + dim;
    }

    private void loadData() {
        if (!FILE.exists()) return;
        try (Reader reader = new FileReader(FILE)) {
            Type type = new TypeToken<Map<String, Map<String, List<String>>>>(){}.getType();
            Map<String, Map<String, List<String>>> data = GSON.fromJson(reader, type);
            if (data != null) signDatabase = data;
        } catch (Exception e) {
            ChatUtils.error("Failed to load signs. Please delete scanned_signs.json file.");
        }
    }

    private void saveData() {
        try {
            if (!FILE.exists()) {
                if (FILE.getParentFile() != null) FILE.getParentFile().mkdirs();
                FILE.createNewFile();
            }
            try (Writer writer = new FileWriter(FILE)) {
                GSON.toJson(signDatabase, writer);
            }
        } catch (IOException ignored) {}
    }
}
