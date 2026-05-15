package com.RegalManiac.addon.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AutoTradePlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final ItemListSetting tradeItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("buy-items")
        .description("Items that the module will attempt to buy (e.g. Enchanted Books).")
        .defaultValue(Items.ENCHANTED_BOOK)
        .build()
    );

    private final EnchantmentListSetting enchantmentsList = sgGeneral.add(new EnchantmentListSetting.Builder()
        .name("enchantments")
        .description("List of enchantments to look for on enchanted books.")
        .build()
    );

    private final Setting<List<Item>> sellItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("sell-items")
        .description("Items allowed to be sold to villagers (e.g. Sticks, Flesh).")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Boolean> onlyMaxLevel = sgGeneral.add(new BoolSetting.Builder()
        .name("only-max-level")
        .description("Only buy books if the enchantment is at its maximum level.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Automatically faces the villager when interacting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open")
        .description("Automatically open the trade menu when a villager is nearby.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoClose = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-close")
        .description("Automatically close the trade menu when finished or out of resources.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Automatically disable the module when trading resources are depleted.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance to the villager for interaction.")
        .defaultValue(4.5)
        .min(1)
        .max(6)
        .build()
    );

    private final Setting<Integer> slotDelay = sgGeneral.add(new IntSetting.Builder()
        .name("trade-delay")
        .description("The delay in ticks between clicking slots.")
        .defaultValue(3)
        .min(1)
        .build()
    );

    private final Setting<Integer> openDelay = sgGeneral.add(new IntSetting.Builder()
        .name("open-delay")
        .description("Tick delay between opening villager trading menus.")
        .defaultValue(10)
        .min(0)
        .build()
    );

    private final Setting<Integer> closeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("close-delay")
        .description("Tick delay before automatically closing the trading menu.")
        .defaultValue(10)
        .min(0)
        .build()
    );

    private final Setting<Boolean> linuxFix = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-close-for-stuped-linux")
        .description("Forcefully closes the trade window after a set time to bypass Linux bugs.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> linuxFixDelay = sgGeneral.add(new IntSetting.Builder()
        .name("linux-close-delay")
        .description("Ticks to wait before force-closing (20 ticks = 1 sec).")
        .defaultValue(60)
        .min(1)
        .visible(linuxFix::get)
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Sends chat messages about module actions.")
        .defaultValue(true)
        .build()
    );

    private int timer;
    private int openTimer;
    private int closeTimer;
    private int linuxForceCloseTimer;
    private final Set<UUID> interactedVillagers = new HashSet<>();

    public AutoTradePlus() {
        super(Categories.Player, "auto-trade-+", "Automated villager trading system.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        linuxForceCloseTimer = 0;
        interactedVillagers.clear();
    }

    private void debugInfo(String message) {
        if (debug.get()) {
            info(message);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        AutoCraftPlus autoCraft = Modules.get().get(AutoCraftPlus.class);
        if (autoCraft != null && autoCraft.isActive() && autoCraft.hasItemsToCraft()) {
            if (mc.player.currentScreenHandler instanceof MerchantScreenHandler) {
                debugInfo("Pausing AutoTrade: AutoCraftPlus needs to craft.");
                closeMerchantScreen();
            }
            return;
        }

        if (openTimer > 0) openTimer--;

        if (!(mc.player.currentScreenHandler instanceof MerchantScreenHandler handler)) {
            linuxForceCloseTimer = 0;
            if (autoOpen.get() && mc.currentScreen == null && openTimer <= 0) {
                findAndOpenVillager();
            }
            closeTimer = closeDelay.get();
            return;
        }

        openTimer = openDelay.get();

        if (linuxFix.get()) {
            linuxForceCloseTimer++;
            if (linuxForceCloseTimer >= linuxFixDelay.get()) {
                debugInfo("Linux Fix: Time limit reached! Force closing.");
                closeMerchantScreen();
                openTimer = openDelay.get();
                return;
            }
        }

        VillagerEntity tradingVillager = mc.world.getEntitiesByClass(VillagerEntity.class,
                mc.player.getBoundingBox().expand(15.0),
                v -> v.getCustomer() == mc.player)
            .stream().findFirst().orElse(null);

        if (tradingVillager != null) {
            if (rotate.get()) {
                Rotations.rotate(Rotations.getYaw(tradingVillager), Rotations.getPitch(tradingVillager), 10, null);
            }

            if (!mc.player.isInRange(tradingVillager, range.get())) {
                debugInfo("Villager is out of range! Force closing trade.");
                closeMerchantScreen();
                return;
            }
        }

        if (timer > 0) {
            timer--;
            return;
        }

        TradeOfferList offers = handler.getRecipes();
        if (offers == null || offers.isEmpty()) {
            if (autoClose.get()) {
                if (closeTimer > 0) {
                    closeTimer--;
                } else {
                    closeMerchantScreen();
                    openTimer = openDelay.get();
                }
            }
            return;
        }

        ItemStack outputSlot = handler.getSlot(2).getStack();

        if (!outputSlot.isEmpty()) {
            boolean isEmerald = outputSlot.getItem() == Items.EMERALD;
            boolean isWantedItem = isDesiredItem(outputSlot);

            if (isEmerald || isWantedItem) {
                if (!hasInventorySpace()) {
                    debugInfo("Inventory full! Stopping trade.");
                    if (autoClose.get()) closeMerchantScreen();
                    if (autoDisable.get()) this.toggle();
                    return;
                }

                handleOutputSlot(handler, outputSlot);
                timer = slotDelay.get();
                closeTimer = closeDelay.get();
                return;
            }
        }

        TradeOffer bestBuyOffer = null;
        int bestBuyIndex = -1;
        TradeOffer bestSellOffer = null;
        int bestSellIndex = -1;

        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            if (offer.isDisabled()) continue;
            if (!canAffordFromInventory(offer, handler)) continue;

            ItemStack result = offer.getSellItem();

            if (isDesiredItem(result)) {
                bestBuyOffer = offer;
                bestBuyIndex = i;
                break;
            }

            if (result.getItem() == Items.EMERALD) {
                Item inputItem = offer.getFirstBuyItem().itemStack().getItem();
                if (sellItems.get().contains(inputItem)) {
                    if (bestSellOffer == null) {
                        bestSellOffer = offer;
                        bestSellIndex = i;
                    }
                }
            }
        }

        if (bestBuyOffer != null) {
            performTrade(handler, bestBuyOffer, bestBuyIndex);
            closeTimer = closeDelay.get();
        }
        else if (bestSellOffer != null) {
            performTrade(handler, bestSellOffer, bestSellIndex);
            closeTimer = closeDelay.get();
        }
        else {
            if (autoClose.get()) {
                if (closeTimer > 0) {
                    closeTimer--;
                    return;
                }

                boolean criticalStop = false;

                if (!hasInventorySpace()) {
                    debugInfo("Inventory full (less than 3 slots)! Disabling.");
                    criticalStop = true;
                } else {
                    boolean hasAnyDesiredTrade = false;
                    boolean canAffordAtLeastOne = false;

                    for (TradeOffer offer : offers) {
                        if (offer.isDisabled()) continue;

                        ItemStack result = offer.getSellItem();
                        boolean isDesiredBuy = isDesiredItem(result);
                        boolean isDesiredSell = (result.getItem() == Items.EMERALD && sellItems.get().contains(offer.getFirstBuyItem().itemStack().getItem()));

                        if (isDesiredBuy || isDesiredSell) {
                            hasAnyDesiredTrade = true;
                            if (canAffordFromInventory(offer, handler)) {
                                canAffordAtLeastOne = true;
                                break;
                            }
                        }
                    }

                    if (hasAnyDesiredTrade && !canAffordAtLeastOne) {
                        debugInfo("Not enough resources for any of the available desired trades! Disabling.");
                        criticalStop = true;
                    } else if (!hasAnyDesiredTrade) {
                        debugInfo("No desired trades found on this villager.");
                    }
                }

                closeMerchantScreen();

                if (autoDisable.get() && criticalStop) {
                    this.toggle();
                }

                openTimer = openDelay.get();
            }
        }
    }

    private void performTrade(MerchantScreenHandler handler, TradeOffer offer, int index) {
        mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(index));
        handler.setRecipeIndex(index);

        if (fillTradeSlots(offer, handler)) {
            timer = slotDelay.get();
        }
    }

    private void handleOutputSlot(MerchantScreenHandler handler, ItemStack output) {
        if (output.isStackable()) {
            mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.QUICK_MOVE, mc.player);
        } else {
            int targetSlot = findTargetSlot(handler, output);
            if (targetSlot != -1) {
                mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);

                if (!handler.getCursorStack().isEmpty()) {
                    mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
                }
            }
        }
    }

    private boolean hasInventorySpace() {
        int freeSlots = 0;
        for (int i = 3; i <= 38; i++) {
            if (mc.player.currentScreenHandler.getSlot(i).getStack().isEmpty()) {
                freeSlots++;
            }
        }

        return freeSlots > 3;
    }

    private boolean isDesiredItem(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (!tradeItems.get().contains(stack.getItem()) && stack.getItem() != Items.EMERALD) return false;

        if (stack.getItem() == Items.ENCHANTED_BOOK) {
            ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (enchants == null || enchants.isEmpty()) return false;

            List<String> selectedIds = enchantmentsList.get().stream()
                .map(enchant -> enchant.getValue().toString())
                .toList();

            boolean matchFound = false;
            for (var entry : enchants.getEnchantmentEntries()) {
                String currentId = entry.getKey().getIdAsString();
                int level = entry.getIntValue();

                if (selectedIds.contains(currentId)) {
                    if (!onlyMaxLevel.get() || level >= entry.getKey().value().getMaxLevel()) {
                        matchFound = true;
                    }
                }
            }
            return matchFound;
        }
        return tradeItems.get().contains(stack.getItem());
    }

    private boolean canAffordFromInventory(TradeOffer offer, MerchantScreenHandler handler) {
        int has1 = 0;
        int has2 = 0;

        for (int i = 3; i <= 38; i++) {
            ItemStack s = handler.getSlot(i).getStack();
            if (offer.getFirstBuyItem().matches(s)) has1 += s.getCount();
            if (offer.getSecondBuyItem().isPresent() && offer.getSecondBuyItem().get().matches(s)) {
                has2 += s.getCount();
            }
        }

        ItemStack slot0 = handler.getSlot(0).getStack();
        ItemStack slot1 = handler.getSlot(1).getStack();

        if (offer.getFirstBuyItem().matches(slot0)) has1 += slot0.getCount();
        if (offer.getSecondBuyItem().isPresent() && offer.getSecondBuyItem().get().matches(slot1)) {
            has2 += slot1.getCount();
        }

        boolean firstOk = has1 >= offer.getFirstBuyItem().count();
        boolean secondOk = true;
        if (offer.getSecondBuyItem().isPresent()) {
            secondOk = has2 >= offer.getSecondBuyItem().get().count();
        }

        return firstOk && secondOk;
    }

    private boolean fillTradeSlots(TradeOffer offer, MerchantScreenHandler handler) {
        net.minecraft.village.TradedItem req1 = offer.getFirstBuyItem();
        ItemStack slot0 = handler.getSlot(0).getStack();

        if (slot0.isEmpty() || !req1.matches(slot0) || slot0.getCount() < req1.count()) {
            for (int i = 3; i <= 38; i++) {
                ItemStack invStack = handler.getSlot(i).getStack();
                if (req1.matches(invStack)) {
                    mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return true;
                }
            }
        }

        if (offer.getSecondBuyItem().isPresent()) {
            net.minecraft.village.TradedItem req2 = offer.getSecondBuyItem().get();
            ItemStack slot1 = handler.getSlot(1).getStack();

            if (slot1.isEmpty() || !req2.matches(slot1) || slot1.getCount() < req2.count()) {
                for (int i = 3; i <= 38; i++) {
                    ItemStack invStack = handler.getSlot(i).getStack();
                    if (req2.matches(invStack)) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int findTargetSlot(MerchantScreenHandler handler, ItemStack stack) {
        if (stack.isStackable()) {
            for (int i = 3; i <= 38; i++) {
                ItemStack invStack = handler.getSlot(i).getStack();
                if (!invStack.isEmpty() && ItemStack.areItemsEqual(stack, invStack) && invStack.getCount() < invStack.getMaxCount()) {
                    return i;
                }
            }
        }
        for (int i = 3; i <= 38; i++) {
            if (handler.getSlot(i).getStack().isEmpty()) return i;
        }
        return -1;
    }

    private void findAndOpenVillager() {
        List<VillagerEntity> villagers = mc.world.getEntitiesByClass(VillagerEntity.class,
            mc.player.getBoundingBox().expand(range.get()),
            v -> v.isAlive() && !v.isBaby()
        );

        if (villagers.isEmpty()) return;
        villagers.sort((v1, v2) -> {
            double d1 = mc.player.squaredDistanceTo(v1);
            double d2 = mc.player.squaredDistanceTo(v2);
            return Double.compare(d1, d2);
        });

        VillagerEntity target = null;
        for (VillagerEntity villager : villagers) {
            if (!interactedVillagers.contains(villager.getUuid())) {
                target = villager;
                break;
            }
        }

        if (target == null) {
            interactedVillagers.clear();
            target = villagers.getFirst();
        }

        interactedVillagers.add(target.getUuid());

        VillagerEntity finalTarget = target;
        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(finalTarget), Rotations.getPitch(finalTarget), 10, () -> interactWithVillager(finalTarget));
        } else {
            interactWithVillager(finalTarget);
        }
    }

    private void interactWithVillager(VillagerEntity villager) {
        if (villager == null || !villager.isAlive()) return;

        Vec3d hitVec = villager.getEntityPos().add(0, villager.getHeight() / 2.0, 0);
        EntityHitResult hitResult = new EntityHitResult(villager, hitVec);

        ActionResult result = mc.interactionManager.interactEntityAtLocation(mc.player, villager, hitResult, Hand.MAIN_HAND);

        if (!result.isAccepted()) {
            result = mc.interactionManager.interactEntity(mc.player, villager, Hand.MAIN_HAND);
        }

        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
            openTimer = openDelay.get();
        }
    }

    private void closeMerchantScreen() {
        if (mc.player != null) {

            mc.player.closeHandledScreen();
            if (mc.currentScreen != null) {
                mc.setScreen(null);
            }
            mc.player.getInventory().updateItems();
        }
    }
}
