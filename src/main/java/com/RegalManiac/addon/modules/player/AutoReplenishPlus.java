package com.RegalManiac.addon.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AutoReplenishPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("The filter mode for replenishment.")
        .defaultValue(Mode.Whitelist)
        .build()
    );

    private final Setting<List<Item>> itemsFilter = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items to include or exclude from replenishment.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Integer> minCount = sgGeneral.add(new IntSetting.Builder()
        .name("min-count")
        .description("Replenish stackable items when the count falls below this value.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 63)
        .build()
    );

    private final Setting<Boolean> unstackable = sgGeneral.add(new BoolSetting.Builder()
        .name("unstackable")
        .description("Whether to replenish non-stackable items like potions or tools.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("The delay between replenishment attempts in ticks.")
        .defaultValue(1)
        .min(0)
        .build()
    );

    private final Setting<Boolean> offhand = sgGeneral.add(new BoolSetting.Builder()
        .name("offhand")
        .description("Whether to replenish items in your offhand slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> searchHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("search-hotbar")
        .description("Search the hotbar for items if they aren't found in the main inventory.")
        .defaultValue(false)
        .build()
    );

    private final ItemStack[] items = new ItemStack[10];
    private boolean prevHadOpenScreen;
    private int tickDelayLeft;

    public enum Mode {
        Whitelist,
        Blacklist
    }

    public AutoReplenishPlus() {
        super(Categories.Player, "auto-replenish-+", "Automatically refills items in your hotbar or offhand.");
        Arrays.fill(items, Items.AIR.getDefaultStack());
    }

    @Override
    public void onActivate() {
        fillItems();
        tickDelayLeft = tickDelay.get();
        prevHadOpenScreen = mc.currentScreen != null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.currentScreen == null && prevHadOpenScreen) fillItems();
        prevHadOpenScreen = mc.currentScreen != null;

        if (mc.player.currentScreenHandler.getStacks().size() != 46 || mc.currentScreen != null) return;

        if (tickDelayLeft > 0) {
            tickDelayLeft--;
            return;
        }

        for (int i = 0; i < 9; i++) {
            checkSlot(i, mc.player.getInventory().getStack(i));
        }

        if (offhand.get() && !Modules.get().get(AutoTotem.class).isLocked()) {
            checkSlot(9, mc.player.getOffHandStack());
        }

        tickDelayLeft = tickDelay.get();
    }

    private void checkSlot(int slot, ItemStack stack) {
        ItemStack prevStack = items[slot];
        items[slot] = stack.copy();

        boolean inList = itemsFilter.get().contains(stack.getItem()) || itemsFilter.get().contains(prevStack.getItem());
        if (mode.get() == Mode.Whitelist && !inList) return;
        if (mode.get() == Mode.Blacklist && inList) return;

        int targetSlot = (slot == 9) ? SlotUtils.OFFHAND : slot;

        boolean needsReplenish = false;
        if (stack.isStackable()) {
            if (!stack.isEmpty() && stack.getCount() <= minCount.get()) needsReplenish = true;
            else if (stack.isEmpty() && !prevStack.isEmpty()) needsReplenish = true;
        } else if (unstackable.get() && stack.isEmpty() && !prevStack.isEmpty()) {
            needsReplenish = true;
        }

        if (!needsReplenish) return;

        int safety = 0;
        while (stack.getCount() < stack.getMaxCount() && safety < 10) {
            int fromSlot = findSmallestStack(stack.isEmpty() ? prevStack : stack, targetSlot);

            if (fromSlot == -1) break;

            InvUtils.move().from(fromSlot).to(targetSlot);

            stack = mc.player.getInventory().getStack(slot == 9 ? 45 : slot);
            safety++;
        }
    }

    private int findSmallestStack(ItemStack lookFor, int excludedSlot) {
        int bestSlot = -1;
        int minCountFound = 65;

        for (int i = mc.player.getInventory().size() - 2; i >= (searchHotbar.get() ? 0 : 9); i--) {
            if (i == excludedSlot) continue;
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.isEmpty() || stack.getItem() != lookFor.getItem()) continue;
            if (!ItemStack.areItemsAndComponentsEqual(lookFor, stack)) continue;

            if (stack.getCount() < minCountFound) {
                minCountFound = stack.getCount();
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private void fillItems() {
        for (int i = 0; i < 9; i++) items[i] = mc.player.getInventory().getStack(i).copy();
        items[9] = mc.player.getOffHandStack().copy();
    }
}
