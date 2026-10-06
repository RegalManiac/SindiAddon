package com.RegalManiac.addon.utils.automation;

import com.google.gson.*;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class KitUtils {

    private static File kitsFolder;

    public static File getKitsFolder() {
        if (kitsFolder == null) {
            kitsFolder = new File(MeteorClient.FOLDER, "SindiAddon/kits");
            if (!kitsFolder.exists() && !kitsFolder.mkdirs()) {
                ChatUtils.error("Failed to create SindiAddon/kits directory!");
            }
        }
        return kitsFolder;
    }

    public static void startCreatingPreset(String name) {
        File folder = getKitsFolder();
        File file = new File(folder, name + ".json");
        if (file.exists()) {
            ChatUtils.error("Preset '" + name + "' already exists! Overwriting is forbidden.");
            return;
        }

        MeteorClient.EVENT_BUS.subscribe(new PresetCreator(name));
        ChatUtils.info("Open any container to save preset '" + name + "'.");
    }

    public static List<PresetItem> loadPreset(String name) {
        File file = new File(getKitsFolder(), name + ".json");
        if (!file.exists()) {
            ChatUtils.error("Preset '" + name + "' does not exist.");
            return null;
        }

        List<PresetItem> items = new ArrayList<>();
        try (FileReader reader = new FileReader(file)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement e : array) {
                JsonObject obj = e.getAsJsonObject();
                PresetItem item = new PresetItem(obj.get("id").getAsString(), obj.get("count").getAsInt());
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
        } catch (Exception e) {
            ChatUtils.error("Failed to load preset '" + name + "'!");
            return null;
        }
        return items;
    }

    public static boolean removePreset(String name) {
        File file = new File(getKitsFolder(), name + ".json");
        if (file.exists() && file.delete()) {
            ChatUtils.info("Preset '" + name + "' removed.");
            return true;
        } else {
            ChatUtils.error("Failed to delete or find preset '" + name + "'.");
            return false;
        }
    }

    private static void savePreset(String name, List<PresetItem> items) {
        try {
            JsonArray array = new JsonArray();
            for (PresetItem item : items) {
                if (item.isEmpty()) continue;
                JsonObject obj = new JsonObject();
                obj.addProperty("id", item.id);
                obj.addProperty("count", item.count);
                array.add(obj);
            }

            try (FileWriter writer = new FileWriter(new File(getKitsFolder(), name + ".json"))) {
                new GsonBuilder().setPrettyPrinting().create().toJson(array, writer);
            }
        } catch (Exception e) {
            ChatUtils.error("Failed to save preset!");
        }
    }

    private static class PresetCreator {
        private final String name;
        private final MinecraftClient mc = MinecraftClient.getInstance();

        public PresetCreator(String name) {
            this.name = name;
        }

        @EventHandler
        private void onTick(TickEvent.Pre event) {
            if (mc.player == null) return;
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler
                && mc.player.currentScreenHandler.slots.size() > 36) {

                List<PresetItem> items = new ArrayList<>();
                int size = mc.player.currentScreenHandler.slots.size() - 36;
                for (int i = 0; i < size; i++) {
                    ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
                    if (!stack.isEmpty() && !Registries.ITEM.getId(stack.getItem()).toString().equals("minecraft:air")) {
                        items.add(new PresetItem(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount()));
                    }
                }

                if (items.isEmpty()) {
                    ChatUtils.error("Cannot save empty preset. Opened container is empty. Ты че пьяный нахуй");
                    mc.player.closeHandledScreen();
                    MeteorClient.EVENT_BUS.unsubscribe(this);
                    return;
                }

                savePreset(name, items);
                ChatUtils.info("Preset '" + name + "' saved successfully (" + items.size() + " items). Use 'kitcreator set " + name + "' to load it.");
                mc.player.closeHandledScreen();

                MeteorClient.EVENT_BUS.unsubscribe(this);
            }
        }
    }

    public record PresetItem(String id, int count) {
        public boolean isEmpty() {
            return count <= 0 || "minecraft:air".equals(id);
        }
    }
}
