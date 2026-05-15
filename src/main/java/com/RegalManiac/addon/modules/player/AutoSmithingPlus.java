package com.RegalManiac.addon.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SmithingTableBlock;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.screen.ingame.SmithingScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimMaterials;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.item.equipment.trim.ArmorTrimPatterns;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class AutoSmithingPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgItems = settings.createGroup("Items to Upgrade");
    private final SettingGroup sgTrims = settings.createGroup("Armor Trims");

    // --- General Settings ---
    private final Setting<Integer> reach = sgGeneral.add(new IntSetting.Builder().name("reach-distance").description("Maximum distance to interact with containers and smithing tables.").defaultValue(4).build());
    private final Setting<Integer> containerDelay = sgGeneral.add(new IntSetting.Builder().name("container-delay").description("Delay between opening/closing containers and moving items.").defaultValue(10).min(1).build());
    private final Setting<Integer> smithingDelay = sgGeneral.add(new IntSetting.Builder().name("smithing-delay").description("Delay between individual clicks inside the smithing interface.").defaultValue(10).min(1).build());
    private final Setting<Integer> itemsPerCycle = sgGeneral.add(new IntSetting.Builder().name("items-per-cycle").description("How many items to process at once.").defaultValue(5).min(1).sliderMax(36).build());
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder().name("rotate").description("Rotate to block when interacting.").defaultValue(true).build());
    private final Setting<Boolean> useTemplates = sgGeneral.add(new BoolSetting.Builder().name("use-smithing-templates").description("Use Netherite Upgrade Smithing Templates.").defaultValue(true).build());
    private final Setting<Boolean> enableSounds = sgGeneral.add(new BoolSetting.Builder().name("sound-notification").description("Plays notification sounds for events.").defaultValue(true).build());
    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder().name("debug").description("Show detailed information in chat.").defaultValue(true).build());

    // --- Item Settings ---
    private final Setting<Boolean> upgradeSpears = sgItems.add(new BoolSetting.Builder().name("upgrade-spears").defaultValue(true).build());
    private final Setting<Boolean> upgradeSwords = sgItems.add(new BoolSetting.Builder().name("upgrade-swords").defaultValue(true).build());
    private final Setting<Boolean> upgradePickaxes = sgItems.add(new BoolSetting.Builder().name("upgrade-pickaxes").defaultValue(true).build());
    private final Setting<Boolean> upgradeAxes = sgItems.add(new BoolSetting.Builder().name("upgrade-axes").defaultValue(true).build());
    private final Setting<Boolean> upgradeShovels = sgItems.add(new BoolSetting.Builder().name("upgrade-shovels").defaultValue(true).build());
    private final Setting<Boolean> upgradeHoes = sgItems.add(new BoolSetting.Builder().name("upgrade-hoes").defaultValue(true).build());
    private final Setting<Boolean> upgradeHelmets = sgItems.add(new BoolSetting.Builder().name("upgrade-helmets").defaultValue(true).build());
    private final Setting<Boolean> upgradeChestplates = sgItems.add(new BoolSetting.Builder().name("upgrade-chestplates").defaultValue(true).build());
    private final Setting<Boolean> upgradeLeggings = sgItems.add(new BoolSetting.Builder().name("upgrade-leggings").defaultValue(true).build());
    private final Setting<Boolean> upgradeBoots = sgItems.add(new BoolSetting.Builder().name("upgrade-boots").defaultValue(true).build());

    // --- Trim Settings ---
    private final Setting<Boolean> applyTrims = sgTrims.add(new BoolSetting.Builder().name("apply-trims").defaultValue(false).build());
    private final Setting<Boolean> trimDiamondArmor = sgTrims.add(new BoolSetting.Builder().name("trim-diamond-armor").defaultValue(true).build());
    private final Setting<Boolean> trimNetheriteArmor = sgTrims.add(new BoolSetting.Builder().name("trim-netherite-armor").defaultValue(true).build());

    private final Setting<TrimTemplateType> helmetTrimTemplate = sgTrims.add(new EnumSetting.Builder<TrimTemplateType>().name("helmet-trim-template").defaultValue(TrimTemplateType.NONE).build());
    private final Setting<TrimMaterialType> helmetTrimMaterial = sgTrims.add(new EnumSetting.Builder<TrimMaterialType>().name("helmet-trim-material").defaultValue(TrimMaterialType.NONE).build());
    private final Setting<TrimTemplateType> chestplateTrimTemplate = sgTrims.add(new EnumSetting.Builder<TrimTemplateType>().name("chestplate-trim-template").defaultValue(TrimTemplateType.NONE).build());
    private final Setting<TrimMaterialType> chestplateTrimMaterial = sgTrims.add(new EnumSetting.Builder<TrimMaterialType>().name("chestplate-trim-material").defaultValue(TrimMaterialType.NONE).build());
    private final Setting<TrimTemplateType> leggingsTrimTemplate = sgTrims.add(new EnumSetting.Builder<TrimTemplateType>().name("leggings-trim-template").defaultValue(TrimTemplateType.NONE).build());
    private final Setting<TrimMaterialType> leggingsTrimMaterial = sgTrims.add(new EnumSetting.Builder<TrimMaterialType>().name("leggings-trim-material").defaultValue(TrimMaterialType.NONE).build());
    private final Setting<TrimTemplateType> bootsTrimTemplate = sgTrims.add(new EnumSetting.Builder<TrimTemplateType>().name("boots-trim-template").defaultValue(TrimTemplateType.NONE).build());
    private final Setting<TrimMaterialType> bootsTrimMaterial = sgTrims.add(new EnumSetting.Builder<TrimMaterialType>().name("boots-trim-material").defaultValue(TrimMaterialType.NONE).build());

    private enum State { SCAN, INDEXING, FETCH_ITEM, FETCH_MATERIALS, SMITHING, STORING }
    private State currentState = State.SCAN;

    private final Map<BlockPos, List<ItemStack>> shulkerCache = new HashMap<>();
    private final List<BlockPos> shulkerList = new ArrayList<>();
    private final Set<Integer> personalSlots = new HashSet<>();
    private final Queue<Runnable> actionQueue = new LinkedList<>();

    private BlockPos itemOriginPos = null;
    private BlockPos lastShulker = null;
    private int timer = 0;
    private int scanIdx = 0;

    public AutoSmithingPlus() {
        super(Categories.Player, "auto-smithing-+", "Advanced shulker-based auto smithing.");
    }

    @Override
    public void onActivate() {
        resetMemory();
        if (mc.player != null) {
            for (int i = 0; i < 36; i++) {
                if (!mc.player.getInventory().getStack(i).isEmpty()) personalSlots.add(i);
            }
        }
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WButton clearBtn = list.add(theme.button("Clear Memory")).expandX().widget();
        clearBtn.action = () -> {
            resetMemory();
            if (isActive()) if (debug.get()) ChatUtils.info("Memory wiped. Rescanning...");notifyInfo();
        };
        return list;
    }

    private void resetMemory() {
        shulkerCache.clear();
        actionQueue.clear();
        lastShulker = null;
        itemOriginPos = null;
        scanIdx = 0;
        timer = 0;
        personalSlots.clear();
        currentState = State.SCAN;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (isPaused()) return;

        if (isInventoryFull() && findFinishedInInv() == -1 && currentState != State.STORING) {
            if (debug.get())ChatUtils.warning("Inventory is full! Disabling.");notifyWarning();
            toggle();
            return;
        }

        if (mc.currentScreen == null && !actionQueue.isEmpty()) actionQueue.clear();

        if (!actionQueue.isEmpty()) {
            if (mc.currentScreen == null) {
                timer--;
                if (timer < -40) {
                    if (debug.get())ChatUtils.warning("Server GUI lag! Clearing queue and retrying...");notifyWarning();
                    actionQueue.clear();
                    timer = 0;
                }
                return;
            }
            if (timer <= 0) {
                actionQueue.poll().run();
                timer = containerDelay.get();
            } else {
                timer--;
            }
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        if (findFinishedInInv() != -1 && currentState != State.STORING && mc.currentScreen == null) {
            currentState = State.STORING;
            return;
        }

        switch (currentState) {
            case SCAN -> {
                updateContainers();
                scanIdx = 0;
                currentState = State.INDEXING;
            }
            case INDEXING -> doIndexing();
            case FETCH_ITEM -> { if (!findAndTakeItem()) { resetMemory(); ChatUtils.info("All items processed. Disabling...");notifyFinish(); toggle(); } }
            case FETCH_MATERIALS -> { if (!findAndTakeMaterials()) currentState = State.SMITHING; }
            case SMITHING -> doSmithing();
            case STORING -> doStoring();
        }
    }

    private void doIndexing() {
        if (scanIdx >= shulkerList.size()) {
            if (debug.get())ChatUtils.info("Indexing finished. Found " + shulkerCache.size() + " containers.");notifyInfo();
            currentState = State.FETCH_ITEM;
            return;
        }
        BlockPos pos = shulkerList.get(scanIdx);
        if (!(mc.currentScreen instanceof ShulkerBoxScreen || mc.currentScreen instanceof GenericContainerScreen)) {
            open(pos);
            timer = 3;
        } else {
            List<ItemStack> items = new ArrayList<>();
            int size = mc.player.currentScreenHandler.slots.size() - 36;
            for (int i = 0; i < size; i++) items.add(mc.player.currentScreenHandler.getSlot(i).getStack().copy());
            shulkerCache.put(pos, items);
            scanIdx++;
            mc.player.closeHandledScreen();
            timer = containerDelay.get();
        }
    }

    private boolean findAndTakeItem() {
        if (findTargetInInv() != -1 && getFreeInventorySlots() == 0) {
            if (mc.currentScreen != null) mc.player.closeHandledScreen();
            currentState = State.FETCH_MATERIALS;
            return true;
        }

        int freeSlots = getFreeInventorySlots();
        int toTake = Math.min(itemsPerCycle.get(), freeSlots);
        if (toTake <= 0) {
            if (mc.currentScreen != null) mc.player.closeHandledScreen();
            return false;
        }

        if (mc.currentScreen != null && lastShulker != null && shulkerCache.containsKey(lastShulker)) {
            List<ItemStack> items = shulkerCache.get(lastShulker);
            List<Integer> slotsToTake = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).isEmpty() && getNextTask(items.get(i)) != null) {
                    slotsToTake.add(i);
                    if (slotsToTake.size() >= toTake) break;
                }
            }

            if (!slotsToTake.isEmpty()) {
                itemOriginPos = lastShulker;
                for (int slotId : slotsToTake) {
                    int finalI = slotId;
                    actionQueue.add(() -> mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, finalI, 0, SlotActionType.QUICK_MOVE, mc.player));
                    actionQueue.add(() -> items.set(finalI, ItemStack.EMPTY));
                }
                actionQueue.add(() -> mc.player.closeHandledScreen());
                currentState = State.FETCH_MATERIALS;
                timer = containerDelay.get();
                return true;
            }
        }

        for (var entry : shulkerCache.entrySet()) {
            if (mc.currentScreen != null && entry.getKey().equals(lastShulker)) continue;

            List<ItemStack> items = entry.getValue();
            List<Integer> slotsToTake = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).isEmpty() && getNextTask(items.get(i)) != null) {
                    slotsToTake.add(i);
                    if (slotsToTake.size() >= toTake) break;
                }
            }

            if (!slotsToTake.isEmpty()) {
                if (mc.currentScreen != null) mc.player.closeHandledScreen(); // Закрываем старый
                itemOriginPos = entry.getKey();
                lastShulker = itemOriginPos;
                open(lastShulker);
                return true;
            }
        }

        if (mc.currentScreen != null) mc.player.closeHandledScreen();
        return false;
    }

    private boolean findAndTakeMaterials() {
        Map<Item, Integer> requiredItems = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            SmithingTask task = getNextTask(s);
            if (task != null) {
                if (task.template != null) requiredItems.put(task.template, requiredItems.getOrDefault(task.template, 0) + 1);
                if (task.material != null) requiredItems.put(task.material, requiredItems.getOrDefault(task.material, 0) + 1);
            }
        }

        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && requiredItems.containsKey(s.getItem())) {
                int currentNeed = requiredItems.get(s.getItem());
                int newNeed = currentNeed - s.getCount();
                if (newNeed <= 0) requiredItems.remove(s.getItem());
                else requiredItems.put(s.getItem(), newNeed);
            }
        }

        if (requiredItems.isEmpty()) {
            currentState = State.SMITHING;
            return false;
        }

        Item missingItem = requiredItems.keySet().iterator().next();

        BlockPos bestShulker = null;
        int bestSlot = -1;
        int maxStack = -1;

        for (var entry : shulkerCache.entrySet()) {
            List<ItemStack> items = entry.getValue();
            for (int i = 0; i < items.size(); i++) {
                ItemStack s = items.get(i);
                if (s.getItem() == missingItem) {
                    if (s.getCount() > maxStack) {
                        maxStack = s.getCount();
                        bestSlot = i;
                        bestShulker = entry.getKey();
                    }
                }
            }
        }

        if (bestShulker != null) {
            if (!(mc.currentScreen instanceof ShulkerBoxScreen || mc.currentScreen instanceof GenericContainerScreen) || !bestShulker.equals(lastShulker)) {
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                lastShulker = bestShulker;
                open(lastShulker);
                return true;
            }

            int finalI = bestSlot;
            actionQueue.add(() -> mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, finalI, 0, SlotActionType.QUICK_MOVE, mc.player));
            actionQueue.add(() -> {
                shulkerCache.get(lastShulker).set(finalI, ItemStack.EMPTY);
                mc.player.closeHandledScreen();
            });
            timer = containerDelay.get();
            return true;
        }

        if (debug.get()) ChatUtils.error("Missing material: " + missingItem + ". Disabling.");
        notifyError();
        toggle();
        return true;
    }

    private void doSmithing() {
        if (!(mc.currentScreen instanceof SmithingScreen)) {
            BlockPos table = findSmithingTable();
            if (table == null) { if (debug.get())ChatUtils.error("No smithing table nearby!");notifyWarning(); toggle(); return; }
            open(table);
            timer = 3;
            return;
        }

        SmithingScreenHandler h = ((SmithingScreen) mc.currentScreen).getScreenHandler();

        ItemStack s0 = h.getSlot(0).getStack();
        ItemStack s1 = h.getSlot(1).getStack();
        ItemStack s2 = h.getSlot(2).getStack();
        ItemStack s3 = h.getSlot(3).getStack();

        if (!s3.isEmpty()) {
            mc.interactionManager.clickSlot(h.syncId, 3, 0, SlotActionType.QUICK_MOVE, mc.player);
            timer = smithingDelay.get();
            return;
        }

        SmithingTask task = null;
        if (!s1.isEmpty()) {
            task = getNextTask(s1);
        } else {
            int targetIdx = findTargetInInv();
            if (targetIdx != -1) {
                task = getNextTask(mc.player.getInventory().getStack(targetIdx));
            }
        }

        if (task == null) {
            if (!s0.isEmpty()) { mc.interactionManager.clickSlot(h.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player); timer = smithingDelay.get(); return; }
            if (!s1.isEmpty()) { mc.interactionManager.clickSlot(h.syncId, 1, 0, SlotActionType.QUICK_MOVE, mc.player); timer = smithingDelay.get(); return; }
            if (!s2.isEmpty()) { mc.interactionManager.clickSlot(h.syncId, 2, 0, SlotActionType.QUICK_MOVE, mc.player); timer = smithingDelay.get(); return; }

            mc.player.closeHandledScreen();
            currentState = State.STORING;
            return;
        }

        boolean missingTemplateInInv = task.template != null && s0.isEmpty() && findItemInInv(task.template) == -1;
        boolean missingMaterialInInv = task.material != null && s2.isEmpty() && findItemInInv(task.material) == -1;

        if (missingTemplateInInv || missingMaterialInInv) {
            if (debug.get()) ChatUtils.warning("Not enough materials, going back to fetch more!");
            mc.player.closeHandledScreen();
            currentState = State.FETCH_MATERIALS;
            timer = containerDelay.get();
            return;
        }

        if (!s0.isEmpty() && s0.getItem() != task.template) { mc.interactionManager.clickSlot(h.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player); timer = smithingDelay.get(); return; }
        if (!s2.isEmpty() && s2.getItem() != task.material) { mc.interactionManager.clickSlot(h.syncId, 2, 0, SlotActionType.QUICK_MOVE, mc.player); timer = smithingDelay.get(); return; }
        if (!s1.isEmpty() && getNextTask(s1) == null) { mc.interactionManager.clickSlot(h.syncId, 1, 0, SlotActionType.QUICK_MOVE, mc.player); timer = smithingDelay.get(); return; }

        if (s0.isEmpty() && task.template != null) {
            int invIdx = findItemInInv(task.template);
            if (invIdx != -1) { transferToSmithing(h.syncId, invIdx, 0); return; }
        }

        if (s1.isEmpty()) {
            int targetIdx = findTargetInInv();
            if (targetIdx != -1) { transferToSmithing(h.syncId, targetIdx, 1); return; }
        }

        if (s2.isEmpty() && task.material != null) {
            int invIdx = findItemInInv(task.material);
            if (invIdx != -1) { transferToSmithing(h.syncId, invIdx, 2); return; }
        }
    }

    private void doStoring() {
        List<Integer> finishedSlots = findAllFinishedInInv();
        if (finishedSlots.isEmpty()) {
            itemOriginPos = null;
            currentState = State.FETCH_ITEM;
            return;
        }

        BlockPos targetShulker = findShulkerWithSpace(itemOriginPos);
        if (targetShulker == null) {
            if (debug.get()) ChatUtils.warning("All containers full! Disabling.");
            notifyWarning();
            toggle();
            return;
        }

        if (!(mc.currentScreen instanceof ShulkerBoxScreen || mc.currentScreen instanceof GenericContainerScreen)) {
            lastShulker = targetShulker;
            open(lastShulker);
            timer = 3;
        } else {
            int containerSize = mc.player.currentScreenHandler.slots.size() - 36;
            int syncId = mc.player.currentScreenHandler.syncId;

            for (int idx : finishedSlots) {
                int guiSlot = invToGui(idx, containerSize);
                actionQueue.add(() -> mc.interactionManager.clickSlot(syncId, guiSlot, 0, SlotActionType.QUICK_MOVE, mc.player));
            }

            actionQueue.add(() -> {
                updateCacheFromScreen();

                boolean hasMoreItemsHere = false;
                List<ItemStack> currentItems = shulkerCache.get(lastShulker);
                if (currentItems != null) {
                    for (ItemStack s : currentItems) {
                        if (!s.isEmpty() && getNextTask(s) != null) {
                            hasMoreItemsHere = true;
                            break;
                        }
                    }
                }

                if (hasMoreItemsHere && getFreeInventorySlots() > 0) {
                    currentState = State.FETCH_ITEM;
                    timer = containerDelay.get();
                } else {
                    mc.player.closeHandledScreen();
                    itemOriginPos = null;
                    currentState = State.FETCH_ITEM;
                    timer = containerDelay.get();
                }
            });
        }
    }

    // --- Helpers ---

    private void transferToSmithing(int syncId, int invSlot, int guiSlotTarget) {
        int guiSlot = invToGui(invSlot, 4);
        mc.interactionManager.clickSlot(syncId, guiSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, guiSlotTarget, 0, SlotActionType.PICKUP, mc.player);
        timer = smithingDelay.get();
    }

    private SmithingTask getNextTask(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item item = stack.getItem();

        if (isDiamondUpgradable(item)) {
            Item template = useTemplates.get() ? Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE : null;
            return new SmithingTask(template, Items.NETHERITE_INGOT);
        }

        if (applyTrims.get() && isTrimmableArmor(item)) {
            TrimTemplateType tType = getTrimTemplateForArmor(item);
            TrimMaterialType mType = getTrimMaterialForArmor(item);

            if (tType != TrimTemplateType.NONE && mType != TrimMaterialType.NONE) {
                ArmorTrim existingTrim = stack.get(DataComponentTypes.TRIM);
                if (existingTrim == null || !existingTrim.pattern().matchesKey(tType.patternKey) || !existingTrim.material().matchesKey(mType.materialKey)) {
                    return new SmithingTask(tType.item, mType.item);
                }
            }
        }
        return null;
    }

    private boolean isInventoryFull() {
        for (int i = 0; i < 36; i++) {
            if (!personalSlots.contains(i)) {
                if (mc.player.getInventory().getStack(i).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private int getFreeInventorySlots() {
        int free = 0;
        for (int i = 0; i < 36; i++) {
            if (!personalSlots.contains(i) && mc.player.getInventory().getStack(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private List<Integer> findAllFinishedInInv() {
        List<Integer> finished = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && getNextTask(s) == null && isAnyTrackedItem(s.getItem())) {
                finished.add(i);
            }
        }
        return finished;
    }

    private int findFinishedInInv() {
        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && getNextTask(s) == null && isAnyTrackedItem(s.getItem())) return i;
        }
        return -1;
    }

    private int findTargetInInv() {
        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && getNextTask(s) != null) return i;
        }
        return -1;
    }

    private int findItemInInv(Item item) {
        if (item == null) return -1;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private BlockPos findShulkerWithSpace(BlockPos preferred) {
        if (preferred != null && hasSpace(preferred)) return preferred;
        for (BlockPos pos : shulkerCache.keySet()) if (hasSpace(pos)) return pos;
        return null;
    }

    private boolean hasSpace(BlockPos pos) {
        List<ItemStack> items = shulkerCache.get(pos);
        if (items == null) return false;
        for (ItemStack s : items) if (s.isEmpty()) return true;
        return false;
    }

    private void updateContainers() {
        shulkerList.clear();
        BlockPos p = mc.player.getBlockPos();
        int r = reach.get();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = p.add(x, y, z);
                    var block = mc.world.getBlockState(pos).getBlock();
                    if (block instanceof ShulkerBoxBlock || block instanceof ChestBlock || block instanceof BarrelBlock) {
                        shulkerList.add(pos);
                    }
                }
            }
        }
    }

    private BlockPos findSmithingTable() {
        BlockPos p = mc.player.getBlockPos();
        int r = reach.get();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = p.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() instanceof SmithingTableBlock) return pos;
                }
            }
        }
        return null;
    }

    private void open(BlockPos p) {
        if (p == null) return;
        if (rotate.get()) Rotations.rotate(Rotations.getYaw(p), Rotations.getPitch(p));
        BlockHitResult hitResult = new BlockHitResult(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), Direction.UP, p, true);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
        timer = containerDelay.get();
    }

    private void updateCacheFromScreen() {
        if (lastShulker == null || mc.player.currentScreenHandler == null) return;
        List<ItemStack> items = new ArrayList<>();
        int size = mc.player.currentScreenHandler.slots.size() - 36;
        for (int i = 0; i < size; i++) items.add(mc.player.currentScreenHandler.getSlot(i).getStack().copy());
        shulkerCache.put(lastShulker, items);
    }

    private boolean isPaused() {
        return mc.currentScreen != null &&
            !(mc.currentScreen instanceof ShulkerBoxScreen) &&
            !(mc.currentScreen instanceof GenericContainerScreen) &&
            !(mc.currentScreen instanceof SmithingScreen);
    }

    private int invToGui(int i, int offset) { return (i < 9) ? i + offset + 27 : i + offset - 9; }

    private boolean isAnyTrackedItem(Item item) {
        return isDiamondEquipment(item) || isNetheriteEquipment(item);
    }

    private boolean isDiamondEquipment(Item item) {
        return item == Items.DIAMOND_SPEAR || item == Items.DIAMOND_SWORD || item == Items.DIAMOND_PICKAXE ||
            item == Items.DIAMOND_AXE || item == Items.DIAMOND_SHOVEL ||
            item == Items.DIAMOND_HOE || item == Items.DIAMOND_HELMET ||
            item == Items.DIAMOND_CHESTPLATE || item == Items.DIAMOND_LEGGINGS ||
            item == Items.DIAMOND_BOOTS;
    }

    private boolean isNetheriteEquipment(Item item) {
        return item == Items.NETHERITE_SPEAR || item == Items.NETHERITE_SWORD || item == Items.NETHERITE_PICKAXE ||
            item == Items.NETHERITE_AXE || item == Items.NETHERITE_SHOVEL ||
            item == Items.NETHERITE_HOE || item == Items.NETHERITE_HELMET ||
            item == Items.NETHERITE_CHESTPLATE || item == Items.NETHERITE_LEGGINGS ||
            item == Items.NETHERITE_BOOTS;
    }

    private boolean isDiamondUpgradable(Item item) {
        if (item == Items.DIAMOND_SPEAR && upgradeSpears.get()) return true;
        if (item == Items.DIAMOND_SWORD && upgradeSwords.get()) return true;
        if (item == Items.DIAMOND_PICKAXE && upgradePickaxes.get()) return true;
        if (item == Items.DIAMOND_AXE && upgradeAxes.get()) return true;
        if (item == Items.DIAMOND_SHOVEL && upgradeShovels.get()) return true;
        if (item == Items.DIAMOND_HOE && upgradeHoes.get()) return true;
        if (item == Items.DIAMOND_HELMET && upgradeHelmets.get()) return true;
        if (item == Items.DIAMOND_CHESTPLATE && upgradeChestplates.get()) return true;
        if (item == Items.DIAMOND_LEGGINGS && upgradeLeggings.get()) return true;
        if (item == Items.DIAMOND_BOOTS && upgradeBoots.get()) return true;
        return false;
    }

    private boolean isTrimmableArmor(Item item) {
        boolean isDiamond = item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS;
        boolean isNetherite = item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS;
        return (trimDiamondArmor.get() && isDiamond) || (trimNetheriteArmor.get() && isNetherite);
    }

    private TrimTemplateType getTrimTemplateForArmor(Item item) {
        if (item == Items.DIAMOND_HELMET || item == Items.NETHERITE_HELMET) return helmetTrimTemplate.get();
        if (item == Items.DIAMOND_CHESTPLATE || item == Items.NETHERITE_CHESTPLATE) return chestplateTrimTemplate.get();
        if (item == Items.DIAMOND_LEGGINGS || item == Items.NETHERITE_LEGGINGS) return leggingsTrimTemplate.get();
        if (item == Items.DIAMOND_BOOTS || item == Items.NETHERITE_BOOTS) return bootsTrimTemplate.get();
        return TrimTemplateType.NONE;
    }

    private TrimMaterialType getTrimMaterialForArmor(Item item) {
        if (item == Items.DIAMOND_HELMET || item == Items.NETHERITE_HELMET) return helmetTrimMaterial.get();
        if (item == Items.DIAMOND_CHESTPLATE || item == Items.NETHERITE_CHESTPLATE) return chestplateTrimMaterial.get();
        if (item == Items.DIAMOND_LEGGINGS || item == Items.NETHERITE_LEGGINGS) return leggingsTrimMaterial.get();
        if (item == Items.DIAMOND_BOOTS || item == Items.NETHERITE_BOOTS) return bootsTrimMaterial.get();
        return TrimMaterialType.NONE;
    }

    private record SmithingTask(Item template, Item material) {}

    public enum TrimTemplateType {
        NONE("None", null, null),
        SENTRY("Sentry", Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.SENTRY),
        DUNE("Dune", Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.DUNE),
        COAST("Coast", Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.COAST),
        WILD("Wild", Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.WILD),
        WARD("Ward", Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.WARD),
        EYE("Eye", Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.EYE),
        VEX("Vex", Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.VEX),
        TIDE("Tide", Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.TIDE),
        SNOUT("Snout", Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.SNOUT),
        RIB("Rib", Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.RIB),
        SPIRE("Spire", Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.SPIRE),
        SILENCE("Silence", Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.SILENCE),
        WAYFINDER("Wayfinder", Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.WAYFINDER),
        RAISER("Raiser", Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.RAISER),
        SHAPER("Shaper", Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.SHAPER),
        HOST("Host", Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.HOST),
        FLOW("Flow", Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.FLOW),
        BOLT("Bolt", Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.BOLT);

        public final String title;
        public final Item item;
        public final RegistryKey<ArmorTrimPattern> patternKey;

        TrimTemplateType(String title, Item item, RegistryKey<ArmorTrimPattern> patternKey) {
            this.title = title; this.item = item; this.patternKey = patternKey;
        }
        @Override public String toString() { return title; }
    }

    public enum TrimMaterialType {
        NONE("None", null, null),
        QUARTZ("Quartz", Items.QUARTZ, ArmorTrimMaterials.QUARTZ),
        IRON("Iron", Items.IRON_INGOT, ArmorTrimMaterials.IRON),
        NETHERITE("Netherite", Items.NETHERITE_INGOT, ArmorTrimMaterials.NETHERITE),
        REDSTONE("Redstone", Items.REDSTONE, ArmorTrimMaterials.REDSTONE),
        COPPER("Copper", Items.COPPER_INGOT, ArmorTrimMaterials.COPPER),
        GOLD("Gold", Items.GOLD_INGOT, ArmorTrimMaterials.GOLD),
        EMERALD("Emerald", Items.EMERALD, ArmorTrimMaterials.EMERALD),
        DIAMOND("Diamond", Items.DIAMOND, ArmorTrimMaterials.DIAMOND),
        LAPIS("Lapis", Items.LAPIS_LAZULI, ArmorTrimMaterials.LAPIS),
        AMETHYST("Amethyst", Items.AMETHYST_SHARD, ArmorTrimMaterials.AMETHYST),
        RESIN("Resin", Items.RESIN_BRICK, ArmorTrimMaterials.RESIN);

        public final String title;
        public final Item item;
        public final RegistryKey<ArmorTrimMaterial> materialKey;

        TrimMaterialType(String title, Item item, RegistryKey<ArmorTrimMaterial> materialKey) {
            this.title = title; this.item = item; this.materialKey = materialKey;
        }
        @Override public String toString() { return title; }
    }

    private void playSound(String soundId) {
        if (mc.player == null || mc.world == null || !enableSounds.get()) return;

        String NS = "sindiaddon";
        net.minecraft.util.Identifier id = soundId.contains(":") ?
            net.minecraft.util.Identifier.of(soundId) :
            net.minecraft.util.Identifier.of(NS, soundId);

        mc.world.playSound(mc.player, mc.player.getBlockPos(),
            net.minecraft.sound.SoundEvent.of(id),
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private void notifyInfo() {
        playSound("fuzz");
    }

    private void notifyWarning() {
        playSound("klaxon");
    }

    private void notifyError() {
        playSound("alarm");
    }

    private void notifyFinish() {
        playSound("sayclear");
    }
}
