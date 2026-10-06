package com.RegalManiac.addon.modules.player;

import com.RegalManiac.addon.utils.automation.AutomationUtils;
import com.RegalManiac.addon.utils.automation.SmithingUtils;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.SmithingTableBlock;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class AutoSmithingPlus extends Module {
    public final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final SettingGroup sgItems = settings.createGroup("Items to Upgrade");
    public final SettingGroup sgTrims = settings.createGroup("Armor Trims");

    // --- General Settings ---
    public final Setting<AutomationUtils.ClickMode> actionType = sgGeneral.add(new EnumSetting.Builder<AutomationUtils.ClickMode>().name("action-type").description("Which click method to use.").defaultValue(AutomationUtils.ClickMode.METEOR).build());
    public final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder().name("action-delay").description("Minimum delay between clicks in ticks.").defaultValue(1).min(1).build());
    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").description("Maximum distance to interact with containers and smithing tables.").defaultValue(4.0).min(1.0).sliderMax(6.0).build());
    public final Setting<Integer> itemsPerCycle = sgGeneral.add(new IntSetting.Builder().name("items-per-cycle").description("How many items to process at once.").defaultValue(5).min(1).sliderMax(27).build());
    public final Setting<Boolean> silentScreen = sgGeneral.add(new BoolSetting.Builder().name("silent-screen").description("Allows looking around and opening GUI while container screens are active.").defaultValue(true).build());
    public final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder().name("rotate").description("Rotate to block when interacting.").defaultValue(true).build());
    public final Setting<Boolean> useTemplates = sgGeneral.add(new BoolSetting.Builder().name("use-smithing-templates").description("Use Netherite Upgrade Smithing Templates.").defaultValue(true).build());
    public final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder().name("debug").description("Show detailed information in chat.").defaultValue(true).build());

    // --- Item Settings ---
    public final Setting<Boolean> upgradeSpears = sgItems.add(new BoolSetting.Builder().name("upgrade-spears").defaultValue(true).build());
    public final Setting<Boolean> upgradeSwords = sgItems.add(new BoolSetting.Builder().name("upgrade-swords").defaultValue(true).build());
    public final Setting<Boolean> upgradePickaxes = sgItems.add(new BoolSetting.Builder().name("upgrade-pickaxes").defaultValue(true).build());
    public final Setting<Boolean> upgradeAxes = sgItems.add(new BoolSetting.Builder().name("upgrade-axes").defaultValue(true).build());
    public final Setting<Boolean> upgradeShovels = sgItems.add(new BoolSetting.Builder().name("upgrade-shovels").defaultValue(true).build());
    public final Setting<Boolean> upgradeHoes = sgItems.add(new BoolSetting.Builder().name("upgrade-hoes").defaultValue(true).build());
    public final Setting<Boolean> upgradeHelmets = sgItems.add(new BoolSetting.Builder().name("upgrade-helmets").defaultValue(true).build());
    public final Setting<Boolean> upgradeChestplates = sgItems.add(new BoolSetting.Builder().name("upgrade-chestplates").defaultValue(true).build());
    public final Setting<Boolean> upgradeLeggings = sgItems.add(new BoolSetting.Builder().name("upgrade-leggings").defaultValue(true).build());
    public final Setting<Boolean> upgradeBoots = sgItems.add(new BoolSetting.Builder().name("upgrade-boots").defaultValue(true).build());

    // --- Trim Settings ---
    public final Setting<Boolean> applyTrims = sgTrims.add(new BoolSetting.Builder().name("apply-trims").defaultValue(false).build());
    public final Setting<Boolean> trimDiamondArmor = sgTrims.add(new BoolSetting.Builder().name("trim-diamond-armor").defaultValue(true).build());
    public final Setting<Boolean> trimNetheriteArmor = sgTrims.add(new BoolSetting.Builder().name("trim-netherite-armor").defaultValue(true).build());

    public final Setting<SmithingUtils.TrimTemplateType> helmetTrimTemplate = sgTrims.add(new EnumSetting.Builder<SmithingUtils.TrimTemplateType>().name("helmet-trim-template").defaultValue(SmithingUtils.TrimTemplateType.NONE).build());
    public final Setting<SmithingUtils.TrimMaterialType> helmetTrimMaterial = sgTrims.add(new EnumSetting.Builder<SmithingUtils.TrimMaterialType>().name("helmet-trim-material").defaultValue(SmithingUtils.TrimMaterialType.NONE).build());
    public final Setting<SmithingUtils.TrimTemplateType> chestplateTrimTemplate = sgTrims.add(new EnumSetting.Builder<SmithingUtils.TrimTemplateType>().name("chestplate-trim-template").defaultValue(SmithingUtils.TrimTemplateType.NONE).build());
    public final Setting<SmithingUtils.TrimMaterialType> chestplateTrimMaterial = sgTrims.add(new EnumSetting.Builder<SmithingUtils.TrimMaterialType>().name("chestplate-trim-material").defaultValue(SmithingUtils.TrimMaterialType.NONE).build());
    public final Setting<SmithingUtils.TrimTemplateType> leggingsTrimTemplate = sgTrims.add(new EnumSetting.Builder<SmithingUtils.TrimTemplateType>().name("leggings-trim-template").defaultValue(SmithingUtils.TrimTemplateType.NONE).build());
    public final Setting<SmithingUtils.TrimMaterialType> leggingsTrimMaterial = sgTrims.add(new EnumSetting.Builder<SmithingUtils.TrimMaterialType>().name("leggings-trim-material").defaultValue(SmithingUtils.TrimMaterialType.NONE).build());
    public final Setting<SmithingUtils.TrimTemplateType> bootsTrimTemplate = sgTrims.add(new EnumSetting.Builder<SmithingUtils.TrimTemplateType>().name("boots-trim-template").defaultValue(SmithingUtils.TrimTemplateType.NONE).build());
    public final Setting<SmithingUtils.TrimMaterialType> bootsTrimMaterial = sgTrims.add(new EnumSetting.Builder<SmithingUtils.TrimMaterialType>().name("boots-trim-material").defaultValue(SmithingUtils.TrimMaterialType.NONE).build());

    private enum State { SCAN, INDEXING, FETCH_ITEM, FETCH_MATERIALS, SMITHING, STORING }
    private State currentState = State.SCAN;

    private final Map<BlockPos, List<ItemStack>> shulkerCache = new HashMap<>();
    private final List<BlockPos> shulkerList = new ArrayList<>();
    private final Set<Integer> personalSlots = new HashSet<>();
    private final Queue<Runnable> actionQueue = new LinkedList<>();

    private BlockPos itemOriginPos = null;
    private BlockPos lastShulker = null;
    private BlockPos activeBlockPos = null;
    private int timer = 0;
    private int scanIdx = 0;
    private int guiTimeout = 0;

    public AutoSmithingPlus() {
        super(Categories.Player, "auto-smithing-+", "Advanced shulker-based auto smithing.");
    }

    @Override
    public void onActivate() {
        actionQueue.clear();
        timer = 0;
        guiTimeout = 0;
        if (currentState == null) currentState = State.SCAN;

        if (personalSlots.isEmpty() && mc.player != null) {
            for (int i = 0; i < 36; i++) {
                if (!mc.player.getInventory().getStack(i).isEmpty()) {
                    personalSlots.add(i);
                }
            }
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!isActive() || !silentScreen.get()) return;

        if (currentState == State.SCAN) return;

        if (activeBlockPos != null && AutomationUtils.getDistanceToBlock(activeBlockPos) > range.get()) return;

        if (event.screen instanceof HandledScreen) {
            event.cancel();
        }
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WButton clearBtn = list.add(theme.button("Clear Memory")).expandX().widget();
        clearBtn.action = this::resetMemory;
        return list;
    }

    private void resetMemory() {
        shulkerCache.clear();
        actionQueue.clear();
        personalSlots.clear();
        lastShulker = null;
        itemOriginPos = null;
        activeBlockPos = null;
        scanIdx = 0;
        guiTimeout = 0;
        currentState = State.SCAN;
        if (isActive()) {
            if (debug.get()) ChatUtils.info("Memory wiped...");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (activeBlockPos != null && AutomationUtils.getDistanceToBlock(activeBlockPos) > range.get()) {
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                mc.player.closeHandledScreen();
            }
            if (!actionQueue.isEmpty()) actionQueue.clear();
            guiTimeout = 0;
            return;
        }

        if (AutomationUtils.isPaused()) return;

        boolean isGuiOpen = mc.player.currentScreenHandler != mc.player.playerScreenHandler;

        if (AutomationUtils.isInventoryFull(personalSlots) && findFinishedInInv() == -1 && currentState != State.STORING) {
            if (debug.get()) ChatUtils.warning("Inventory is full! Disabling.");
            if (isActive()) toggle();
            return;
        }

        if (!actionQueue.isEmpty()) {
            if (!isGuiOpen) {
                guiTimeout++;
                if (guiTimeout > 40) {
                    if (debug.get()) ChatUtils.warning("GUI lag! Clearing queue...");
                    actionQueue.clear();
                    guiTimeout = 0;
                }
                return;
            }

            if (guiTimeout > 0) guiTimeout = 0;

            if (timer <= 0) {
                actionQueue.poll().run();
                timer = actionDelay.get();
            } else {
                timer--;
            }
            return;
        } else {
            guiTimeout = 0;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        int finishedIdx = findFinishedInInv();
        if (finishedIdx != -1 && currentState != State.STORING && !isGuiOpen) {
            currentState = State.STORING;
            return;
        } else if (finishedIdx == -1 && currentState == State.STORING && actionQueue.isEmpty()) {
            itemOriginPos = null;
            currentState = State.FETCH_ITEM;
            return;
        }

        switch (currentState) {
            case SCAN -> {
                if (debug.get()) ChatUtils.info("Scanning for containers...");
                shulkerList.clear();
                shulkerList.addAll(AutomationUtils.findContainers(range.get()));
                scanIdx = 0;
                currentState = State.INDEXING;
            }
            case INDEXING -> doIndexing();
            case FETCH_ITEM -> { if (!findAndTakeItem()) { resetMemory(); ChatUtils.info("All items processed. Disabling..."); toggle(); } }
            case FETCH_MATERIALS -> { if (!findAndTakeMaterials()) currentState = State.SMITHING; }
            case SMITHING -> doSmithing();
            case STORING -> doStoring();
        }
    }

    private void doIndexing() {
        if (scanIdx >= shulkerList.size()) {
            if (debug.get()) ChatUtils.info("Indexing finished. Found " + shulkerCache.size() + " containers.");
            currentState = State.FETCH_ITEM;
            return;
        }
        BlockPos pos = shulkerList.get(scanIdx);
        activeBlockPos = pos;

        boolean isOpen = mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler
            || mc.player.currentScreenHandler instanceof GenericContainerScreenHandler;

        if (!isOpen) {
            AutomationUtils.open(pos, rotate.get());
        } else {
            List<ItemStack> items = new ArrayList<>();
            int size = mc.player.currentScreenHandler.slots.size() - 36;
            for (int i = 0; i < size; i++) items.add(mc.player.currentScreenHandler.getSlot(i).getStack().copy());
            shulkerCache.put(pos, items);
            scanIdx++;
            mc.player.closeHandledScreen();
            activeBlockPos = null;
        }
        timer = actionDelay.get();
    }

    private boolean findAndTakeItem() {
        if (findTargetInInv() != -1 && AutomationUtils.getFreeInventorySlots(personalSlots) == 0) {
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                mc.player.closeHandledScreen();
                activeBlockPos = null;
            }
            currentState = State.FETCH_MATERIALS;
            return true;
        }

        int freeSlots = AutomationUtils.getFreeInventorySlots(personalSlots);
        int toTake = Math.min(itemsPerCycle.get(), freeSlots);
        if (toTake <= 0) {
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                mc.player.closeHandledScreen();
                activeBlockPos = null;
            }
            return false;
        }

        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler && lastShulker != null && shulkerCache.containsKey(lastShulker)) {
            List<ItemStack> items = shulkerCache.get(lastShulker);
            List<Integer> slotsToTake = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).isEmpty() && SmithingUtils.getNextTask(items.get(i), this) != null) {
                    slotsToTake.add(i);
                    if (slotsToTake.size() >= toTake) break;
                }
            }

            if (!slotsToTake.isEmpty()) {
                itemOriginPos = lastShulker;
                activeBlockPos = lastShulker;
                for (int slotId : slotsToTake) {
                    int finalI = slotId;
                    actionQueue.add(() -> AutomationUtils.quickMove(finalI, actionType.get()));
                    actionQueue.add(() -> items.set(finalI, ItemStack.EMPTY));
                }
                actionQueue.add(() -> {
                    mc.player.closeHandledScreen();
                    activeBlockPos = null;
                });
                currentState = State.FETCH_MATERIALS;
                timer = actionDelay.get();
                return true;
            }
        }

        for (var entry : shulkerCache.entrySet()) {
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler && entry.getKey().equals(lastShulker)) continue;

            List<ItemStack> items = entry.getValue();
            List<Integer> slotsToTake = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).isEmpty() && SmithingUtils.getNextTask(items.get(i), this) != null) {
                    slotsToTake.add(i);
                    if (slotsToTake.size() >= toTake) break;
                }
            }

            if (!slotsToTake.isEmpty()) {
                if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) mc.player.closeHandledScreen();
                itemOriginPos = entry.getKey();
                lastShulker = itemOriginPos;
                activeBlockPos = lastShulker;
                AutomationUtils.open(lastShulker, rotate.get());
                timer = actionDelay.get();
                return true;
            }
        }

        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            mc.player.closeHandledScreen();
            activeBlockPos = null;
        }
        return false;
    }

    private boolean findAndTakeMaterials() {
        Map<Item, Integer> requiredItems = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            SmithingUtils.SmithingTask task = SmithingUtils.getNextTask(s, this);
            if (task != null) {
                if (task.template() != null) requiredItems.put(task.template(), requiredItems.getOrDefault(task.template(), 0) + 1);
                if (task.material() != null) requiredItems.put(task.material(), requiredItems.getOrDefault(task.material(), 0) + 1);
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
            boolean isOpen = mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler
                || mc.player.currentScreenHandler instanceof GenericContainerScreenHandler;

            if (!isOpen || !bestShulker.equals(lastShulker)) {
                if (isOpen) mc.player.closeHandledScreen();
                lastShulker = bestShulker;
                activeBlockPos = lastShulker;
                AutomationUtils.open(lastShulker, rotate.get());
                timer = actionDelay.get();
                return true;
            }

            activeBlockPos = lastShulker;
            int finalI = bestSlot;
            actionQueue.add(() -> AutomationUtils.quickMove(finalI, actionType.get()));
            actionQueue.add(() -> {
                shulkerCache.get(lastShulker).set(finalI, ItemStack.EMPTY);
                mc.player.closeHandledScreen();
                activeBlockPos = null;
            });
            timer = actionDelay.get();
            return true;
        }

        if (debug.get()) ChatUtils.error("Missing material: " + missingItem + ". Disabling.");
        toggle();
        return true;
    }

    private void doSmithing() {
        if (!(mc.player.currentScreenHandler instanceof SmithingScreenHandler h)) {
            BlockPos table = findSmithingTable();
            if (table == null) { if (debug.get()) ChatUtils.error("No smithing table nearby!"); toggle(); return; }
            activeBlockPos = table;
            AutomationUtils.open(table, rotate.get());
            timer = actionDelay.get();
            return;
        }

        ItemStack s0 = h.getSlot(0).getStack();
        ItemStack s1 = h.getSlot(1).getStack();
        ItemStack s2 = h.getSlot(2).getStack();
        ItemStack s3 = h.getSlot(3).getStack();

        if (!s3.isEmpty()) {
            guiTimeout = 0;
            AutomationUtils.quickMove(3, actionType.get());
            timer = actionDelay.get();
            return;
        }

        SmithingUtils.SmithingTask task = null;
        if (!s1.isEmpty()) {
            task = SmithingUtils.getNextTask(s1, this);
        } else {
            int targetIdx = findTargetInInv();
            if (targetIdx != -1) {
                task = SmithingUtils.getNextTask(mc.player.getInventory().getStack(targetIdx), this);
            }
        }

        if (task == null) {
            if (!s0.isEmpty()) { AutomationUtils.quickMove(0, actionType.get()); timer = actionDelay.get(); return; }
            if (!s1.isEmpty()) { AutomationUtils.quickMove(1, actionType.get()); timer = actionDelay.get(); return; }
            if (!s2.isEmpty()) { AutomationUtils.quickMove(2, actionType.get()); timer = actionDelay.get(); return; }

            mc.player.closeHandledScreen();
            activeBlockPos = null;
            currentState = State.STORING;
            return;
        }

        boolean missingTemplateInInv = task.template() != null && s0.isEmpty() && findItemInInv(task.template()) == -1;
        boolean missingMaterialInInv = task.material() != null && s2.isEmpty() && findItemInInv(task.material()) == -1;

        if (missingTemplateInInv || missingMaterialInInv) {
            if (debug.get()) ChatUtils.warning("Not enough materials, going back to fetch more!");
            mc.player.closeHandledScreen();
            activeBlockPos = null;
            currentState = State.FETCH_MATERIALS;
            timer = actionDelay.get();
            return;
        }

        if (!s0.isEmpty() && s0.getItem() != task.template()) { AutomationUtils.quickMove(0, actionType.get()); timer = actionDelay.get(); return; }
        if (!s2.isEmpty() && s2.getItem() != task.material()) { AutomationUtils.quickMove(2, actionType.get()); timer = actionDelay.get(); return; }
        if (!s1.isEmpty() && SmithingUtils.getNextTask(s1, this) == null) { AutomationUtils.quickMove(1, actionType.get()); timer = actionDelay.get(); return; }

        if (s0.isEmpty() && task.template() != null) {
            int invIdx = findItemInInv(task.template());
            if (invIdx != -1) { guiTimeout = 0; transferToSmithing(invIdx); return; }
        }

        if (s1.isEmpty()) {
            int targetIdx = findTargetInInv();
            if (targetIdx != -1) { guiTimeout = 0; transferToSmithing(targetIdx); return; }
        }

        if (s2.isEmpty() && task.material() != null) {
            int invIdx = findItemInInv(task.material());
            if (invIdx != -1) { guiTimeout = 0; transferToSmithing(invIdx); return; }
        }

        if (!s0.isEmpty() && !s1.isEmpty() && (!s2.isEmpty() || task.material() == null)) {
            guiTimeout++;
            if (guiTimeout > 40) {
                if (debug.get()) ChatUtils.error("Server didn't provide smithing result in time!");
                toggle();
            }
        } else {
            guiTimeout = 0;
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
            toggle();
            return;
        }

        activeBlockPos = targetShulker;
        boolean isOpen = mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler
            || mc.player.currentScreenHandler instanceof GenericContainerScreenHandler;

        if (!isOpen || !targetShulker.equals(lastShulker)) {
            if (isOpen) mc.player.closeHandledScreen();
            lastShulker = targetShulker;
            AutomationUtils.open(lastShulker, rotate.get());
            timer = actionDelay.get();
        } else {
            int containerSize = mc.player.currentScreenHandler.slots.size() - 36;
            List<ItemStack> shulkerItems = shulkerCache.get(lastShulker);

            int freeSpaces = 0;
            if (shulkerItems != null) {
                for (ItemStack s : shulkerItems) {
                    if (s.isEmpty()) freeSpaces++;
                }
            }

            int toStore = Math.min(finishedSlots.size(), freeSpaces);

            if (toStore > 0 && actionQueue.isEmpty()) {
                for (int i = 0; i < toStore; i++) {
                    int guiSlot = AutomationUtils.invToGui(finishedSlots.get(i), containerSize);
                    actionQueue.add(() -> AutomationUtils.quickMove(guiSlot, actionType.get()));
                }

                actionQueue.add(() -> {
                    updateCacheFromScreen();
                    mc.player.closeHandledScreen();
                    activeBlockPos = null;
                    timer = actionDelay.get();
                });
            } else if (toStore == 0) {
                updateCacheFromScreen();
                mc.player.closeHandledScreen();
                activeBlockPos = null;
                timer = actionDelay.get();
            }
        }
    }

    private BlockPos findShulkerWithSpace(BlockPos preferred) {
        if (preferred != null && AutomationUtils.hasSpace(shulkerCache.get(preferred))) return preferred;
        for (BlockPos pos : shulkerCache.keySet()) if (AutomationUtils.hasSpace(shulkerCache.get(pos))) return pos;
        return null;
    }

    private void updateCacheFromScreen() {
        if (lastShulker == null || mc.player.currentScreenHandler == null) return;
        List<ItemStack> items = new ArrayList<>();
        int size = mc.player.currentScreenHandler.slots.size() - 36;
        for (int i = 0; i < size; i++) items.add(mc.player.currentScreenHandler.getSlot(i).getStack().copy());
        shulkerCache.put(lastShulker, items);
    }

    private int findFinishedInInv() {
        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && SmithingUtils.getNextTask(s, this) == null && SmithingUtils.isAnyTrackedItem(s.getItem())) return i;
        }
        return -1;
    }

    private int findTargetInInv() {
        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && SmithingUtils.getNextTask(s, this) != null) return i;
        }
        return -1;
    }

    private void transferToSmithing(int invSlot) {
        int guiSlot = AutomationUtils.invToGui(invSlot, 4);
        AutomationUtils.quickMove(guiSlot, actionType.get());
        timer = actionDelay.get();
    }

    private List<Integer> findAllFinishedInInv() {
        List<Integer> finished = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && SmithingUtils.getNextTask(s, this) == null && SmithingUtils.isAnyTrackedItem(s.getItem())) {
                finished.add(i);
            }
        }
        return finished;
    }

    private int findItemInInv(Item item) {
        if (item == null) return -1;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private BlockPos findSmithingTable() {
        BlockPos p = mc.player.getBlockPos();
        int r = (int) Math.ceil(range.get());
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = p.add(x, y, z);
                    if (AutomationUtils.getDistanceToBlock(pos) <= range.get()) {
                        if (mc.world.getBlockState(pos).getBlock() instanceof SmithingTableBlock) return pos;
                    }
                }
            }
        }
        return null;
    }
}
