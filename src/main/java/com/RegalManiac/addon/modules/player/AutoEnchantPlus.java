package com.RegalManiac.addon.modules.player;

import com.RegalManiac.addon.utils.EnchantTreeUtils;
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
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.stream.Collectors;

import static com.RegalManiac.addon.utils.EnchantTreeUtils.getRepairCost;

public class AutoEnchantPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgArmor = settings.createGroup("Armor");
    private final SettingGroup sgWeapons = settings.createGroup("Weapons");
    private final SettingGroup sgTools = settings.createGroup("Tools");


    // --- General Settings ---
    private final Setting<Integer> reach = sgGeneral.add(new IntSetting.Builder().name("reach-distance").description("Maximum distance to interact with shulkers and anvils.").defaultValue(4).build());
    private final Setting<Integer> containerDelay = sgGeneral.add(new IntSetting.Builder().name("container-delay").description("Delay between opening/closing containers and moving items.").defaultValue(10).min(1).build());
    private final Setting<Integer> anvilDelay = sgGeneral.add(new IntSetting.Builder().name("anvil-delay").description("Delay between individual clicks inside the anvil interface.").defaultValue(10).min(1).build());
    private final Setting<Boolean> autoPlace = sgGeneral.add(new BoolSetting.Builder().name("auto-place-anvil").description("Automatically places an anvil if one is not found nearby.").defaultValue(true).build());
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder().name("rotate").description("Поворачиваться к блоку при взаимодействии.").defaultValue(true).build());
    private final Setting<Boolean> enableSounds = sgGeneral.add(new BoolSetting.Builder().name("sound-notification").description("Plays notification sounds for events.").defaultValue(true).build());
    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder().name("debug").description("Show detailed information in chat.").defaultValue(true).build());

    private final Setting<Set<RegistryKey<Enchantment>>> helmetEnchants = sgArmor.add(new EnchantmentListSetting.Builder().name("helmet").description("Required enchants for helmets.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> chestplateEnchants = sgArmor.add(new EnchantmentListSetting.Builder().name("chestplate").description("Required enchants for chestplates.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> leggingsEnchants = sgArmor.add(new EnchantmentListSetting.Builder().name("leggings").description("Required enchants for leggings.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> bootsEnchants = sgArmor.add(new EnchantmentListSetting.Builder().name("boots").description("Required enchants for boots.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> elytraEnchants = sgArmor.add(new EnchantmentListSetting.Builder().name("elytra").description("Required enchants for elytra.").build());

    private final Setting<Set<RegistryKey<Enchantment>>> swordEnchants = sgWeapons.add(new EnchantmentListSetting.Builder().name("sword").description("Required enchants for swords.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> spearEnchants = sgWeapons.add(new EnchantmentListSetting.Builder().name("spear").description("Required enchants for spear.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> maceEnchants = sgWeapons.add(new EnchantmentListSetting.Builder().name("mace").description("Required enchants for the mace.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> bowEnchants = sgWeapons.add(new EnchantmentListSetting.Builder().name("bow").description("Required enchants for bows.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> crossbowEnchants = sgWeapons.add(new EnchantmentListSetting.Builder().name("crossbow").description("Required enchants for crossbows.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> tridentEnchants = sgWeapons.add(new EnchantmentListSetting.Builder().name("trident").description("Required enchants for tridents.").build());

    private final Setting<Set<RegistryKey<Enchantment>>> pickaxeEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("pickaxe").description("Required enchants for pickaxes.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> axeEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("axe").description("Required enchants for axes.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> shovelEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("shovel").description("Required enchants for shovels.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> hoeEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("hoe").description("Required enchants for hoes.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> fishingRodEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("fishing-rod").description("Required enchants for fishing rods.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> shearEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("shears").description("Required enchants for shears.").build());
    private final Setting<Set<RegistryKey<Enchantment>>> flintEnchants = sgTools.add(new EnchantmentListSetting.Builder().name("flint-and-steel").description("Required enchants for flint and steel.").build());

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
    private ItemStack currentTargetItem = null;
    private int timer = 0;
    private int scanIdx = 0;
    private int neededXpLevel = -1;

    public AutoEnchantPlus() {
        super(Categories.Player, "auto-enchant-+", "Advanced shulker-based auto enchanting.");
    }

    @Override
    public void onActivate() {
        actionQueue.clear();
        timer = 0;
        if (currentState == null) currentState = State.SCAN;
        personalSlots.clear();
        if (mc.player != null) {
            for (int i = 0; i < 36; i++) {
                if (!mc.player.getInventory().getStack(i).isEmpty()) personalSlots.add(i);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (isInventoryFull()) {
            if (debug.get()) ChatUtils.error("Inventory is fully packed! Stopping to prevent item drop.");notifyWarning();
            if (isActive()) toggle();
            return;
        }
        if (isPaused()) return;
        if (mc.currentScreen == null && !actionQueue.isEmpty()) {
            actionQueue.clear();
        }
        if (!actionQueue.isEmpty()) {
            if (mc.currentScreen == null) {
                timer--;
                if (timer < -40) {
                    if (debug.get()) ChatUtils.warning("Server GUI lag! Clearing queue and retrying...");notifyWarning();
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
        int finished = findFinishedInInv();
        if (finished != -1 && currentState != State.STORING && mc.currentScreen == null) {
            currentState = State.STORING;
            return;
        }

        switch (currentState) {
            case SCAN -> {
                if (debug.get()) ChatUtils.info("Scanning for shulker boxes...");
                notifyInfo();
                updateContainers();
                scanIdx = 0;
                currentState = State.INDEXING;
            }
            case INDEXING -> doIndexing();
            case FETCH_ITEM -> {if (!findAndTakeItem()) { resetMemory(); ChatUtils.info("All items processed. Disabling..."); notifyFinish(); toggle();}}
            case FETCH_BOOKS -> { if (!findAndTakeBooks()) currentState = State.ENCHANTING; }
            case ENCHANTING -> doEnchanting();
            case STORING -> doStoring();
            case WAIT_XP -> { if (mc.player.experienceLevel >= neededXpLevel) currentState = State.ENCHANTING; }
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
        currentTargetItem = null;
        lastShulker = null;
        scanIdx = 0;
        treeBuilt = false;
        currentState = State.SCAN;
        if (isActive()) {
            if (debug.get()) ChatUtils.info("Memory wiped. Rescanning...");notifyInfo();
        }
    }

    private void doIndexing() {
        if (scanIdx >= shulkerList.size()) {
            if (debug.get()) ChatUtils.info("Indexing finished. Found " + shulkerCache.size() + " shulkers.");
            notifyInfo();
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
            for (int i = 0; i < size; i++) {
                items.add(mc.player.currentScreenHandler.getSlot(i).getStack().copy());
            }
            shulkerCache.put(pos, items);
            scanIdx++;
            mc.player.closeHandledScreen();
            timer = containerDelay.get();
        }
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
                if (!s.isEmpty() && isEnchantable(s) && !hasUnwantedEnchants(s)) {
                    List<RegistryKey<Enchantment>> needed = getNeeded(s);
                    if (!needed.isEmpty()) {
                        for (var ench : needed) {
                            if (!isBookInCache(ench)) {
                                if (debug.get())ChatUtils.error("Book [" + ench.getValue().getPath() + "] missing in cache! Stopping.");
                                notifyError();
                                toggle();
                                return true;
                            }
                        }

                        lastShulker = entry.getKey();
                        open(lastShulker);
                        int finalI = i;
                        currentTargetItem = s.copy();

                        actionQueue.add(() -> mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, finalI, 0, SlotActionType.QUICK_MOVE, mc.player));
                        actionQueue.add(() -> {
                            items.set(finalI, ItemStack.EMPTY);
                            mc.player.closeHandledScreen();
                        });

                        currentState = State.FETCH_BOOKS;
                        timer = containerDelay.get();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void doEnchanting() {
        if (!(mc.currentScreen instanceof AnvilScreen)) {
            BlockPos anvil = findAnvil();
            if (anvil == null) {
                if (autoPlace.get()) placeAnvil();
                else { ChatUtils.error("No anvil nearby!"); toggle(); notifyError();}
                return;
            }
            open(anvil);
            timer = 3;
            return;
        }

        AnvilScreenHandler h = ((AnvilScreen) mc.currentScreen).getScreenHandler();

        if (!treeBuilt) {
            int targetIdx = findTargetInInv();

            if (targetIdx == -1) {
                mc.player.closeHandledScreen();
                currentState = State.STORING;
                return;
            }

            ItemStack baseItem = mc.player.getInventory().getStack(targetIdx);

            List<ItemStack> books = new ArrayList<>();

            List<RegistryKey<Enchantment>> needed = getNeeded(baseItem);
            for (var ench : needed) {
                int bIdx = findBookInInv(ench);
                if (bIdx != -1) {
                    books.add(mc.player.getInventory().getStack(bIdx).copy());
                }
            }

            if (books.isEmpty()) {
                mc.player.closeHandledScreen();
                currentState = State.FETCH_BOOKS;
                return;
            }

            EnchantTreeUtils.Node root = EnchantTreeUtils.calculateOptimalTree(baseItem, books);
            stepQueue.clear();
            EnchantTreeUtils.buildStepList(root, stepQueue);
            virtualResults.clear();
            treeBuilt = true;
            if (debug.get()) ChatUtils.info("Tree built. Steps: " + stepQueue.size());
            notifyInfo();
        }

        if (stepQueue.isEmpty()) {
            mc.player.closeHandledScreen();
            currentState = State.STORING;
            treeBuilt = false;
            return;
        }

        EnchantTreeUtils.Step current = stepQueue.get(0);

        ItemStack leftNeeded = current.left().isLeaf() ? current.left().item : virtualResults.get(current.left().id);
        ItemStack rightNeeded = current.right().isLeaf() ? current.right().item : virtualResults.get(current.right().id);

        if (leftNeeded == null) leftNeeded = current.left().item;
        if (rightNeeded == null) rightNeeded = current.right().item;

        if (leftNeeded == null || leftNeeded.isEmpty()) {
            if (debug.get())ChatUtils.error("Critical error: tree component is empty!");
            notifyError();
            toggle();
            return;
        }

        ItemStack s0 = h.getSlot(0).getStack();
        ItemStack s1 = h.getSlot(1).getStack();
        ItemStack s2 = h.getSlot(2).getStack();

        if (s0.isOf(Items.ENCHANTED_BOOK) && !leftNeeded.isOf(Items.ENCHANTED_BOOK)) {
            mc.interactionManager.clickSlot(h.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
            timer = anvilDelay.get();
            return;
        }

        if (!isSameForAnvil(s0, leftNeeded)) {
            if (!s0.isEmpty()) {
                mc.interactionManager.clickSlot(h.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
                timer = anvilDelay.get();
                return;
            }
            int invIdx = findExactStack(leftNeeded);
            if (invIdx != -1) transferToAnvil(h.syncId, invIdx, 0);
            else {
                if (debug.get())ChatUtils.error("Not found: " + leftNeeded.getItem().toString() + " RC: " + getRepairCost(leftNeeded));
                notifyError();
                toggle();
            }
            return;
        }

        if (s1.isEmpty() || !isSameForAnvil(s1, rightNeeded)) {
            if (!s1.isEmpty()) {
                mc.interactionManager.clickSlot(h.syncId, 1, 0, SlotActionType.QUICK_MOVE, mc.player);
                timer = anvilDelay.get();
                return;
            }
            int invIdx = findExactStack(rightNeeded);
            if (invIdx != -1) transferToAnvil(h.syncId, invIdx, 1);
            else {
                if (debug.get())ChatUtils.error("Not found: " + rightNeeded.getItem().toString() + " RC: " + getRepairCost(rightNeeded));
                notifyError();
                toggle();
            }
            return;
        }

        int cost = h.getLevelCost();
        if (!s2.isEmpty() && cost > 0) {
            if (cost > mc.player.experienceLevel) {
                if (debug.get())ChatUtils.warning("Need " + cost + " levels.");
                notifyWarning();
                neededXpLevel = cost;
                mc.player.closeHandledScreen();
                currentState = State.WAIT_XP;
                return;
            }

            ItemStack result = s2.copy();
            mc.interactionManager.clickSlot(h.syncId, 2, 0, SlotActionType.QUICK_MOVE, mc.player);

            updateNextStepWithResult(result);

            timer = anvilDelay.get();
        }
    }

    private int findExactStack(ItemStack target) {
        if (target == null || target.isEmpty()) return -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || stack.getItem() != target.getItem()) continue;

            if (getRepairCost(stack) != getRepairCost(target)) continue;

            if (target.isOf(Items.ENCHANTED_BOOK)) {
                if (containsAllEnchantments(stack, target)) return i;
            } else {
                if (Objects.equals(getEnchantments(stack), getEnchantments(target))) return i;
            }
        }

        if (debug.get()) ChatUtils.warning("Could not find in inventory: " + target.getItem().toString() + " | RC: " + getRepairCost(target));
        return -1;
    }

    private boolean containsAllEnchantments(ItemStack stack, ItemStack target) {
        ItemEnchantmentsComponent stackEnchs = getEnchantments(stack);
        ItemEnchantmentsComponent targetEnchs = getEnchantments(target);

        for (var entry : targetEnchs.getEnchantmentEntries()) {
            if (stackEnchs.getLevel(entry.getKey()) < entry.getIntValue()) return false;
        }
        return true;
    }

    private ItemEnchantmentsComponent getEnchantments(ItemStack stack) {
        if (stack.isOf(Items.ENCHANTED_BOOK)) {
            return stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        }
        return stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
    }

    private boolean isSameForAnvil(ItemStack anvilStack, ItemStack neededStack) {
        if (anvilStack.isEmpty() || neededStack == null) return false;
        if (anvilStack.getItem() != neededStack.getItem()) return false;

        int anvilRC = EnchantTreeUtils.getRepairCost(anvilStack);
        int neededRC = EnchantTreeUtils.getRepairCost(neededStack);

        if (anvilRC != neededRC) return false;

        if (neededStack.isOf(Items.ENCHANTED_BOOK)) {
            return containsAllEnchantments(anvilStack, neededStack);
        }

        return Objects.equals(getEnchantments(anvilStack), getEnchantments(neededStack));
    }

    private void transferToAnvil(int syncId, int invSlot, int anvilSlot) {
        int guiSlot = invToGui(invSlot, 3);
        mc.interactionManager.clickSlot(syncId, guiSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, anvilSlot, 0, SlotActionType.PICKUP, mc.player);
        timer = anvilDelay.get();
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
                    timer = containerDelay.get();
                }
            } else {
                if (debug.get())ChatUtils.error("No place found for anvil!");
                notifyError();
                toggle();
            }
        } else {
            ChatUtils.error("Anvil not found in hotbar!");
            notifyWarning();
            toggle();
        }
    }

    private void doStoring() {
        int idx = findFinishedInInv();

        if (idx == -1) {
            currentTargetItem = null;
            currentState = State.FETCH_ITEM;
            return;
        }

        BlockPos targetShulker = findShulkerWithSpace(lastShulker);

        if (targetShulker == null) {
            if (debug.get()) ChatUtils.warning("All containers full! Leaving item in inventory.");
            notifyWarning();
            currentTargetItem = null;
            currentState = State.FETCH_ITEM;
            return;
        }

        if (!(mc.currentScreen instanceof ShulkerBoxScreen || mc.currentScreen instanceof GenericContainerScreen)) {
            lastShulker = targetShulker;
            open(lastShulker);
            timer = 3;
        } else {
            int containerSize = mc.player.currentScreenHandler.slots.size() - 36;
            int syncId = mc.player.currentScreenHandler.syncId;
            int guiSlot = invToGui(idx, containerSize);
            actionQueue.add(() -> mc.interactionManager.clickSlot(syncId, guiSlot, 0, SlotActionType.QUICK_MOVE, mc.player));

            actionQueue.add(() -> {
                updateCacheFromScreen();
                mc.player.closeHandledScreen();
                currentTargetItem = null;
                timer = containerDelay.get();
            });

            currentState = State.FETCH_ITEM;
        }
    }

    private BlockPos findShulkerWithSpace(BlockPos preferred) {
        if (preferred != null && hasSpace(preferred)) return preferred;
        for (BlockPos pos : shulkerCache.keySet()) {
            if (hasSpace(pos)) return pos;
        }
        return null;
    }

    private boolean hasSpace(BlockPos pos) {
        List<ItemStack> items = shulkerCache.get(pos);
        if (items == null) return false;
        for (ItemStack s : items) {
            if (s.isEmpty()) return true;
        }
        return false;
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

    // --- Search Helpers ---

    private int findFinishedInInv() {
        if (currentTargetItem == null) return -1;

        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);

            if (!s.isEmpty() && s.getItem() == currentTargetItem.getItem() && getNeeded(s).isEmpty()) return i;
        }
        return -1;
    }

    private int findTargetInInv() {
        if (currentTargetItem == null) return -1;
        for (int i = 0; i < 36; i++) {
            if (personalSlots.contains(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (currentTargetItem != null && (!s.isEmpty() && s.getItem() != currentTargetItem.getItem())) continue;

            if (!s.isEmpty() && s.getItem() == currentTargetItem.getItem() && !getNeeded(s).isEmpty() && !hasUnwantedEnchants(s)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSingleEnchantBook(ItemStack s) {
        if (!s.isOf(Items.ENCHANTED_BOOK)) return false;
        return getEnchantments(s).getEnchantments().size() == 1;
    }

    private boolean isInventoryFull() {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private boolean hasUnwantedEnchants(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var currentEnchants = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT)
            .getEnchantments().stream().map(e -> e.getKey().get()).collect(Collectors.toSet());

        if (currentEnchants.isEmpty()) return false;

        Set<RegistryKey<Enchantment>> wantedEnchants = getTargetEnchants(stack);

        for (RegistryKey<Enchantment> current : currentEnchants) {
            if (!wantedEnchants.contains(current)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEnchantable(ItemStack s) {
        String n = s.getItem().toString().toLowerCase();
        boolean isHighTier = n.contains("diamond") || n.contains("netherite") ||
            n.contains("elytra") || n.contains("bow") || n.contains("crossbow") ||
            n.contains("trident") || n.contains("mace") || n.contains("shears") ||
            n.contains("fishing-rod") || n.contains("flint-and-steel");

        if (!isHighTier) return false;
        return n.contains("helmet") || n.contains("chestplate") || n.contains("leggings") || n.contains("boots") ||
            n.contains("sword") || n.contains("spear") || n.contains("mace") || n.contains("pickaxe") || n.contains("axe") ||
            n.contains("shovel") || n.contains("bow") || n.contains("trident") || n.contains("elytra") ||
            n.contains("crossbow") || n.contains("hoe") || n.contains("fishing-rod") ||
            n.contains("shears") || n.contains("flint-and-steel");
    }

    private List<RegistryKey<Enchantment>> getNeeded(ItemStack stack) {
        if (stack.isEmpty()) return Collections.emptyList();
        var current = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT)
            .getEnchantments().stream().map(e -> e.getKey().get()).collect(Collectors.toSet());

        Set<RegistryKey<Enchantment>> wanted = getTargetEnchants(stack);

        return wanted.stream().filter(e -> !current.contains(e)).collect(Collectors.toList());
    }

    private Set<RegistryKey<Enchantment>> getTargetEnchants(ItemStack stack) {
        if (stack.isEmpty()) return Collections.emptySet();
        String n = stack.getItem().toString().toLowerCase();

        if (n.contains("helmet")) return helmetEnchants.get();
        if (n.contains("chestplate")) return chestplateEnchants.get();
        if (n.contains("leggings")) return leggingsEnchants.get();
        if (n.contains("boots")) return bootsEnchants.get();
        if (n.contains("elytra")) return elytraEnchants.get();
        if (n.contains("sword")) return swordEnchants.get();
        if (n.contains("spear")) return spearEnchants.get();
        if (n.contains("mace")) return maceEnchants.get();
        if (n.contains("trident")) return tridentEnchants.get();
        if (n.contains("bow")) return bowEnchants.get();
        if (n.contains("crossbow")) return crossbowEnchants.get();
        if (n.contains("pickaxe")) return pickaxeEnchants.get();
        if (n.contains("shovel")) return shovelEnchants.get();
        if (n.contains("axe")) return axeEnchants.get();
        if (n.contains("hoe")) return hoeEnchants.get();
        if (n.contains("fishing-rod")) return fishingRodEnchants.get();
        if (n.contains("shears")) return shearEnchants.get();
        if (n.contains("flint-and-steel")) return flintEnchants.get();

        return Collections.emptySet();
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

    private boolean isBookInCache(RegistryKey<Enchantment> e) {
        for (var items : shulkerCache.values()) {
            for (ItemStack s : items) {
                if (isSingleEnchantBook(s)) {
                    var comp = getEnchantments(s);
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
        List<RegistryKey<Enchantment>> neededForItem = getNeeded(targetItem);

        Set<RegistryKey<Enchantment>> alreadyHaveInBooks = new HashSet<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isSingleEnchantBook(s)) {
                var comp = getEnchantments(s);
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
                        if (isTargetBook(s, ench)) {
                            open(entry.getKey());
                            int finalI = i;
                            actionQueue.add(() -> mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, finalI, 0, SlotActionType.QUICK_MOVE, mc.player));
                            actionQueue.add(() -> { entry.getValue().set(finalI, ItemStack.EMPTY); mc.player.closeHandledScreen(); });
                            timer = containerDelay.get();
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isTargetBook(ItemStack s, RegistryKey<Enchantment> ench) {
        if (!isSingleEnchantBook(s)) return false;
        var comp = getEnchantments(s);
        for (var entry : comp.getEnchantmentEntries()) {
            if (entry.getKey().getKey().get().equals(ench)) return true;
        }
        return false;
    }

    private int findBookInInv(RegistryKey<Enchantment> e) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isSingleEnchantBook(s)) {
                var comp = getEnchantments(s);
                for (var entry : comp.getEnchantmentEntries()) if (entry.getKey().getKey().get().equals(e)) return i;
            }
        }
        return -1;
    }

    private int invToGui(int i, int offset) { return (i < 9) ? i + offset + 27 : i + offset - 9; }

    private void open(BlockPos p) {
        if (p == null) return;
        faceBlock(p);
        BlockHitResult hitResult = new BlockHitResult(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), Direction.UP, p, true);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private BlockPos findAnvil() {
        for (int x = -4; x <= 4; x++) for (int y = -4; y <= 4; y++) for (int z = -4; z <= 4; z++) {
            BlockPos p = mc.player.getBlockPos().add(x,y,z);
            if (mc.world.getBlockState(p).getBlock() instanceof AnvilBlock) return p;
        }
        return null;
    }

    private void faceBlock(BlockPos pos) {
        if (!rotate.get()) return;

        if (pos != null) {
            double yaw = Rotations.getYaw(pos);
            double pitch = Rotations.getPitch(pos);
            Rotations.rotate(yaw, pitch);
        }
    }

    private boolean isPaused() {

        return mc.currentScreen != null &&
            !(mc.currentScreen instanceof ShulkerBoxScreen) &&
            !(mc.currentScreen instanceof GenericContainerScreen) &&
            !(mc.currentScreen instanceof AnvilScreen);
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
