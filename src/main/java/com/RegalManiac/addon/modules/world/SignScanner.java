package com.RegalManiac.addon.modules.world;

import com.RegalManiac.addon.utils.text.TextRenderUtils;
import com.RegalManiac.addon.utils.text.TextRenderUtils.SignRenderData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
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
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.io.*;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class SignScanner extends Module {
    public final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final SettingGroup sgRender = settings.createGroup("Render");

    public final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder().name("range").defaultValue(64).min(1).sliderMax(512).build());
    public final Setting<Boolean> notification = sgGeneral.add(new BoolSetting.Builder().name("notification").defaultValue(true).build());

    public final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("render").defaultValue(true).build());
    public final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>().name("render-mode").defaultValue(RenderMode.Both).visible(render::get).build());

    public final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).visible(() -> render.get() && renderMode.get() != RenderMode.Text).build());
    public final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(255, 255, 255, 45)).visible(() -> render.get() && renderMode.get() != RenderMode.Text && shapeMode.get() != ShapeMode.Lines).build());
    public final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(255, 255, 255, 255)).visible(() -> render.get() && renderMode.get() != RenderMode.Text && shapeMode.get() != ShapeMode.Sides).build());

    public final Setting<Boolean> preventOverlap = sgRender.add(new BoolSetting.Builder().name("prevent-overlap").description("Smoothly fades out signs that are behind other signs to prevent text mess.").defaultValue(true).visible(() -> render.get() && renderMode.get() != RenderMode.Box).build());
    public final Setting<Double> baseScale = sgRender.add(new DoubleSetting.Builder().name("base-scale").defaultValue(1.0).min(0.2).sliderMax(2.0).visible(() -> render.get() && renderMode.get() != RenderMode.Box).build());
    public final Setting<Boolean> multilineDisplay = sgRender.add(new BoolSetting.Builder().name("multiline-display").description("Display sign text as multiple lines.").defaultValue(true).visible(() -> render.get() && renderMode.get() != RenderMode.Box).build());
    public final Setting<SettingColor> textColor = sgRender.add(new ColorSetting.Builder().name("text-color").defaultValue(new SettingColor(255, 255, 255, 255)).visible(() -> render.get() && renderMode.get() != RenderMode.Box).build());
    public final Setting<SettingColor> backgroundColor = sgRender.add(new ColorSetting.Builder().name("background-color").defaultValue(new SettingColor(0, 0, 0, 100)).visible(() -> render.get() && renderMode.get() != RenderMode.Box).build());
    public final Setting<Boolean> showBackground = sgRender.add(new BoolSetting.Builder().name("show-background").defaultValue(true).visible(() -> render.get() && renderMode.get() != RenderMode.Box).build());

    private Map<String, Map<String, List<String>>> signDatabase = new HashMap<>();
    private final Map<String, String> editingCache = new HashMap<>();
    private final Map<String, Long> lastEditTimes = new HashMap<>();
    private static final File FILE = new File(MeteorClient.FOLDER, "SindiAddon/ScannedSigns.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private int ticksPassed = 0;
    private final List<SignRenderData> signsToRender = new CopyOnWriteArrayList<>();
    private final TextRenderUtils renderUtils = new TextRenderUtils();

    private static final Pattern FORMAT_CODE = Pattern.compile("§.");
    private static final Pattern AMPERSAND_CODE = Pattern.compile("&[0-9a-fklmnor]");

    public SignScanner() {
        super(Categories.World, "sign-scanner", "Scans, tracks changes, saves sign text.");
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

        List<SignRenderData> currentTickSigns = new ArrayList<>();

        for (int x = pX - chunkRange; x <= pX + chunkRange; x++) {
            for (int z = pZ - chunkRange; z <= pZ + chunkRange; z++) {
                WorldChunk chunk = mc.world.getChunk(x, z);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof SignBlockEntity sign) {
                        BlockPos pos = sign.getPos();
                        if (mc.player.getBlockPos().getSquaredDistance(pos) > rSq) continue;

                        List<String> lines = getSignLines(sign);
                        String text = String.join(" ", lines).trim();
                        if (text.isEmpty()) continue;

                        currentTickSigns.add(new SignRenderData(lines, text, Vec3d.ofCenter(pos), pos));

                        String posKey = pos.getX() + "," + pos.getY() + "," + pos.getZ();
                        long currentTime = System.currentTimeMillis();
                        String cachedText = editingCache.get(posKey);

                        if (!text.equals(cachedText)) {
                            editingCache.put(posKey, text);
                            lastEditTimes.put(posKey, currentTime);
                            continue;
                        }

                        Long lastEdit = lastEditTimes.get(posKey);
                        if (lastEdit != null && (currentTime - lastEdit) < 1000) continue;

                        List<String> history = serverSigns.computeIfAbsent(posKey, k -> new ArrayList<>());
                        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                        String newEntry = text + " [" + date + "]";

                        if (history.isEmpty()) {
                            if (notification.get()) ChatUtils.info("Found sign at §a[%s]§7: §f%s", posKey, text);
                            history.add(newEntry);
                            foundNew = true;
                        } else {
                            String lastEntry = history.getLast();
                            String lastText = lastEntry.length() > 22 ? lastEntry.substring(0, lastEntry.length() - 22) : lastEntry;
                            if (!lastText.equals(text)) {
                                if (notification.get()) ChatUtils.info("Sign changed at §a[%s]§7: §8%s §7-> §f%s", posKey, lastText, text);
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
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || renderMode.get() == RenderMode.Text) return;

        for (SignRenderData sign : signsToRender) {
            event.renderer.box(sign.pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!render.get() || renderMode.get() == RenderMode.Box || mc.world == null || mc.player == null) return;
        renderUtils.render(this, signsToRender);
    }

    private List<String> getSignLines(SignBlockEntity sign) {
        List<String> lines = new ArrayList<>();
        try {
            for (Text line : sign.getFrontText().getMessages(false)) {
                String clean = cleanText(line.getString());
                if (!clean.isEmpty()) lines.add(clean);
            }
            for (Text line : sign.getBackText().getMessages(false)) {
                String clean = cleanText(line.getString());
                if (!clean.isEmpty()) lines.add(clean);
            }
        } catch (Exception ignored) {}
        return lines;
    }

    private String cleanText(String text) {
        if (text == null || text.isEmpty()) return "";
        text = FORMAT_CODE.matcher(text).replaceAll("");
        return AMPERSAND_CODE.matcher(text).replaceAll("").trim();
    }

    private String getServerId() {
        String dim = mc.world != null ? mc.world.getRegistryKey().getValue().getPath() : "unknown";
        if (mc.isInSingleplayer()) return "Singleplayer_" + dim;
        ServerInfo info = mc.getCurrentServerEntry();
        return (info != null ? info.address.replace(":", "_") : "Unknown") + "_" + dim;
    }

    private void loadData() {
        if (!FILE.exists()) return;
        try (Reader reader = new FileReader(FILE)) {
            Type type = new TypeToken<Map<String, Map<String, List<String>>>>(){}.getType();
            Map<String, Map<String, List<String>>> data = GSON.fromJson(reader, type);
            if (data != null) signDatabase = data;
        } catch (Exception e) {
            ChatUtils.error("Failed to load signs.");
        }
    }

    private void saveData() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return;
            try (Writer writer = new FileWriter(FILE)) {
                GSON.toJson(signDatabase, writer);
            }
        } catch (IOException ignored) {}
    }

    public enum RenderMode {
        Box,
        Text,
        Both
    }
}
