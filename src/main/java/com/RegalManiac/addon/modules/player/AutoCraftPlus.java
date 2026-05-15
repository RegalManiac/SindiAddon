package com.RegalManiac.addon.modules.player;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.context.ContextParameterMap;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class AutoCraftPlus extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInventory = settings.createGroup("Inventory Craft");
    private final SettingGroup sgTable = settings.createGroup("Table Craft");
    private final SettingGroup sgFireworks = settings.createGroup("Fireworks");

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items").description("Items to craft using the standard recipe book.").defaultValue(List.of()).filter(item -> item != Items.FIREWORK_ROCKET).build()
    );
    private final Setting<CraftMode> craftMode = sgGeneral.add(new EnumSetting.Builder<CraftMode>()
        .name("craft-mode").description("How items are picked up (Simple = 1, All = max, Enhanced = half/right-click).").defaultValue(CraftMode.Simple).build()
    );
    private final Setting<Integer> delayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ticks").description("Tick delay between crafting actions.").defaultValue(4).min(0).build()
    );
    private final Setting<Boolean> drop = sgGeneral.add(new BoolSetting.Builder()
        .name("drop").description("Automatically drops crafted items on the ground.").defaultValue(false).build()
    );
    private final Setting<Boolean> silent = sgGeneral.add(new BoolSetting.Builder()
        .name("silent").description("Crafts items without opening the GUI on your screen.").defaultValue(true).build()
    );

    // INVENTORY SETTINGS
    private final Setting<Boolean> craftInInv = sgInventory.add(new BoolSetting.Builder()
        .name("craft-in-inventory").defaultValue(true).build()
    );
    private final Setting<Boolean> invAutoOpen = sgInventory.add(new BoolSetting.Builder()
        .name("auto-open").visible(craftInInv::get).defaultValue(true).build()
    );
    private final Setting<Boolean> invAutoClose = sgInventory.add(new BoolSetting.Builder()
        .name("auto-close").visible(craftInInv::get).defaultValue(true).build()
    );

    // TABLE SETTINGS
    private final Setting<Boolean> craftInTable = sgTable.add(new BoolSetting.Builder()
        .name("craft-in-table").defaultValue(true).build()
    );
    private final Setting<Boolean> tableAutoOpen = sgTable.add(new BoolSetting.Builder()
        .name("auto-open").visible(craftInTable::get).defaultValue(true).build()
    );
    private final Setting<Boolean> tableAutoClose = sgTable.add(new BoolSetting.Builder()
        .name("auto-close").visible(craftInTable::get).defaultValue(true).build()
    );

    // FIREWORKS
    private final Setting<FireworkLevel> fwLevel = sgFireworks.add(new EnumSetting.Builder<FireworkLevel>()
        .name("firework").description("Manually crafts fireworks of the selected flight duration.").defaultValue(FireworkLevel.None).build()
    );

    private int delayLeft = 0;
    private int lastInvHash = -1;
    private boolean isCraftingSession = false;
    private boolean checkedInvThisSession = false;
    private boolean checkedTableThisSession = false;
    private boolean tableOpenedByUs = false;
    private boolean pendingSync = false;
    private int syncWaitTimer = 0;
    private boolean syncPhaseClose = false;
    private boolean botNeedsToClearCursor = false;
    private BlockPos lastPlayerPos = null;
    private CraftingScreen tableScreen;


    public AutoCraftPlus() {
        super(Categories.Player, "auto-craft-+", "Automatically crafts items.");
    }

    @Override
    public void onActivate() {
        delayLeft = 0;
        lastInvHash = -1;
        isCraftingSession = false;
        checkedInvThisSession = false;
        checkedTableThisSession = false;
        tableOpenedByUs = false;
        tableScreen = null;
        botNeedsToClearCursor = false;
        lastPlayerPos = null;
        triggerSync();
    }

    private void triggerSync() {
        pendingSync = true;
        syncPhaseClose = false;
        syncWaitTimer = 0;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        triggerSync();
    }

    private int getInvHash() {
        int hash = 0;
        if (mc.player == null) return hash;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                hash = hash * 31 + stack.getItem().hashCode() + stack.getCount();
            }
        }
        return hash;
    }

    private BlockPos findTable() {
        int r = 4;
        BlockPos p = mc.player.getBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = p.add(x, y, z);
                    if (mc.world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private void openTable(BlockPos pos) {
        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof CraftingScreen c) {
            this.tableScreen = c;
            if (this.silent.get()) {
                event.cancel();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate() || mc.interactionManager == null || mc.player == null) return;

        BlockPos currentPos = mc.player.getBlockPos();
        if (lastPlayerPos == null || !lastPlayerPos.equals(currentPos)) {
            lastPlayerPos = currentPos;
            checkedTableThisSession = false;
        }

        if (pendingSync) {
            if (!syncPhaseClose) {
                if (mc.currentScreen instanceof InventoryScreen) {
                    pendingSync = false;
                    return;
                }
                if (mc.currentScreen == null) {
                    mc.setScreen(new InventoryScreen(mc.player));
                    mc.currentScreen = null;
                    syncPhaseClose = true;
                    syncWaitTimer = 0;
                }
            } else {
                if (syncWaitTimer > 0) {
                    syncWaitTimer--;
                } else {
                    mc.player.closeHandledScreen();
                    if (mc.currentScreen instanceof InventoryScreen) {
                        mc.setScreen(null);
                    }
                    pendingSync = false;
                    syncPhaseClose = false;
                }
            }
            return;
        }

        int currentHash = getInvHash();
        if (currentHash != lastInvHash) {
            lastInvHash = currentHash;
            checkedInvThisSession = false;
            checkedTableThisSession = false;
        }

        if (delayLeft > 0) {
            delayLeft--;
            return;
        }

        boolean needsTableStrict = needsTableToCraft();
        AbstractRecipeScreenHandler handler = null;
        boolean isTable = false;
        boolean isInv = false;

        if (mc.currentScreen instanceof CraftingScreen c) {
            handler = c.getScreenHandler();
            isTable = true;
        } else if (silent.get() && tableScreen != null) {
            handler = tableScreen.getScreenHandler();
            isTable = true;
        } else if (mc.currentScreen instanceof InventoryScreen i) {
            handler = i.getScreenHandler();
            isInv = true;
        } else if (silent.get() && mc.currentScreen == null) {
            if (!needsTableStrict) {
                if (mc.player.currentScreenHandler instanceof AbstractRecipeScreenHandler h) {
                    handler = h;
                }
                isInv = true;
            }
        }

        if (handler == null || (!isTable && !isInv)) {
            if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen) && !(mc.currentScreen instanceof CraftingScreen)) {
                return;
            }

            if (hasItemsToCraft()) {
                if (!checkedInvThisSession && craftInInv.get() && !needsTableStrict) {
                    if (!silent.get() && invAutoOpen.get()) {
                        mc.setScreen(new InventoryScreen(mc.player));
                        delayLeft = delayTicks.get();
                    }
                    isCraftingSession = true;
                    return;
                } else if (!checkedTableThisSession && craftInTable.get() && tableAutoOpen.get()) {
                    BlockPos table = findTable();
                    if (table != null) {
                        openTable(table);
                        tableOpenedByUs = true;
                        delayLeft = delayTicks.get();
                        isCraftingSession = true;
                    } else {
                        checkedTableThisSession = true;
                    }
                    return;
                }
            }
            isCraftingSession = false;
            return;
        }

        if (!handler.getCursorStack().isEmpty()) {
            if (botNeedsToClearCursor) {
                if (drop.get()) {
                    click(handler.syncId, -999, 0, SlotActionType.PICKUP);
                } else {
                    int targetSlot = findMatchingOrEmptySlot(handler, handler.getCursorStack());
                    if (targetSlot != -1) {
                        click(handler.syncId, targetSlot, 0, SlotActionType.PICKUP);
                    } else {
                        click(handler.syncId, -999, 0, SlotActionType.PICKUP);
                    }
                }
                botNeedsToClearCursor = false;
                delayLeft = delayTicks.get();
            }
            return;
        } else {
            botNeedsToClearCursor = false;
        }

        boolean actionPerformed = false;

        if (!items.get().isEmpty()) {
            Object2IntMap<Item> inventoryCounts = new Object2IntOpenHashMap<>();
            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty()) {
                    inventoryCounts.put(stack.getItem(), inventoryCounts.getInt(stack.getItem()) + stack.getCount());
                }
            }
            ContextParameterMap context = SlotDisplayContexts.createParameters(mc.world);

            for (RecipeResultCollection collection : mc.player.getRecipeBook().getOrderedResults()) {
                for (RecipeDisplayEntry recipe : collection.filter(RecipeResultCollection.RecipeFilterMode.ANY)) {
                    List<ItemStack> results = recipe.display().result().getStacks(context);
                    if (results.isEmpty()) continue;

                    Item item = results.get(0).getItem();
                    if (item == Items.FIREWORK_ROCKET && fwLevel.get() != FireworkLevel.None) continue;

                    if (items.get().contains(item)) {
                        if (recipe.craftingRequirements().isEmpty()) continue;
                        List<Ingredient> ingredients = recipe.craftingRequirements().get();

                        if (hasIngredientsInInventory(ingredients, inventoryCounts)) {
                            boolean needsTable = ingredients.size() > 4;
                            if (needsTable && !isTable) continue;

                            clearGrid(handler);

                            boolean all = craftMode.get() != CraftMode.Simple;
                            mc.interactionManager.clickRecipe(handler.syncId, recipe.id(), all);

                            if (drop.get()) {
                                click(handler.syncId, 0, all ? 1 : 0, SlotActionType.THROW);
                            } else {
                                click(handler.syncId, 0, 1, SlotActionType.QUICK_MOVE);
                            }

                            mc.player.getInventory().updateItems();
                            actionPerformed = true;
                            break;
                        }
                    }
                }
                if (actionPerformed) break;
            }
        }

        if (!actionPerformed && fwLevel.get() != FireworkLevel.None) {
            actionPerformed = doFireworkCraft(handler);
        }

        if (actionPerformed) {
            delayLeft = delayTicks.get();
            isCraftingSession = true;
        } else {
            if (isInv) checkedInvThisSession = true;
            if (isTable) checkedTableThisSession = true;

            if (isCraftingSession) {
                if (isTable && tableAutoClose.get() && tableOpenedByUs) {
                    if (silent.get()) mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(handler.syncId));
                    else mc.player.closeHandledScreen();
                } else if (isInv && invAutoClose.get() && !silent.get() && mc.currentScreen instanceof InventoryScreen) {
                    mc.player.closeHandledScreen();
                }

                isCraftingSession = false;
                tableOpenedByUs = false;
                tableScreen = null;
            }
        }
    }

    private boolean doFireworkCraft(AbstractRecipeScreenHandler handler) {
        int neededPowder = switch (fwLevel.get()) {
            case Level_1 -> 1;
            case Level_2 -> 2;
            case Level_3 -> 3;
            default -> 0;
        };

        if (neededPowder == 0) return false;

        int gridStart = 1;
        int gridEnd = (handler instanceof net.minecraft.screen.PlayerScreenHandler) ? 5 : 10;

        int paperSlots = 0;
        int powderSlots = 0;
        int otherItems = 0;
        boolean allAre32 = true;

        for (int i = gridStart; i < gridEnd; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                if (stack.getItem() == Items.PAPER) paperSlots++;
                else if (stack.getItem() == Items.GUNPOWDER) powderSlots++;
                else otherItems++;

                if (stack.getCount() != 32) allAre32 = false;
            }
        }

        CraftMode mode = craftMode.get();

        if (mode == CraftMode.Enhanced) {
            if (paperSlots == 1 && powderSlots == neededPowder && otherItems == 0 && allAre32) {
                if (!handler.getSlot(0).getStack().isEmpty()) {
                    if (drop.get()) click(handler.syncId, 0, 1, SlotActionType.THROW);
                    else click(handler.syncId, 0, 1, SlotActionType.QUICK_MOVE);
                    return true;
                }
            }

            boolean isBuildingPerfectly = (otherItems == 0 && allAre32 && paperSlots <= 1 && powderSlots <= neededPowder);
            if (!isBuildingPerfectly && (paperSlots > 0 || powderSlots > 0 || otherItems > 0)) {
                clearGrid(handler);
                return true;
            }

            int missingPaper = 1 - paperSlots;
            int missingPowder = neededPowder - powderSlots;

            int invPaperEnhanced = 0;
            int invPowderEnhanced = 0;
            int searchStart = (handler instanceof net.minecraft.screen.PlayerScreenHandler) ? 9 : 10;
            for (int i = searchStart; i < handler.slots.size(); i++) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (stack.getItem() == Items.PAPER) {
                    if (stack.getCount() == 64) invPaperEnhanced += 2;
                    else if (stack.getCount() == 32) invPaperEnhanced += 1;
                } else if (stack.getItem() == Items.GUNPOWDER) {
                    if (stack.getCount() == 64) invPowderEnhanced += 2;
                    else if (stack.getCount() == 32) invPowderEnhanced += 1;
                }
            }

            if (invPaperEnhanced < missingPaper || invPowderEnhanced < missingPowder) {
                if (paperSlots > 0 || powderSlots > 0) {
                    clearGrid(handler);
                    return true;
                }
                return false;
            }

            if (missingPaper > 0) {
                int pSlot = findBestSlot(handler, Items.PAPER, mode);
                if (pSlot != -1) {
                    moveToGrid(handler, pSlot, 1, mode);
                    return true;
                }
            }

            for (int i = 0; i < neededPowder; i++) {
                int target = 2 + i;
                if (handler.getSlot(target).getStack().isEmpty()) {
                    int gSlot = findBestSlot(handler, Items.GUNPOWDER, mode);
                    if (gSlot != -1) {
                        moveToGrid(handler, gSlot, target, mode);
                        return true;
                    }
                }
            }

            return false;
        }

        boolean gridPerfect = (paperSlots == 1 && powderSlots == neededPowder && otherItems == 0);

        if (gridPerfect) {
            if (!handler.getSlot(0).getStack().isEmpty()) {
                if (drop.get()) click(handler.syncId, 0, 1, SlotActionType.THROW);
                else click(handler.syncId, 0, 1, SlotActionType.QUICK_MOVE);
                return true;
            }
        }

        if (otherItems > 0 || paperSlots > 1 || powderSlots > neededPowder) {
            clearGrid(handler);
            return true;
        }

        int paperAvailable = getTotalItem(handler, Items.PAPER);
        int powderAvailable = getTotalItem(handler, Items.GUNPOWDER);
        int paperFullStacks = getFullStacks(handler, Items.PAPER);
        int powderFullStacks = getFullStacks(handler, Items.GUNPOWDER);

        boolean canCraft = false;
        if (mode == CraftMode.All) {
            canCraft = paperFullStacks >= 1 && powderFullStacks >= neededPowder;
        } else {
            canCraft = paperAvailable >= 1 && powderAvailable >= neededPowder;
        }

        if (!canCraft) {
            if (paperSlots > 0 || powderSlots > 0) {
                clearGrid(handler);
                return true;
            }
            return false;
        }

        if (handler.getSlot(1).getStack().isEmpty()) {
            int pSlot = findBestSlot(handler, Items.PAPER, mode);
            if (pSlot != -1) {
                moveToGrid(handler, pSlot, 1, mode);
                return true;
            }
        }

        for (int i = 0; i < neededPowder; i++) {
            int target = 2 + i;
            if (handler.getSlot(target).getStack().isEmpty()) {
                int gSlot = findBestSlot(handler, Items.GUNPOWDER, mode);
                if (gSlot != -1) {
                    moveToGrid(handler, gSlot, target, mode);
                    return true;
                }
            }
        }

        return false;
    }

    private int getTotalItem(AbstractRecipeScreenHandler handler, Item item) {
        int total = 0;
        for (int i = 1; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.getItem() == item) total += stack.getCount();
        }
        return total;
    }

    private int getFullStacks(AbstractRecipeScreenHandler handler, Item item) {
        int stacks = 0;
        for (int i = 1; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.getItem() == item && stack.getCount() == stack.getMaxCount()) stacks++;
        }
        return stacks;
    }

    private int findBestSlot(AbstractRecipeScreenHandler handler, Item item, CraftMode mode) {
        int startSearch = (handler instanceof net.minecraft.screen.PlayerScreenHandler) ? 9 : 10;
        for (int i = startSearch; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.getItem() == item) {
                if (mode == CraftMode.Enhanced) {
                    if (stack.getCount() == 64 || stack.getCount() == 32) return i;
                } else if (mode == CraftMode.All) {
                    if (stack.getCount() == stack.getMaxCount()) return i;
                } else {
                    return i;
                }
            }
        }
        return -1;
    }

    private void clearGrid(AbstractRecipeScreenHandler handler) {
        int gridStart = 1;
        int gridEnd = (handler instanceof net.minecraft.screen.PlayerScreenHandler) ? 5 : 10;

        for (int i = gridStart; i < gridEnd; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                boolean canFit = false;
                for (int j = 0; j < mc.player.getInventory().getMainStacks().size(); j++) {
                    ItemStack invStack = mc.player.getInventory().getMainStacks().get(j);
                    if (invStack.isEmpty() || (ItemStack.areItemsAndComponentsEqual(invStack, stack) && invStack.getCount() + stack.getCount() <= invStack.getMaxCount())) {
                        canFit = true;
                        break;
                    }
                }

                if (canFit) {
                    click(handler.syncId, i, 0, SlotActionType.QUICK_MOVE);
                } else {
                    click(handler.syncId, i, 1, SlotActionType.THROW);
                }
            }
        }
    }

    private void moveToGrid(AbstractRecipeScreenHandler handler, int sourceSlot, int targetSlot, CraftMode mode) {
        if (mode == CraftMode.Enhanced) {
            ItemStack sourceStack = handler.getSlot(sourceSlot).getStack();
            if (sourceStack.getCount() == 64) {
                click(handler.syncId, sourceSlot, 1, SlotActionType.PICKUP);
                click(handler.syncId, targetSlot, 0, SlotActionType.PICKUP);
            } else if (sourceStack.getCount() == 32) {
                click(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP);
                click(handler.syncId, targetSlot, 0, SlotActionType.PICKUP);
            }
        } else if (mode == CraftMode.All) {
            click(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP);
            click(handler.syncId, targetSlot, 0, SlotActionType.PICKUP);
        } else {
            click(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP);
            click(handler.syncId, targetSlot, 1, SlotActionType.PICKUP);
            click(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP);
        }
    }

    private int findMatchingOrEmptySlot(AbstractRecipeScreenHandler handler, ItemStack cursorStack) {
        int startSearch = (handler instanceof net.minecraft.screen.PlayerScreenHandler) ? 9 : 10;
        int endSearch = handler.slots.size();

        int firstEmpty = -1;

        for (int i = startSearch; i < endSearch; i++) {
            ItemStack slotStack = handler.getSlot(i).getStack();

            if (slotStack.isEmpty()) {
                if (firstEmpty == -1) firstEmpty = i;
            } else if (ItemStack.areItemsAndComponentsEqual(slotStack, cursorStack)) {
                if (slotStack.getCount() + cursorStack.getCount() <= slotStack.getMaxCount()) {
                    return i;
                }
            }
        }
        return firstEmpty;
    }

    private void click(int id, int slot, int btn, SlotActionType type) {
        mc.interactionManager.clickSlot(id, slot, btn, type, mc.player);
    }

    public boolean needsTableToCraft() {
        if (mc.player == null || mc.world == null) return false;

        if (fwLevel.get() != FireworkLevel.None) {
            int neededPowder = switch (fwLevel.get()) {
                case Level_1 -> 1;
                case Level_2 -> 2;
                case Level_3 -> 3;
                default -> 0;
            };
            int paperFound = 0, powderFound = 0;
            int paperFullStacks = 0, powderFullStacks = 0;
            int paperEnhancedStacks = 0, powderEnhancedStacks = 0;

            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == Items.PAPER) {
                    paperFound += stack.getCount();
                    if (stack.getCount() == stack.getMaxCount()) paperFullStacks++;
                    if (stack.getCount() == 64) paperEnhancedStacks += 2;
                    else if (stack.getCount() == 32) paperEnhancedStacks += 1;
                } else if (stack.getItem() == Items.GUNPOWDER) {
                    powderFound += stack.getCount();
                    if (stack.getCount() == stack.getMaxCount()) powderFullStacks++;
                    if (stack.getCount() == 64) powderEnhancedStacks += 2;
                    else if (stack.getCount() == 32) powderEnhancedStacks += 1;
                }
            }

            boolean hasEnough = false;
            if (craftMode.get() == CraftMode.Enhanced) {
                hasEnough = (paperEnhancedStacks >= 1 && powderEnhancedStacks >= neededPowder);
            } else if (craftMode.get() == CraftMode.All) {
                hasEnough = (paperFullStacks >= 1 && powderFullStacks >= neededPowder);
            } else {
                hasEnough = (paperFound >= 1 && powderFound >= neededPowder);
            }

            if (hasEnough) {
                if (craftInInv.get()) return false;
            }
        }

        if (items.get().isEmpty()) return false;

        Object2IntMap<Item> inventoryCounts = new Object2IntOpenHashMap<>();
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                inventoryCounts.put(stack.getItem(), inventoryCounts.getInt(stack.getItem()) + stack.getCount());
            }
        }

        ContextParameterMap context = SlotDisplayContexts.createParameters(mc.world);
        boolean foundInv = false;
        boolean foundTable = false;

        for (RecipeResultCollection collection : mc.player.getRecipeBook().getOrderedResults()) {
            for (RecipeDisplayEntry recipe : collection.filter(RecipeResultCollection.RecipeFilterMode.ANY)) {
                List<ItemStack> results = recipe.display().result().getStacks(context);
                if (results.isEmpty()) continue;

                Item item = results.get(0).getItem();
                if (item == Items.FIREWORK_ROCKET && fwLevel.get() != FireworkLevel.None) continue;

                if (items.get().contains(item)) {
                    if (recipe.craftingRequirements().isEmpty()) continue;
                    List<Ingredient> ingredients = recipe.craftingRequirements().get();

                    if (hasIngredientsInInventory(ingredients, inventoryCounts)) {
                        if (ingredients.size() > 4) {
                            if (craftInTable.get()) foundTable = true;
                        } else {
                            if (craftInInv.get()) foundInv = true;
                        }
                    }
                }
            }
        }
        return foundTable && !foundInv;
    }

    public boolean hasItemsToCraft() {
        if (mc.player == null || mc.world == null) return false;

        if (fwLevel.get() != FireworkLevel.None) {
            int neededPowder = switch (fwLevel.get()) {
                case Level_1 -> 1;
                case Level_2 -> 2;
                case Level_3 -> 3;
                default -> 0;
            };

            int paperFound = 0, powderFound = 0;
            int paperFullStacks = 0, powderFullStacks = 0;
            int paperEnhancedStacks = 0, powderEnhancedStacks = 0;

            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == Items.PAPER) {
                    paperFound += stack.getCount();
                    if (stack.getCount() == stack.getMaxCount()) paperFullStacks++;
                    if (stack.getCount() == 64) paperEnhancedStacks += 2;
                    else if (stack.getCount() == 32) paperEnhancedStacks += 1;
                } else if (stack.getItem() == Items.GUNPOWDER) {
                    powderFound += stack.getCount();
                    if (stack.getCount() == stack.getMaxCount()) powderFullStacks++;
                    if (stack.getCount() == 64) powderEnhancedStacks += 2;
                    else if (stack.getCount() == 32) powderEnhancedStacks += 1;
                }
            }

            if (craftMode.get() == CraftMode.Enhanced) {
                if (paperEnhancedStacks >= 1 && powderEnhancedStacks >= neededPowder) return true;
            } else if (craftMode.get() == CraftMode.All) {
                if (paperFullStacks >= 1 && powderFullStacks >= neededPowder) return true;
            } else {
                if (paperFound >= 1 && powderFound >= neededPowder) return true;
            }
        }

        if (items.get().isEmpty()) return false;

        Object2IntMap<Item> inventoryCounts = new Object2IntOpenHashMap<>();
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                inventoryCounts.put(stack.getItem(), inventoryCounts.getInt(stack.getItem()) + stack.getCount());
            }
        }

        ContextParameterMap context = SlotDisplayContexts.createParameters(mc.world);

        for (RecipeResultCollection collection : mc.player.getRecipeBook().getOrderedResults()) {
            for (RecipeDisplayEntry recipe : collection.filter(RecipeResultCollection.RecipeFilterMode.ANY)) {
                List<ItemStack> results = recipe.display().result().getStacks(context);
                if (results.isEmpty()) continue;

                Item resultItem = results.get(0).getItem();
                if (resultItem == Items.FIREWORK_ROCKET && fwLevel.get() != FireworkLevel.None) continue;

                if (items.get().contains(resultItem)) {
                    if (recipe.craftingRequirements().isEmpty()) continue;
                    List<Ingredient> ingredients = recipe.craftingRequirements().get();

                    if (hasIngredientsInInventory(ingredients, inventoryCounts)) {
                        boolean needsTable = ingredients.size() > 4;
                        if (needsTable && !craftInTable.get()) {
                            continue;
                        }
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean hasIngredientsInInventory(List<Ingredient> ingredients, Object2IntMap<Item> currentInv) {
        Object2IntMap<Item> tempInv = new Object2IntOpenHashMap<>(currentInv);

        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;

            boolean ingredientFound = false;

            for (Object2IntMap.Entry<Item> entry : tempInv.object2IntEntrySet()) {
                if (entry.getIntValue() > 0) {
                    if (ingredient.test(entry.getKey().getDefaultStack())) {
                        tempInv.put(entry.getKey(), entry.getIntValue() - 1);
                        ingredientFound = true;
                        break;
                    }
                }
            }

            if (!ingredientFound) return false;
        }

        return true;
    }

    public enum FireworkLevel {
        None("None"),
        Level_1("Level 1"),
        Level_2("Level 2"),
        Level_3("Level 3");

        private final String title;
        FireworkLevel(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum CraftMode {
        Simple("Simple"),
        All("All"),
        Enhanced("Enhanced");

        private final String title;
        CraftMode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }
}
