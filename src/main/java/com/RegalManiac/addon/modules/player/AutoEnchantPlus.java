package com.RegalManiac.addon.modules.player;

import com.RegalManiac.addon.utils.automation.AutomationUtils;
import com.RegalManiac.addon.utils.automation.EnchantTreeUtils;
import com.RegalManiac.addon.utils.automation.EnchantUtils;
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
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AnvilBlock;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.util.math.BlockPos;

import java.util.*;

import static com.RegalManiac.addon.utils.automation.EnchantTreeUtils.getRepairCost;

public class AutoEnchantPlus extends Module {
    public final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final SettingGroup sgArmor = settings.createGroup("Armor");
    public final SettingGroup sgWeapons = settings.createGroup("Weapons");
    public final SettingGroup sgTools = settings.createGroup("Tools");

    // --- General Settings ---
    public final Setting<AutomationUtils.ClickMode> actionType = sgGeneral.add(new EnumSetting.Builder<AutomationUtils.ClickMode>().name("action-type").description("Which click method to use.").defaultValue(AutomationUtils.ClickMode.METEOR).build());
    public final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder().name("action-delay").description("Minimum delay between clicks in ticks.").defaultValue(1).min(1).build());
    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").description("Maximum distance to interact with containers and blocks.").defaultValue(4.0).min(1.0).sliderMax(6.0).build());
    public final Setting<Boolean> autoPlace = sgGeneral.add(new BoolSetting.Builder().name("auto-place-anvil").description("Automatically places an anvil if one is not found nearby.").defaultValue(true).build());
    public final Setting<Boolean> silentScreen = sgGeneral.add(new BoolSetting.Builder().name("silent-screen").description("Allows looking around and opening GUI while container screens are active.").defaultValue(true).build());
    public final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder().name("rotate").description("Turn to the block when interacting.").defaultValue(true).build());
    public final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder().name("debug").description("Show detailed information in chat.").defaultValue(true).build());

    public final Setting<Set<RegistryKey<Enchantment>>> helmetEnchants = sgArmor.add(new EnchantmentListSetting.Builder().name("helmet").description("Required enchants for helmets.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> chestplateEnchants = sgArmor.add(new EnchantmentListSetting.Builder().name("chestplate").description("Required enchants for chestplates.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> leggingsEnchants = sgArmor.add(new EnchantmentListSetting.Builder().name("leggings").description("Required enchants for leggings.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> bootsEnchants = sgArmor.add(new EnchantmentListSetting.Builder().name("boots").description("Required enchants for boots.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> elytraEnchants = sgArmor.add(new EnchantmentListSetting.Builder().name("elytra").description("Required enchants for elytra.").build());

    public final Setting<Set<RegistryKey<Enchantment>>> swordEnchants = sgWeapons.add(new EnchantmentListSetting.Builder().name("sword").description("Required enchants for swords.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> spearEnchants = sgWeapons.add(new EnchantmentListSetting.Builder().name("spear").description("Required enchants for spear.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> maceEnchants = sgWeapons.add(new EnchantmentListSetting.Builder().name("mace").description("Required enchants for the mace.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> bowEnchants = sgWeapons.add(new EnchantmentListSetting.Builder().name("bow").description("Required enchants for bows.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> crossbowEnchants = sgWeapons.add(new EnchantmentListSetting.Builder().name("crossbow").description("Required enchants for crossbows.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> tridentEnchants = sgWeapons.add(new EnchantmentListSetting.Builder().name("trident").description("Required enchants for tridents.").build());

    public final Setting<Set<RegistryKey<Enchantment>>> pickaxeEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("pickaxe").description("Required enchants for pickaxes.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> axeEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("axe").description("Required enchants for axes.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> shovelEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("shovel").description("Required enchants for shovels.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> hoeEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("hoe").description("Required enchants for hoes.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> fishingRodEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("fishing-rod").description("Required enchants for fishing rods.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> shearEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("shears").description("Required enchants for shears.").build());
    public final Setting<Set<RegistryKey<Enchantment>>> flintEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("flint-and-steel").description("Required enchants for flint and steel.").build());

    private enum State { SCAN, INDEXING, FETCH_ITEM, FETCH_BOOKS, ENCHANTING, STORING, WAIT_XP }
    private State currentState = State.SCAN;

    private final Map<BlockPos, List<ItemStack>> shulkerCache = new HashMap<>();
    private final Map<UUID, ItemStack> virtualResults = new HashMap<>();
    private final List<EnchantTreeUtils.Step> stepQueue = new ArrayList<>();
    private boolean treeBuilt = false;
    private final List<BlockPos> shulkerList = new ArrayList<>();
    private final Set<Integer> personalSlots = new HashSet<>();
    private final Queue<Runnable> actionQueue = new LinkedList<>();

    private BlockPos lastShulker = null;
    private BlockPos activeBlockPos = null;
    private ItemStack currentTargetItem = null;
    private int timer = 0;
    private int scanIdx = 0;
    private int neededXpLevel = -1;
    private int guiTimeout = 0;

    public AutoEnchantPlus() {
        super(Categories.Player, "auto-enchant-+", "Advanced shulker-based auto enchanting.");
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

        if (currentState == State.SCAN || currentState == State.WAIT_XP) return;

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
        stepQueue.clear();
        virtualResults.clear();
        personalSlots.clear();
        currentTargetItem = null;
        lastShulker = null;
        activeBlockPos = null;
        scanIdx = 0;
        guiTimeout = 0;
        treeBuilt = false;
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

        if (AutomationUtils.isInventoryFull(personalSlots)) {
            if (debug.get()) ChatUtils.error("Inventory is full! Disabling.");
            if (isActive()) toggle();
            return;
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

        int finishedIdx = findFinishedInInv();
        if (finishedIdx != -1 && !isGuiOpen) {
            currentState = State.STORING;
        } else if (finishedIdx == -1 && currentState == State.STORING && actionQueue.isEmpty()) {
            currentTargetItem = null;
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
            case FETCH_ITEM -> {
                if (!findAndTakeItem()) {
                    resetMemory();
                    ChatUtils.info("All items processed. Disabling...");
                    toggle();
                }
            }
            case FETCH_BOOKS -> { if (!findAndTakeBooks()) currentState = State.ENCHANTING; }
            case ENCHANTING -> doEnchanting();
            case STORING -> doStoring();
            case WAIT_XP -> { if (mc.player.experienceLevel >= neededXpLevel) currentState = State.ENCHANTING; }
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

        boolean isOpen = mc.player.currentScreenHandler instanceof net.minecraft.screen.ShulkerBoxScreenHandler
            || mc.player.currentScreenHandler instanceof net.minecraft.screen.GenericContainerScreenHandler;

        if (!isOpen) {
            AutomationUtils.open(pos, rotate.get());
        } else {
            List<ItemStack> items = new ArrayList<>();
            int size = mc.player.currentScreenHandler.slots.size() - 36;
            for (int i = 0; i < size; i++) {
                items.add(mc.player.currentScreenHandler.getSlot(i).getStack().copy());
            }
            shulkerCache.put(pos, items);
            scanIdx++;
            mc.player.closeHandledScreen();
            activeBlockPos = null;
        }
        timer = actionDelay.get();
    }

    private boolean findAndTakeItem() {
        if (findTargetInInv() != -1) {
            currentState = State.FETCH_BOOKS;
            return true;
        }

        for (var entry : shulkerCache.entrySet()) {
            List<ItemStack> items = entry.getValue();
            for (int i = 0; i < items.size(); i++) {
                ItemStack s = items.get(i);
                if (!s.isEmpty() && EnchantUtils.isEnchantable(s) && !EnchantUtils.hasUnwantedEnchants(s, this)) {
                    List<RegistryKey<Enchantment>> needed = EnchantUtils.getNeeded(s, this);
                    if (!needed.isEmpty()) {
                        for (var ench : needed) {
                            if (!isBookInCache(ench)) {
                                if (debug.get()) ChatUtils.error("Book [" + ench.getValue().getPath() + "] missing in cache! Stopping.");
                                toggle();
                                return true;
                            }
                        }

                        lastShulker = entry.getKey();
                        activeBlockPos = lastShulker;
                        AutomationUtils.open(lastShulker, rotate.get());
                        int finalI = i;
                        currentTargetItem = s.copy();

                        actionQueue.add(() -> AutomationUtils.quickMove(finalI, actionType.get()));
                        actionQueue.add(() -> {
                            items.set(finalI, ItemStack.EMPTY);
                            mc.player.closeHandledScreen();
                            activeBlockPos = null;
                        });

                        currentState = State.FETCH_BOOKS;
                        timer = actionDelay.get();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void doEnchanting() {
        if (!(mc.player.currentScreenHandler instanceof AnvilScreenHandler h)) {
            BlockPos anvil = findAnvil();
            if (anvil == null) {
                if (autoPlace.get()) placeAnvil();
                else { ChatUtils.error("No anvil nearby!"); toggle(); }
                return;
            }
            activeBlockPos = anvil;
            AutomationUtils.open(anvil, rotate.get());
            timer = actionDelay.get();
            return;
        }

        if (!treeBuilt) {
            int targetIdx = findTargetInInv();

            if (targetIdx == -1) {
                mc.player.closeHandledScreen();
                activeBlockPos = null;
                currentState = State.STORING;
                return;
            }

            ItemStack baseItem = mc.player.getInventory().getStack(targetIdx);
            List<ItemStack> books = new ArrayList<>();
            List<RegistryKey<Enchantment>> needed = EnchantUtils.getNeeded(baseItem, this);

            for (var ench : needed) {
                int bIdx = findBookInInv(ench);
                if (bIdx != -1) {
                    books.add(mc.player.getInventory().getStack(bIdx).copy());
                }
            }

            if (books.isEmpty()) {
                mc.player.closeHandledScreen();
                activeBlockPos = null;
                currentState = State.FETCH_BOOKS;
                return;
            }

            EnchantTreeUtils.Node root = EnchantTreeUtils.calculateOptimalTree(baseItem, books);
            stepQueue.clear();
            EnchantTreeUtils.buildStepList(root, stepQueue);
            virtualResults.clear();
            treeBuilt = true;
            if (debug.get()) ChatUtils.info("Tree built. Steps: " + stepQueue.size());
        }

        if (stepQueue.isEmpty()) {
            mc.player.closeHandledScreen();
            activeBlockPos = null;
            currentState = State.STORING;
            treeBuilt = false;
            return;
        }

        EnchantTreeUtils.Step current = stepQueue.getFirst();

        ItemStack leftNeeded = current.left().isLeaf() ? current.left().item : virtualResults.get(current.left().id);
        ItemStack rightNeeded = current.right().isLeaf() ? current.right().item : virtualResults.get(current.right().id);

        if (leftNeeded == null) leftNeeded = current.left().item;
        if (rightNeeded == null) rightNeeded = current.right().item;

        if (leftNeeded == null || leftNeeded.isEmpty()) {
            if (debug.get()) ChatUtils.error("Critical error: tree component is empty!");
            toggle();
            return;
        }

        ItemStack s0 = h.getSlot(0).getStack();
        ItemStack s1 = h.getSlot(1).getStack();
        ItemStack s2 = h.getSlot(2).getStack();

        if (s0.isOf(Items.ENCHANTED_BOOK) && !leftNeeded.isOf(Items.ENCHANTED_BOOK)) {
            AutomationUtils.quickMove(0, actionType.get());
            timer = actionDelay.get();
            return;
        }

        if (!EnchantUtils.isSameForAnvil(s0, leftNeeded)) {
            if (!s0.isEmpty()) {
                AutomationUtils.quickMove(0, actionType.get());
                timer = actionDelay.get();
                return;
            }
            int invIdx = findExactStack(leftNeeded);
            if (invIdx != -1) {
                guiTimeout = 0;
                transferToAnvil(invIdx);
            } else {
                guiTimeout++;
                if (guiTimeout > 40) {
                    if (debug.get()) ChatUtils.error("Ping timeout! Not found: " + leftNeeded.getItem().toString());
                    toggle();
                }
            }
            return;
        }

        if (s1.isEmpty() || !EnchantUtils.isSameForAnvil(s1, rightNeeded)) {
            if (!s1.isEmpty()) {
                AutomationUtils.quickMove(1, actionType.get());
                timer = actionDelay.get();
                return;
            }
            int invIdx = findExactStack(rightNeeded);
            if (invIdx != -1) {
                guiTimeout = 0;
                transferToAnvil(invIdx);
            } else {
                guiTimeout++;
                if (guiTimeout > 40) {
                    if (debug.get()) ChatUtils.error("Ping timeout! Not found: " + rightNeeded.getItem().toString());
                    toggle();
                }
            }
            return;
        }

        int cost = h.getLevelCost();

        if (!s2.isEmpty() && cost > 0) {
            if (cost > mc.player.experienceLevel) {
                if (debug.get()) ChatUtils.warning("Need " + cost + " levels.");
                neededXpLevel = cost;
                mc.player.closeHandledScreen();
                activeBlockPos = null;
                currentState = State.WAIT_XP;
                return;
            }
            guiTimeout = 0;
            ItemStack result = s2.copy();
            AutomationUtils.quickMove(2, actionType.get());
            updateNextStepWithResult(result);
            timer = actionDelay.get();
        } else {
            guiTimeout++;
            if (guiTimeout > 40) {
                if (debug.get()) ChatUtils.error("Server didn't provide anvil result in time!");
                toggle();
            }
        }
    }

    private int findExactStack(ItemStack target) {
        if (target == null || target.isEmpty()) return -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || stack.getItem() != target.getItem()) continue;
            if (getRepairCost(stack) != getRepairCost(target)) continue;

            if (target.isOf(Items.ENCHANTED_BOOK)) {
                if (EnchantUtils.containsAllEnchantments(stack, target)) return i;
            } else {
                if (Objects.equals(EnchantUtils.getEnchantments(stack), EnchantUtils.getEnchantments(target))) return i;
            }
        }
        return -1;
    }

    private void transferToAnvil(int invSlot) {
        int guiSlot = AutomationUtils.invToGui(invSlot, 3);
        AutomationUtils.quickMove(guiSlot, actionType.get());
        timer = actionDelay.get();
    }

    private void updateNextStepWithResult(ItemStack resultStack) {
        if (stepQueue.isEmpty() || resultStack.isEmpty()) return;

        EnchantTreeUtils.Step finishedStep = stepQueue.getFirst();
        virtualResults.put(finishedStep.resultNode().id, resultStack.copy());
        stepQueue.removeFirst();

        if (stepQueue.isEmpty()) {
            treeBuilt = false;
            virtualResults.clear();
        }
    }

    private void placeAnvil() {
        meteordevelopment.meteorclient.utils.player.FindItemResult anvil = InvUtils.findInHotbar(itemStack ->
            itemStack.getItem() instanceof net.minecraft.item.BlockItem bi && bi.getBlock() instanceof AnvilBlock
        );

        if (anvil.isHotbar()) {
            int preSlot = mc.player.getInventory().getSelectedSlot();
            BlockPos targetPos = null;
            BlockPos pPos = mc.player.getBlockPos();

            search:
            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -2; z <= 2; z++) {
                        BlockPos checkPos = pPos.add(x, y, z);
                        if (checkPos.equals(pPos) || checkPos.equals(pPos.up())) continue;
                        if (!mc.world.getBlockState(checkPos).isReplaceable()) continue;

                        BlockPos supportPos = checkPos.down();
                        net.minecraft.block.BlockState supportState = mc.world.getBlockState(supportPos);
                        net.minecraft.block.Block supportBlock = supportState.getBlock();

                        if (supportState.getCollisionShape(mc.world, supportPos).isEmpty()) continue;
                        if (supportBlock instanceof net.minecraft.block.FallingBlock) continue;
                        if (supportBlock instanceof net.minecraft.block.BlockWithEntity ||
                            supportBlock instanceof net.minecraft.block.CraftingTableBlock) continue;

                        targetPos = checkPos;
                        break search;
                    }
                }
            }

            if (targetPos != null) {
                if (BlockUtils.place(targetPos, anvil, rotate.get(), 0, true, false, false)) {
                    InvUtils.swap(preSlot, false);
                    timer = actionDelay.get();
                }
            } else {
                if (debug.get()) ChatUtils.error("No place found for anvil!");
                toggle();
            }
        } else {
            ChatUtils.error("Anvil not found in hotbar!");
            toggle();
        }
    }

    private void doStoring() {
        int idx = findFinishedInInv();
        if (idx == -1) return;

        BlockPos targetShulker = findShulkerWithSpace(lastShulker);
        if (targetShulker == null) {
            if (debug.get()) ChatUtils.warning("All containers full!");
            currentTargetItem = null;
            currentState = State.FETCH_ITEM;
            return;
        }

        boolean isOpen = mc.player.currentScreenHandler instanceof net.minecraft.screen.ShulkerBoxScreenHandler
            || mc.player.currentScreenHandler instanceof net.minecraft.screen.GenericContainerScreenHandler;

        activeBlockPos = targetShulker;

        if (!isOpen) {
            lastShulker = targetShulker;
            AutomationUtils.open(lastShulker, rotate.get());
            timer = actionDelay.get();
        } else {
            int containerSize = mc.player.currentScreenHandler.slots.size() - 36;
            int guiSlot = AutomationUtils.invToGui(idx, containerSize);

            actionQueue.add(() -> AutomationUtils.quickMove(guiSlot, actionType.get()));
            actionQueue.add(() -> {
                updateCacheFromScreen();
                mc.player.closeHandledScreen();
                activeBlockPos = null;
                timer = actionDelay.get();
            });
        }
    }

    private BlockPos findShulkerWithSpace(BlockPos preferred) {
        if (preferred != null && AutomationUtils.hasSpace(shulkerCache.get(preferred))) return preferred;
        for (BlockPos pos : shulkerCache.keySet()) {
            if (AutomationUtils.hasSpace(shulkerCache.get(pos))) return pos;
        }
        return null;
    }

    private void updateCacheFromScreen() {
        if (lastShulker == null || mc.player.currentScreenHandler == null) return;
        List<ItemStack> items = new ArrayList<>();
        int size = mc.player.currentScreenHandler.slots.size() - 36;
        for (int i = 0; i < size; i++) {
            items.add(mc.player.currentScreenHandler.getSlot(i).getStack().copy());
        }
        shulkerCache.put(lastShulker, items);
    }

    private int findFinishedInInv() {
        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);

            if (s.isEmpty()) continue;
            if (currentTargetItem != null && s.getItem() != currentTargetItem.getItem()) continue;

            if (EnchantUtils.isEnchantable(s) && EnchantUtils.getNeeded(s, this).isEmpty() && s.hasEnchantments()) {
                return i;
            }
        }
        return -1;
    }

    private int findTargetInInv() {
        if (currentTargetItem == null) return -1;
        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() == currentTargetItem.getItem() && !EnchantUtils.getNeeded(s, this).isEmpty() && !EnchantUtils.hasUnwantedEnchants(s, this)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isBookInCache(RegistryKey<Enchantment> e) {
        for (var items : shulkerCache.values()) {
            for (ItemStack s : items) {
                if (EnchantUtils.isSingleEnchantBook(s)) {
                    var comp = EnchantUtils.getEnchantments(s);
                    for (var entry : comp.getEnchantments()) if (entry.getKey().get().equals(e)) return true;
                }
            }
        }
        return false;
    }

    private boolean findAndTakeBooks() {
        int itemIdx = findTargetInInv();
        if (itemIdx == -1) return false;

        ItemStack targetItem = mc.player.getInventory().getStack(itemIdx);
        List<RegistryKey<Enchantment>> neededForItem = EnchantUtils.getNeeded(targetItem, this);

        Set<RegistryKey<Enchantment>> alreadyHaveInBooks = new HashSet<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (EnchantUtils.isSingleEnchantBook(s)) {
                var comp = EnchantUtils.getEnchantments(s);
                for (var entry : comp.getEnchantmentEntries()) {
                    alreadyHaveInBooks.add(entry.getKey().getKey().get());
                }
            }
        }

        for (var ench : neededForItem) {
            if (findBookInInv(ench) == -1 && !alreadyHaveInBooks.contains(ench)) {
                for (var entry : shulkerCache.entrySet()) {
                    for (int i = 0; i < entry.getValue().size(); i++) {
                        ItemStack s = entry.getValue().get(i);
                        if (EnchantUtils.isTargetBook(s, ench)) {
                            activeBlockPos = entry.getKey();
                            AutomationUtils.open(entry.getKey(), rotate.get());
                            int finalI = i;
                            actionQueue.add(() -> AutomationUtils.quickMove(finalI, actionType.get()));
                            actionQueue.add(() -> {
                                entry.getValue().set(finalI, ItemStack.EMPTY);
                                mc.player.closeHandledScreen();
                                activeBlockPos = null;
                            });
                            timer = actionDelay.get();
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private int findBookInInv(RegistryKey<Enchantment> e) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (EnchantUtils.isSingleEnchantBook(s)) {
                var comp = EnchantUtils.getEnchantments(s);
                for (var entry : comp.getEnchantmentEntries()) if (entry.getKey().getKey().get().equals(e)) return i;
            }
        }
        return -1;
    }

    private BlockPos findAnvil() {
        BlockPos p = mc.player.getBlockPos();
        int r = (int) Math.ceil(range.get());
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = p.add(x,y,z);
                    if (AutomationUtils.getDistanceToBlock(pos) <= range.get() && mc.world.getBlockState(pos).getBlock() instanceof AnvilBlock) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
