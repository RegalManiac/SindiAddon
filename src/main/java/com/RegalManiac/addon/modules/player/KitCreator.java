package com.RegalManiac.addon.modules.player;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import com.RegalManiac.addon.utils.automation.AutomationUtils;
import com.RegalManiac.addon.utils.automation.KitUtils;
import com.RegalManiac.addon.utils.automation.KitUtils.PresetItem;
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
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.*;

public class KitCreator extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<AutomationUtils.ClickMode> actionType = sgGeneral.add(new EnumSetting.Builder<AutomationUtils.ClickMode>().name("action-type").description("Which click method to use.").defaultValue(AutomationUtils.ClickMode.METEOR).build());
    public final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder().name("action-delay").description("Minimum delay between clicks in ticks.").defaultValue(1).min(1).build());
    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").description("Maximum distance to interact with containers.").defaultValue(4.0).min(1.0).sliderMax(6.0).build());
    public final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder().name("rotate").description("Turn to the block when interacting.").defaultValue(true).build());
    public final Setting<Boolean> silentScreen = sgGeneral.add(new BoolSetting.Builder().name("silent-screen").description("Allows looking around while GUI is active.").defaultValue(true).build());
    public final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder().name("debug").description("Show detailed information in chat.").defaultValue(true).build());
    public final Setting<Integer> stacksPerCycle = sgGeneral.add(new IntSetting.Builder().name("stacks-per-cycle").description("How many stacks to process in one cycle.").defaultValue(10).min(1).sliderMax(27).build());

    private enum State {IDLE, SCAN, INDEXING, FETCH_EMPTY_SHULKER, PLACE_SHULKER, FETCH_RESOURCES, STORE_RESOURCES, BREAK_FILLED, WAIT_FOR_FILLED_BREAK, PICKUP_FILLED_SHULKER, WALK_TO_CHEST, STORE_FILLED_SHULKER, WALK_BACK}

    private State currentState = State.IDLE;
    private final Map<BlockPos, List<ItemStack>> shulkerCache = new HashMap<>();
    private final List<BlockPos> containerList = new ArrayList<>();
    private final Set<Integer> personalSlots = new HashSet<>();
    private final Queue<Runnable> actionQueue = new LinkedList<>();

    private final List<PresetItem> activePreset = new ArrayList<>();
    private String activePresetName = "";

    private BlockPos startPos = null;
    private BlockPos emptyShulkersContainer = null;
    private BlockPos targetShulkerPos = null;
    private BlockPos activeBlockPos = null;

    private int fetchedForCurrentPresetItem = 0;
    private int currentFetchInvSlot = -1;

    private int storedForCurrentPresetItem = 0;
    private int currentStoreInvSlot = -1;

    private int presetIndex = 0;
    private int fetchIndex = 0;
    private int scanIdx = 0;
    private int timer = 0;
    private int guiTimeout = 0;

    public KitCreator() {
        super(Categories.Player, "kit-creator", "Automated kit assembly with Baritone.");
    }

    @Override
    public void onActivate() {
        KitUtils.getKitsFolder();

        actionQueue.clear();
        timer = 0;
        guiTimeout = 0;

        if (personalSlots.isEmpty() && mc.player != null) {
            for (int i = 0; i < 36; i++) {
                if (!mc.player.getInventory().getStack(i).isEmpty()) {
                    personalSlots.add(i);
                }
            }
        }

        if (currentState == State.IDLE) {
            if (!activePresetName.isEmpty() && !activePreset.isEmpty()) {
                if (mc.player != null) {
                    startPos = mc.player.getBlockPos();
                    currentState = State.SCAN;
                    ChatUtils.info("KitCreator enabled with preset '" + activePresetName + "'. Starting scan!");
                }
            } else {
                ChatUtils.info("Use 'kitcreator set <name>' to choose a preset and start!");
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        }
        if (BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!isActive() || !silentScreen.get()) return;
        if (currentState == State.SCAN || currentState == State.IDLE) return;
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

    public void resetMemory() {
        shulkerCache.clear();
        actionQueue.clear();
        personalSlots.clear();
        startPos = null;
        emptyShulkersContainer = null;
        targetShulkerPos = null;
        activeBlockPos = null;
        scanIdx = 0;
        guiTimeout = 0;
        presetIndex = 0;
        fetchIndex = 0;
        fetchedForCurrentPresetItem = 0;
        currentFetchInvSlot = -1;
        storedForCurrentPresetItem = 0;
        currentStoreInvSlot = -1;
        currentState = State.IDLE;

        if (mc.player != null) {
            for (int i = 0; i < 36; i++) {
                if (!mc.player.getInventory().getStack(i).isEmpty()) personalSlots.add(i);
            }
        }
        if (debug.get()) ChatUtils.info("Memory wiped...");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (activeBlockPos != null && !isWalkingState(currentState)) {
            if (AutomationUtils.getDistanceToBlock(activeBlockPos) > range.get()) {
                if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                    actionQueue.clear();
                    guiTimeout = 0;

                    if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                        if (timer <= 0) {
                            if (currentState == State.STORE_RESOURCES) {
                                returnCursorToInventory();
                            } else {
                                returnCursorToContainer();
                            }
                            timer = actionDelay.get();
                        } else {
                            timer--;
                        }
                        return;
                    }
                    mc.player.closeHandledScreen();
                }
                activeBlockPos = null;
                return;
            }
        }

        if (AutomationUtils.isInventoryFull(personalSlots) && currentState == State.FETCH_RESOURCES) {
            boolean holdingItem = mc.player.currentScreenHandler != null && !mc.player.currentScreenHandler.getCursorStack().isEmpty();
            if (actionQueue.isEmpty() && !holdingItem) {
                if (debug.get()) ChatUtils.error("Inventory is full! Disabling.");
                if (isActive()) toggle();
                return;
            }
        }

        if (currentState == State.BREAK_FILLED || currentState == State.WAIT_FOR_FILLED_BREAK) {
            if (targetShulkerPos != null && AutomationUtils.getDistanceToBlock(targetShulkerPos) > range.get()) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
                return;
            }
        }

        if (AutomationUtils.isPaused()) return;
        boolean isGuiOpen = mc.player.currentScreenHandler != mc.player.playerScreenHandler;

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

        switch (currentState) {
            case SCAN -> doScan();
            case INDEXING -> doIndexing();
            case FETCH_EMPTY_SHULKER -> fetchEmptyShulker(isGuiOpen);
            case PLACE_SHULKER -> placeShulker();
            case FETCH_RESOURCES -> fetchResources(isGuiOpen);
            case STORE_RESOURCES -> storeResources(isGuiOpen);
            case BREAK_FILLED -> breakFilledShulker();
            case WAIT_FOR_FILLED_BREAK -> waitForFilledBreak();
            case PICKUP_FILLED_SHULKER -> pickupFilledShulker();
            case WALK_TO_CHEST -> walkTo(emptyShulkersContainer, State.STORE_FILLED_SHULKER, 2.0);
            case STORE_FILLED_SHULKER -> storeFilledShulker(isGuiOpen);
            case WALK_BACK -> {
                walkTo(startPos, State.FETCH_EMPTY_SHULKER, 1.0);
                if (currentState == State.FETCH_EMPTY_SHULKER) {
                    presetIndex = 0;
                    fetchIndex = 0;
                    fetchedForCurrentPresetItem = 0;
                    currentFetchInvSlot = -1;
                    storedForCurrentPresetItem = 0;
                    currentStoreInvSlot = -1;
                }
            }
        }
    }

    private void returnCursorToContainer() {
        if (mc.player == null || mc.player.currentScreenHandler == null) return;
        ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
        if (cursor.isEmpty()) return;

        int containerSize = mc.player.currentScreenHandler.slots.size() - 36;
        if (containerSize <= 0) return;

        for (int i = 0; i < containerSize; i++) {
            ItemStack slotStack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (isSameItem(slotStack, Registries.ITEM.getId(cursor.getItem()).toString()) &&
                slotStack.getMaxCount() - slotStack.getCount() >= cursor.getCount()) {
                AutomationUtils.pickup(i, 0, actionType.get());
                return;
            }
        }

        for (int i = 0; i < containerSize; i++) {
            if (mc.player.currentScreenHandler.getSlot(i).getStack().isEmpty()) {
                AutomationUtils.pickup(i, 0, actionType.get());
                return;
            }
        }
    }

    private void returnCursorToInventory() {
        if (mc.player == null || mc.player.currentScreenHandler == null) return;
        ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
        if (cursor.isEmpty()) return;

        int containerSize = mc.player.currentScreenHandler.slots.size() - 36;
        if (containerSize <= 0) return;

        for (int i = containerSize; i < mc.player.currentScreenHandler.slots.size(); i++) {
            int invSlot = i - containerSize;
            int actualInvSlot = (invSlot < 27) ? invSlot + 9 : invSlot - 27;

            if (!personalSlots.contains(actualInvSlot)) {
                if (mc.player.currentScreenHandler.getSlot(i).getStack().isEmpty()) {
                    AutomationUtils.pickup(i, 0, actionType.get());
                    return;
                }
            }
        }

        for (int i = containerSize; i < mc.player.currentScreenHandler.slots.size(); i++) {
            if (mc.player.currentScreenHandler.getSlot(i).getStack().isEmpty()) {
                AutomationUtils.pickup(i, 0, actionType.get());
                return;
            }
        }
    }

    private void updateCacheFromScreen(BlockPos pos) {
        if (mc.player == null || mc.player.currentScreenHandler == mc.player.playerScreenHandler) return;
        int size = mc.player.currentScreenHandler.slots.size() - 36;
        if (size <= 0) return;
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            items.add(mc.player.currentScreenHandler.getSlot(i).getStack().copy());
        }
        shulkerCache.put(pos, items);
    }

    private void doScan() {
        if (debug.get()) ChatUtils.info("Scanning for containers...");
        containerList.clear();
        containerList.addAll(AutomationUtils.findContainers(range.get()));
        scanIdx = 0;
        currentState = State.INDEXING;
    }

    private void doIndexing() {
        if (scanIdx >= containerList.size()) {
            if (debug.get()) ChatUtils.info("Indexing finished.");
            if (emptyShulkersContainer == null) {
                ChatUtils.info("No container with empty shulkers found! Finishing up.");
                resetMemory();
                toggle();
                return;
            }
            currentState = State.FETCH_EMPTY_SHULKER;
            return;
        }

        BlockPos pos = containerList.get(scanIdx);

        if (AutomationUtils.getDistanceToBlock(pos) > range.get()) {
            activeBlockPos = null;
            return;
        }

        activeBlockPos = pos;
        boolean isOpen = mc.player.currentScreenHandler != mc.player.playerScreenHandler;
        if (!isOpen) {
            AutomationUtils.open(pos, rotate.get());
        } else {
            List<ItemStack> items = new ArrayList<>();
            int size = mc.player.currentScreenHandler.slots.size() - 36;
            boolean hasEmptyShulkers = false;

            for (int i = 0; i < size; i++) {
                ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack().copy();
                items.add(stack);
                if (!stack.isEmpty() && Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock && !hasItemsInside(stack)) {
                    hasEmptyShulkers = true;
                }
            }

            shulkerCache.put(pos, items);
            if (hasEmptyShulkers && emptyShulkersContainer == null) {
                emptyShulkersContainer = pos;
            }

            scanIdx++;
            mc.player.closeHandledScreen();
            activeBlockPos = null;
        }
        timer = actionDelay.get();
    }

    private void fetchEmptyShulker(boolean isGuiOpen) {
        if (AutomationUtils.getDistanceToBlock(emptyShulkersContainer) > range.get()) {
            activeBlockPos = null;
            return;
        }

        activeBlockPos = emptyShulkersContainer;
        if (!isGuiOpen) {
            AutomationUtils.open(emptyShulkersContainer, rotate.get());
            timer = actionDelay.get();
            return;
        }

        List<ItemStack> items = shulkerCache.get(emptyShulkersContainer);
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock && !hasItemsInside(stack)) {
                int finalI = i;
                actionQueue.add(() -> {
                    AutomationUtils.quickMove(finalI, actionType.get());
                    items.set(finalI, ItemStack.EMPTY);
                });
                actionQueue.add(() -> {
                    mc.player.closeHandledScreen();
                    activeBlockPos = null;
                    currentState = State.PLACE_SHULKER;
                });
                return;
            }
        }

        ChatUtils.info("All items processed. Disabling...");
        resetMemory();
        toggle();
    }

    private void placeShulker() {
        FindItemResult emptyShulkerResult = InvUtils.findInHotbar(itemStack ->
            Block.getBlockFromItem(itemStack.getItem()) instanceof ShulkerBoxBlock && !hasItemsInside(itemStack));

        if (!emptyShulkerResult.found()) {
            emptyShulkerResult = InvUtils.find(itemStack ->
                Block.getBlockFromItem(itemStack.getItem()) instanceof ShulkerBoxBlock && !hasItemsInside(itemStack));
            if (emptyShulkerResult.found()) {
                InvUtils.move().from(emptyShulkerResult.slot()).toHotbar(mc.player.getInventory().getSelectedSlot());
                timer = actionDelay.get();
                return;
            }
        }

        if (!emptyShulkerResult.found()) {
            ChatUtils.error("No EMPTY shulker box found in inventory! Disabling.");
            toggle();
            return;
        }

        targetShulkerPos = findAirNextTo(startPos);
        if (targetShulkerPos != null && BlockUtils.place(targetShulkerPos, emptyShulkerResult, rotate.get(), 0, true, false, false)) {
            shulkerCache.remove(targetShulkerPos);

            fetchIndex = presetIndex;
            fetchedForCurrentPresetItem = 0;
            currentFetchInvSlot = -1;
            currentState = State.FETCH_RESOURCES;
            timer = actionDelay.get();
        } else {
            ChatUtils.error("Cannot place target shulker box! Ensure space around you.");
            toggle();
        }
    }

    private void fetchResources(boolean isGuiOpen) {
        if (presetIndex >= activePreset.size()) {
            currentState = State.BREAK_FILLED;
            return;
        }

        int cycleEnd = Math.min(presetIndex + stacksPerCycle.get(), activePreset.size());

        if (fetchIndex < presetIndex) {
            fetchIndex = presetIndex;
            fetchedForCurrentPresetItem = 0;
            currentFetchInvSlot = -1;
        }

        if (fetchIndex >= cycleEnd) {
            if (isGuiOpen) mc.player.closeHandledScreen();
            activeBlockPos = null;
            currentState = State.STORE_RESOURCES;
            return;
        }

        PresetItem needed = activePreset.get(fetchIndex);
        if (fetchedForCurrentPresetItem >= needed.count()) {
            fetchedForCurrentPresetItem = 0;
            currentFetchInvSlot = -1;
            fetchIndex++;
            return;
        }

        int remainingNeeded = needed.count() - fetchedForCurrentPresetItem;
        boolean requireFull = isFullStack(needed.id(), needed.count());
        int requiredSize = getMaxStackSize(needed.id());

        BlockPos foundPos = findItemInCache(needed.id(), requireFull, requiredSize);
        if (foundPos == null) {
            ChatUtils.error("Missing item: " + needed.id() + " for preset! Need " + remainingNeeded + " more.");
            toggle();
            return;
        }

        if (AutomationUtils.getDistanceToBlock(foundPos) > range.get()) {
            activeBlockPos = null;
            return;
        }

        if (!foundPos.equals(activeBlockPos) || !isGuiOpen) {
            if (isGuiOpen) mc.player.closeHandledScreen();
            activeBlockPos = foundPos;
            AutomationUtils.open(foundPos, rotate.get());
            timer = actionDelay.get();
            return;
        }

        updateCacheFromScreen(foundPos);

        int slotInContainer = findSlotInCache(foundPos, needed.id(), requireFull, requiredSize);
        if (slotInContainer != -1) {
            ItemStack sourceStack = mc.player.currentScreenHandler.getSlot(slotInContainer).getStack();
            int availableInSlot = sourceStack.getCount();
            int toTake = Math.min(remainingNeeded, availableInSlot);

            boolean fullStack = requireFull && (toTake >= requiredSize);

            if (!fullStack) {
                if (currentFetchInvSlot == -1) {
                    currentFetchInvSlot = findEmptyInventorySlot();
                }

                if (currentFetchInvSlot != -1) {
                    int targetGuiSlot = AutomationUtils.invToGui(currentFetchInvSlot, mc.player.currentScreenHandler.slots.size() - 36);

                    actionQueue.add(() -> AutomationUtils.pickup(slotInContainer, 0, actionType.get()));

                    for (int k = 0; k < toTake; k++) {
                        actionQueue.add(() -> {
                            AutomationUtils.pickup(targetGuiSlot, 1, actionType.get());
                            fetchedForCurrentPresetItem++;
                        });
                    }

                    actionQueue.add(() -> {
                        AutomationUtils.pickup(slotInContainer, 0, actionType.get());
                        updateSlotInCache(foundPos, slotInContainer, toTake);
                        if (fetchedForCurrentPresetItem >= needed.count()) {
                            fetchedForCurrentPresetItem = 0;
                            currentFetchInvSlot = -1;
                            fetchIndex++;
                        }
                    });
                } else {
                    mc.player.closeHandledScreen();
                    activeBlockPos = null;
                    currentState = State.STORE_RESOURCES;
                    return;
                }
            } else {
                int amountToMove = sourceStack.getCount();
                actionQueue.add(() -> {
                    AutomationUtils.quickMove(slotInContainer, actionType.get());
                    updateSlotInCache(foundPos, slotInContainer, amountToMove);
                    fetchedForCurrentPresetItem += amountToMove;
                    if (fetchedForCurrentPresetItem >= needed.count()) {
                        fetchedForCurrentPresetItem = 0;
                        currentFetchInvSlot = -1;
                        fetchIndex++;
                    }
                });
            }
            timer = actionDelay.get();
        } else {
            ChatUtils.error("Cache mismatch for item: " + needed.id());
            toggle();
        }
    }

    private void storeResources(boolean isGuiOpen) {
        if (AutomationUtils.getDistanceToBlock(targetShulkerPos) > range.get()) {
            activeBlockPos = null;
            return;
        }

        if (!targetShulkerPos.equals(activeBlockPos) || !isGuiOpen) {
            if (isGuiOpen) mc.player.closeHandledScreen();
            activeBlockPos = targetShulkerPos;
            AutomationUtils.open(targetShulkerPos, rotate.get());
            timer = actionDelay.get();
            return;
        }

        if (presetIndex >= fetchIndex) {
            mc.player.closeHandledScreen();
            activeBlockPos = null;
            if (presetIndex >= activePreset.size()) {
                currentState = State.BREAK_FILLED;
            } else {
                currentState = State.FETCH_RESOURCES;
            }
            return;
        }

        PresetItem needed = activePreset.get(presetIndex);

        if (storedForCurrentPresetItem >= needed.count()) {
            storedForCurrentPresetItem = 0;
            currentStoreInvSlot = -1;
            presetIndex++;
            return;
        }

        int remainingNeeded = needed.count() - storedForCurrentPresetItem;

        if (currentStoreInvSlot == -1 || mc.player.getInventory().getStack(currentStoreInvSlot).isEmpty() || !isSameItem(mc.player.getInventory().getStack(currentStoreInvSlot), needed.id())) {
            currentStoreInvSlot = -1;

            for (int slot = 0; slot < 36; slot++) {
                if (personalSlots.contains(slot)) continue;
                ItemStack stack = mc.player.getInventory().getStack(slot);
                if (isSameItem(stack, needed.id())) {
                    currentStoreInvSlot = slot;
                    break;
                }
            }

            if (currentStoreInvSlot == -1) {
                for (int slot = 0; slot < 36; slot++) {
                    if (!personalSlots.contains(slot)) continue;
                    ItemStack stack = mc.player.getInventory().getStack(slot);
                    if (isSameItem(stack, needed.id())) {
                        currentStoreInvSlot = slot;
                        break;
                    }
                }
            }
        }

        if (currentStoreInvSlot != -1) {
            int invGuiSlot = AutomationUtils.invToGui(currentStoreInvSlot, 27);
            ItemStack sourceStack = mc.player.getInventory().getStack(currentStoreInvSlot);
            int availableInSlot = sourceStack.getCount();
            int toTake = Math.min(remainingNeeded, availableInSlot);

            boolean fullStack = isFullStack(needed.id(), toTake);

            int targetContainerSlot = presetIndex % 27;

            if (!fullStack) {
                actionQueue.add(() -> AutomationUtils.pickup(invGuiSlot, 0, actionType.get()));

                for (int k = 0; k < toTake; k++) {
                    actionQueue.add(() -> {
                        if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                            actionQueue.clear();
                            return;
                        }
                        AutomationUtils.pickup(targetContainerSlot, 1, actionType.get());
                        storedForCurrentPresetItem++;
                    });
                }

                actionQueue.add(() -> {
                    if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                        AutomationUtils.pickup(invGuiSlot, 0, actionType.get());
                    }
                    if (storedForCurrentPresetItem >= needed.count()) {
                        storedForCurrentPresetItem = 0;
                        currentStoreInvSlot = -1;
                        presetIndex++;
                    } else {
                        currentStoreInvSlot = -1;
                    }
                });
            } else {
                actionQueue.add(() -> {
                    AutomationUtils.quickMove(invGuiSlot, actionType.get());
                    storedForCurrentPresetItem += toTake;
                    if (storedForCurrentPresetItem >= needed.count()) {
                        storedForCurrentPresetItem = 0;
                        currentStoreInvSlot = -1;
                        presetIndex++;
                    } else {
                        currentStoreInvSlot = -1;
                    }
                });
            }
        } else {
            if (debug.get()) ChatUtils.warning("Item missing during store: " + needed.id());
            storedForCurrentPresetItem = 0;
            currentStoreInvSlot = -1;
            presetIndex++;
        }

        timer = actionDelay.get();
    }

    private void breakFilledShulker() {
        if (targetShulkerPos == null || mc.world == null) return;
        if (mc.world.getBlockState(targetShulkerPos).isAir()) {
            currentState = State.PICKUP_FILLED_SHULKER;
            return;
        }

        currentState = State.WAIT_FOR_FILLED_BREAK;
    }

    private void waitForFilledBreak() {
        if (mc.world == null || targetShulkerPos == null) return;

        if (mc.world.getBlockState(targetShulkerPos).isAir()) {
            currentState = State.PICKUP_FILLED_SHULKER;
            return;
        }

        BlockUtils.breakBlock(targetShulkerPos, true);
    }

    private void pickupFilledShulker() {
        if (hasFilledShulkerInInv()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            activeBlockPos = null;
            currentState = State.WALK_TO_CHEST;
            return;
        }

        if (targetShulkerPos != null) {
            if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetShulkerPos));
            }
        }
    }

    private void storeFilledShulker(boolean isGuiOpen) {
        if (AutomationUtils.getDistanceToBlock(emptyShulkersContainer) > range.get()) {
            activeBlockPos = null;
            return;
        }

        if (!emptyShulkersContainer.equals(activeBlockPos) || !isGuiOpen) {
            if (isGuiOpen) mc.player.closeHandledScreen();
            activeBlockPos = emptyShulkersContainer;
            AutomationUtils.open(emptyShulkersContainer, rotate.get());
            timer = actionDelay.get();
            return;
        }

        int invSlot = -1;
        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock && hasItemsInside(stack)) {
                invSlot = i;
                break;
            }
        }

        if (invSlot != -1) {
            int guiSlot = AutomationUtils.invToGui(invSlot, mc.player.currentScreenHandler.slots.size() - 36);
            actionQueue.add(() -> AutomationUtils.quickMove(guiSlot, actionType.get()));
            actionQueue.add(() -> {
                mc.player.closeHandledScreen();
                activeBlockPos = null;
                presetIndex = 0;
                fetchIndex = 0;
                fetchedForCurrentPresetItem = 0;
                currentFetchInvSlot = -1;
                storedForCurrentPresetItem = 0;
                currentStoreInvSlot = -1;
                currentState = State.WALK_BACK;
            });
        } else {
            ChatUtils.warning("Filled shulker not found in inventory!");
            mc.player.closeHandledScreen();
            activeBlockPos = null;
            presetIndex = 0;
            fetchIndex = 0;
            fetchedForCurrentPresetItem = 0;
            currentFetchInvSlot = -1;
            storedForCurrentPresetItem = 0;
            currentStoreInvSlot = -1;
            currentState = State.WALK_BACK;
        }
        timer = actionDelay.get();
    }

    private void walkTo(BlockPos target, State nextState, double distance) {
        if (target == null) {
            currentState = nextState;
            return;
        }

        if (AutomationUtils.getDistanceToBlock(target) <= distance) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
            currentState = nextState;
            return;
        }

        if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(target));
        }
    }

    private boolean isWalkingState(State state) {
        return state == State.WALK_BACK || state == State.WALK_TO_CHEST || state == State.PICKUP_FILLED_SHULKER;
    }

    private BlockPos findAirNextTo(BlockPos start) {
        if (mc.world == null || mc.player == null) return null;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                BlockPos pos = start.add(x, 0, z);
                if (mc.world.getBlockState(pos).isReplaceable()
                    && mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down())) {
                    Box blockBox = new Box(pos);
                    if (!mc.player.getBoundingBox().intersects(blockBox)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private boolean hasItemsInside(ItemStack stack) {
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        return container != null && !container.copyFirstStack().isEmpty();
    }

    private boolean hasFilledShulkerInInv() {
        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock && hasItemsInside(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFullStack(String id, int count) {
        return count >= getMaxStackSize(id);
    }

    private int getMaxStackSize(String id) {
        try {
            Identifier identifier = id.contains(":") ? Identifier.tryParse(id) : Identifier.tryParse("minecraft:" + id);
            return Registries.ITEM.get(identifier).getMaxCount();
        } catch (Exception e) {
            return 64;
        }
    }

    private boolean isSameItem(ItemStack stack, String presetId) {
        String stackId = Registries.ITEM.getId(stack.getItem()).toString();
        String normalizedPresetId = presetId.contains(":") ? presetId : "minecraft:" + presetId;
        return stackId.equals(normalizedPresetId);
    }

    private BlockPos findItemInCache(String id, boolean requireFullStack, int requiredStackSize) {
        for (Map.Entry<BlockPos, List<ItemStack>> entry : shulkerCache.entrySet()) {
            if (entry.getKey().equals(emptyShulkersContainer) || entry.getKey().equals(targetShulkerPos)) continue;
            for (ItemStack s : entry.getValue()) {
                if (isSameItem(s, id) && s.getCount() >= 1) {
                    if (requireFullStack && s.getCount() < requiredStackSize) continue;
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private int findSlotInCache(BlockPos pos, String id, boolean requireFullStack, int requiredStackSize) {
        List<ItemStack> items = shulkerCache.get(pos);
        if (items == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (isSameItem(s, id) && s.getCount() >= 1) {
                if (requireFullStack && s.getCount() < requiredStackSize) continue;
                return i;
            }
        }
        return -1;
    }

    private int findEmptyInventorySlot() {
        for (int i = 0; i < 36; i++) {
            if (!personalSlots.contains(i) && mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private void updateSlotInCache(BlockPos pos, int slot, int amountRemoved) {
        List<ItemStack> items = shulkerCache.get(pos);
        if (items != null && slot >= 0 && slot < items.size()) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                int remaining = stack.getCount() - amountRemoved;
                if (remaining <= 0) {
                    items.set(slot, ItemStack.EMPTY);
                } else {
                    stack.setCount(remaining);
                }
            }
        }
    }

    public void startCreatingPreset(String name) {
        KitUtils.startCreatingPreset(name);
    }

    public void setActivePreset(String name) {
        List<PresetItem> loaded = KitUtils.loadPreset(name);
        if (loaded == null) return;

        resetMemory();
        activePreset.clear();
        activePreset.addAll(loaded);
        activePresetName = name;

        if (isActive() && mc.player != null) {
            startPos = mc.player.getBlockPos();
            currentState = State.SCAN;
            ChatUtils.info("Switched to preset '" + name + "'. Starting scan!");
        } else {
            ChatUtils.info("Selected preset '" + name + "'. Enable KitCreator module to start!");
        }
    }

    public void removePreset(String name) {
        if (KitUtils.removePreset(name)) {
            if (activePresetName.equals(name)) {
                activePreset.clear();
                activePresetName = "";
                if (isActive()) resetMemory();
            }
        }
    }
}
