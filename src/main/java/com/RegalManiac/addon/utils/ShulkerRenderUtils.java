package com.RegalManiac.addon.utils;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTooltips;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

public class ShulkerRenderUtils {

    private static final WeakHashMap<ItemStack, CachedData> cache = new WeakHashMap<>();
    private record CachedData(ItemStack mostCommonStack, boolean hasMultiple) {}
    public enum IconPosition { BottomRight, BottomLeft, TopRight, TopLeft, Center }

    @SuppressWarnings("unchecked")
    public static void renderShulkerOverlay(DrawContext context, int x, int y, ItemStack stack) {
        if (stack.isEmpty()) return;

        BetterTooltips module = Modules.get().get(BetterTooltips.class);
        if (module == null || !module.isActive()) return;

        Setting<Boolean> enabled = (Setting<Boolean>) module.settings.get("show-icons");
        if (enabled == null || !enabled.get()) return;

        if (!(stack.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof ShulkerBoxBlock)) return;

        CachedData data = cache.get(stack);
        if (data == null) {
            List<ItemStack> items = parseShulkerContents(stack);
            if (items.isEmpty()) return;

            ItemStack mostCommon = ItemStack.EMPTY;
            int maxCount = -1;
            for (ItemStack is : items) {
                if (is.getCount() > maxCount) {
                    maxCount = is.getCount();
                    mostCommon = is;
                }
            }

            ItemStack renderStack = mostCommon.copy();
            renderStack.setCount(1); // Для рендера достаточно одного предмета

            data = new CachedData(renderStack, items.size() > 1);
            cache.put(stack, data);
        }

        if (data.mostCommonStack == null || data.mostCommonStack.isEmpty()) return;

        int iconSize = ((Setting<Integer>) module.settings.get("icon-size")).get();
        IconPosition pos = ((Setting<IconPosition>) module.settings.get("icon-position")).get();

        int iconX, iconY;
        switch (pos) {
            case BottomLeft -> { iconX = x; iconY = y + 16 - iconSize; }
            case TopRight   -> { iconX = x + 16 - iconSize; iconY = y; }
            case TopLeft    -> { iconX = x; iconY = y; }
            case Center     -> { iconX = x + (16 - iconSize) / 2; iconY = y + (16 - iconSize) / 2; }
            default         -> { iconX = x + 16 - iconSize; iconY = y + 16 - iconSize; }
        }

        context.getMatrices().pushMatrix();
        if (iconSize == 16) {
            context.drawItem(data.mostCommonStack, iconX, iconY);
        } else {
            float scale = iconSize / 16.0f;
            context.getMatrices().translate(iconX, iconY);
            context.getMatrices().scale(scale, scale);
            context.drawItem(data.mostCommonStack, 0, 0);
        }
        context.getMatrices().popMatrix();

        Setting<Boolean> showMultiSetting = (Setting<Boolean>) module.settings.get("show-multiple");

        if (showMultiSetting != null && showMultiSetting.get() && data.hasMultiple) {
            String multiText = ((Setting<String>) module.settings.get("multiple-indicator")).get();

            if (multiText != null && !multiText.isEmpty()) {
                int multiSize = ((Setting<Integer>) module.settings.get("multiple-size")).get();
                MinecraftClient mc = MinecraftClient.getInstance();

                float textScale = multiSize / 9.0f;
                int textWidth = mc.textRenderer.getWidth(multiText);

                float textX = (x + 16) - (textWidth * textScale) - 1;
                float textY = y + 1;

                context.getMatrices().pushMatrix();
                context.getMatrices().translate(textX, textY);
                context.getMatrices().scale(textScale, textScale);
                context.drawText(mc.textRenderer, multiText, 0, 0, 0xFFFFFF00, true);
                context.getMatrices().popMatrix();
            }
        }
    }

    public static List<ItemStack> parseShulkerContents(ItemStack shulkerStack) {
        List<ItemStack> mergedList = new ArrayList<>();
        ContainerComponent container = shulkerStack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            List<ItemStack> items = container.stream().toList();
            for (ItemStack itemStack : items) {
                mergeStack(mergedList, itemStack);
            }
            if (!mergedList.isEmpty()) {
                return mergedList;
            }
        }

        NbtComponent customData = shulkerStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        if (customData != null && !customData.isEmpty()) {
            NbtCompound nbt = customData.copyNbt();
            if (nbt.contains("BlockEntityTag")) {
                var optional = nbt.getCompound("BlockEntityTag");
                if (optional.isPresent()) {
                    NbtCompound blockEntityTag = optional.get();
                    if (blockEntityTag.contains("Items")) {
                        var itemsListOpt = blockEntityTag.getList("Items");
                        if (itemsListOpt.isEmpty()) return mergedList;
                        NbtList items = itemsListOpt.get();
                        for (int i = 0; i < items.size(); i++) {
                            var itemOpt = items.getCompound(i);
                            if (itemOpt.isPresent()) {
                                ItemStack parsed = parseItemFromNbt(itemOpt.get());
                                mergeStack(mergedList, parsed);
                            }
                        }
                    }
                }
            }
        }
        return mergedList;
    }

    private static void mergeStack(List<ItemStack> list, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (ItemStack existing : list) {
            if (ItemStack.areItemsAndComponentsEqual(existing, stack)) {
                existing.setCount(existing.getCount() + stack.getCount());
                return;
            }
        }
        list.add(stack.copy());
    }

    private static ItemStack parseItemFromNbt(NbtCompound itemTag) {
        String id = itemTag.getString("id", "");
        if (id.isEmpty()) return ItemStack.EMPTY;
        int count = 1;
        if (itemTag.contains("count")) {
            count = itemTag.getInt("count", 1);
        } else if (itemTag.contains("Count")) {
            count = itemTag.getByte("Count", (byte) 1);
        }
        Identifier itemId = Identifier.tryParse(id);
        if (itemId == null) return ItemStack.EMPTY;
        Item item = Registries.ITEM.get(itemId);
        if (item == Registries.ITEM.get(Registries.ITEM.getDefaultId())) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, count);
    }
}
