package com.RegalManiac.addon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

public class OffhandPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgQuickSwap = settings.createGroup("Quick Swap");
    private final SettingGroup sgSlot1 = settings.createGroup("Slot 1");
    private final SettingGroup sgSlot2 = settings.createGroup("Slot 2");
    private final SettingGroup sgSlot3 = settings.createGroup("Slot 3");
    private final SettingGroup sgSafety = settings.createGroup("Safety Factors");

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("The delay between slot movements (ticks).")
        .defaultValue(0).min(0)
        .build()
    );

    private final Setting<Double> health = sgGeneral.add(new DoubleSetting.Builder()
        .name("health")
        .description("Health threshold for mandatory totem.")
        .defaultValue(10).range(0, 36).sliderMax(36)
        .build()
    );

    private final Setting<Boolean> quickSwapActive = sgQuickSwap.add(new BoolSetting.Builder()
        .name("enable-quick-swap")
        .description("Enables right-click quick swap mode.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> quickSwapHealth = sgQuickSwap.add(new DoubleSetting.Builder()
        .name("quick-swap-health")
        .description("Health threshold specifically for quick swapping.")
        .defaultValue(15).range(0, 36).sliderMax(36)
        .visible(quickSwapActive::get)
        .build()
    );

    private final Setting<Boolean> weaponOnly = sgQuickSwap.add(new BoolSetting.Builder()
        .name("tool-only")
        .description("Allow quick swap ONLY when holding a weapon or tool.")
        .defaultValue(false)
        .visible(quickSwapActive::get)
        .build()
    );

    private final Setting<SlotType> type1 = sgSlot1.add(new EnumSetting.Builder<SlotType>()
        .name("type")
        .defaultValue(SlotType.Item)
        .visible(quickSwapActive::get)
        .build()
    );
    private final Setting<List<net.minecraft.item.Item>> item1 = sgSlot1.add(new ItemListSetting.Builder()
        .name("items")
        .visible(() -> quickSwapActive.get() && type1.get() == SlotType.Item)
        .build()
    );
    private final Setting<List<net.minecraft.entity.effect.StatusEffect>> pot1 = sgSlot1.add(new StatusEffectListSetting.Builder()
        .name("potions")
        .visible(() -> quickSwapActive.get() && type1.get() == SlotType.Potion)
        .build()
    );

    private final Setting<SlotType> type2 = sgSlot2.add(new EnumSetting.Builder<SlotType>()
        .name("type")
        .defaultValue(SlotType.Item)
        .visible(quickSwapActive::get)
        .build()
    );
    private final Setting<List<net.minecraft.item.Item>> item2 = sgSlot2.add(new ItemListSetting.Builder()
        .name("items")
        .visible(() -> quickSwapActive.get() && type2.get() == SlotType.Item)
        .build()
    );
    private final Setting<List<net.minecraft.entity.effect.StatusEffect>> pot2 = sgSlot2.add(new StatusEffectListSetting.Builder()
        .name("potions")
        .visible(() -> quickSwapActive.get() && type2.get() == SlotType.Potion)
        .build()
    );

    private final Setting<SlotType> type3 = sgSlot3.add(new EnumSetting.Builder<SlotType>()
        .name("type")
        .defaultValue(SlotType.Potion)
        .visible(quickSwapActive::get)
        .build()
    );
    private final Setting<List<net.minecraft.item.Item>> item3 = sgSlot3.add(new ItemListSetting.Builder()
        .name("items")
        .visible(() -> quickSwapActive.get() && type3.get() == SlotType.Item)
        .build()
    );
    private final Setting<List<net.minecraft.entity.effect.StatusEffect>> pot3 = sgSlot3.add(new StatusEffectListSetting.Builder()
        .name("potions")
        .visible(() -> quickSwapActive.get() && type3.get() == SlotType.Potion)
        .build()
    );

    private final Setting<Boolean> safeElytra = sgSafety.add(new BoolSetting.Builder()
        .name("elytra-check")
        .description("Force totem while flying with elytra.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> safeExplosion = sgSafety.add(new BoolSetting.Builder()
        .name("explosion-check")
        .description("Force totem if a lethal explosion is nearby.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> safeFall = sgSafety.add(new BoolSetting.Builder()
        .name("fall-check")
        .description("Force totem if fall damage might be lethal.")
        .defaultValue(true)
        .build()
    );

    public enum SlotType {
        Item,
        Potion
    }

    private int ticks;
    private int currentSlot = 0;
    private boolean wasSwapped = false;
    private boolean hasThrownSplash = false;

    public OffhandPlus() {
        super(Categories.Combat, "offhand-+", "Advanced offhand with quick swap.");
    }

    @Override
    public void onActivate() {
        ticks = 0;
        wasSwapped = false;
        hasThrownSplash = false;
        currentSlot = 0;

        if (mc.player != null && mc.player.getOffHandStack().isEmpty()) {
            forceEquip();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (ticks > 0) { ticks--; return; }

        ItemStack offhand = mc.player.getOffHandStack();

        if (isSwapKeyHeld()) {
            if (!wasSwapped) {
                hasThrownSplash = false;
                currentSlot = -1;
                for (int i = 0; i < 3; i++) {
                    if (isSlotSettingsValid(i) && (isStackMatchingSlot(offhand, i) || findInSlot(i).found())) {
                        currentSlot = i;
                        break;
                    }
                }
            }

            if (currentSlot != -1) {
                if (isStackMatchingSlot(offhand, currentSlot)) {
                    boolean isSplashPotion = offhand.getItem() == Items.SPLASH_POTION || offhand.getItem() == Items.LINGERING_POTION;

                    if (isSplashPotion) {
                        if (!hasThrownSplash) {
                            mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.OFF_HAND);
                            hasThrownSplash = true;
                        }
                    } else {
                        boolean isFood = offhand.get(DataComponentTypes.FOOD) != null;
                        boolean isDrinkPotion = offhand.getItem() instanceof net.minecraft.item.PotionItem;

                        if (isFood || isDrinkPotion) {
                            if (!mc.player.isUsingItem()) {
                                mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.OFF_HAND);
                            }
                        }
                    }
                } else {
                    FindItemResult result = findInSlot(currentSlot);

                    if (result.found()) {
                        stopUsing();
                        swapToOffhand(result.slot());
                    } else {
                        if (offhand.getItem() != Items.TOTEM_OF_UNDYING) {
                            stopUsing();
                            forceEquip();
                        }
                    }
                }
            } else {
                if (offhand.getItem() != Items.TOTEM_OF_UNDYING) {
                    stopUsing();
                    forceEquip();
                }
            }

            wasSwapped = true;
        } else {
            hasThrownSplash = false;

            if (wasSwapped) {
                stopUsing();
                forceEquip();
                wasSwapped = false;
            } else if (!checkSafety(false)) {
                if (offhand.getItem() != Items.TOTEM_OF_UNDYING) {
                    stopUsing();
                    forceEquip();
                }
            } else if (offhand.isEmpty()) {
                forceEquip();
            }
        }
        ticks = delay.get();
    }

    public boolean isSwapKeyHeld() {
        if (!isActive() || !quickSwapActive.get() || !checkSafety(true)) return false;

        if (mc.currentScreen != null) {
            if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) return false;
            if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)) return false;
        }

        if (!mc.options.useKey.isPressed()) return false;

        if (weaponOnly.get()) {
            net.minecraft.item.Item mainHandItem = mc.player.getMainHandStack().getItem();
            if (!isWeapon(mainHandItem)) {
                return false;
            }
        }

        if (mc.currentScreen == null && mc.crosshairTarget != null) {
            if (mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
                BlockState state = mc.world.getBlockState(pos);
                return !isInteractiveBlock(state);
            } else if (mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
                return !isInteractiveEntity(entity);
            }
        }

        return true;
    }

    private boolean isInteractiveBlock(BlockState state) {
        if (state.hasBlockEntity()) return true;

        Block b = state.getBlock();
        return b instanceof DoorBlock
            || b instanceof TrapdoorBlock
            || b instanceof FenceGateBlock
            || b instanceof ButtonBlock
            || b instanceof LeverBlock
            || b instanceof CraftingTableBlock
            || b instanceof AnvilBlock
            || b instanceof BedBlock
            || b instanceof RepeaterBlock
            || b instanceof ComparatorBlock
            || b instanceof NoteBlock
            || b instanceof RespawnAnchorBlock
            || b instanceof CakeBlock
            || b instanceof ComposterBlock
            || b instanceof GrindstoneBlock
            || b instanceof StonecutterBlock
            || b instanceof CartographyTableBlock
            || b instanceof LoomBlock
            || b instanceof BellBlock
            || b instanceof EnchantingTableBlock;
    }

    private boolean isInteractiveEntity(Entity entity) {
        return entity instanceof MerchantEntity
            || entity instanceof StorageMinecartEntity
            || entity instanceof AbstractHorseEntity
            || entity instanceof PigEntity
            || entity instanceof StriderEntity
            || entity instanceof BoatEntity
            || entity instanceof ItemFrameEntity
            || entity instanceof ArmorStandEntity;
    }

    @EventHandler
    private void onMouseScroll(meteordevelopment.meteorclient.events.meteor.MouseScrollEvent event) {
        if (isSwapKeyHeld()) {
            event.cancel();
            stopUsing();

            int direction = event.value > 0 ? -1 : 1;
            int prevSlot = currentSlot;
            int nextSlot = currentSlot;

            for (int i = 0; i < 3; i++) {
                nextSlot = (nextSlot + direction + 3) % 3;

                if (isSlotSettingsValid(nextSlot) && (isStackMatchingSlot(mc.player.getOffHandStack(), nextSlot) || findInSlot(nextSlot).found())) {
                    currentSlot = nextSlot;
                    break;
                }
            }

            if (currentSlot != prevSlot) {
                hasThrownSplash = false;
            }

            if (!isStackMatchingSlot(mc.player.getOffHandStack(), currentSlot)) {
                FindItemResult res = findInSlot(currentSlot);
                if (res.found()) {
                    swapToOffhand(res.slot());
                }
            }

            ticks = 2;
        }
    }

    private boolean checkSafety(boolean isBindCheck) {
        double totalHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        double threshold = isBindCheck ? quickSwapHealth.get() : health.get();

        if (totalHealth <= threshold) return false;

        if (safeElytra.get() && mc.player.isGliding()) return false;

        if (safeExplosion.get()) {
            boolean crystalNearby = mc.world.getEntitiesByClass(net.minecraft.entity.decoration.EndCrystalEntity.class,
                    mc.player.getBoundingBox().expand(12), entity -> true)
                .stream().anyMatch(crystal -> {
                    double damage = meteordevelopment.meteorclient.utils.entity.DamageUtils.crystalDamage(mc.player, crystal.getEntityPos());
                    return (totalHealth - damage) <= health.get();
                });
            if (crystalNearby) return false;
        }

        if (safeFall.get() && mc.player.getVelocity().y < -0.5) {
            return !(mc.player.fallDistance > 3);
        }
        return true;
    }

    private void swapToOffhand(int inventorySlot) {
        if (inventorySlot < 0 || mc.player == null || mc.interactionManager == null) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;

        int containerSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        int syncId = mc.player.playerScreenHandler.syncId;

        mc.interactionManager.clickSlot(syncId, containerSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, 45, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, containerSlot, 0, SlotActionType.PICKUP, mc.player);
    }

    private void forceEquip() {
        stopUsing();
        FindItemResult result = InvUtils.find(Items.TOTEM_OF_UNDYING);
        if (result.found()) {
            swapToOffhand(result.slot());
        }
    }

    private boolean isStackMatchingSlot(ItemStack stack, int slotIdx) {
        if (stack.isEmpty() || !isSlotSettingsValid(slotIdx)) return false;

        SlotType type = getSlotType(slotIdx);
        if (type == SlotType.Item) {
            net.minecraft.item.Item expectedItem = getRawItem(slotIdx);
            return expectedItem != null && stack.isOf(expectedItem);
        } else {
            List<net.minecraft.entity.effect.StatusEffect> targets = getPotionList(slotIdx);
            if (targets == null || targets.isEmpty()) return false;

            net.minecraft.item.Item item = stack.getItem();
            if (item != Items.POTION && item != Items.SPLASH_POTION && item != Items.LINGERING_POTION) return false;

            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents == null) return false;

            return targets.stream().anyMatch(target ->
                java.util.stream.StreamSupport.stream(contents.getEffects().spliterator(), false)
                    .anyMatch(inst -> inst.getEffectType().value() == target)
            );
        }
    }

    private boolean isSlotSettingsValid(int slot) {
        SlotType type = getSlotType(slot);
        if (type == SlotType.Item) {
            List<net.minecraft.item.Item> items = (slot == 1) ? item2.get() : (slot == 2) ? item3.get() : item1.get();
            return items != null && !items.isEmpty();
        } else {
            List<net.minecraft.entity.effect.StatusEffect> effects = getPotionList(slot);
            return effects != null && !effects.isEmpty();
        }
    }

    private FindItemResult findInSlot(int slotIdx) {
        if (!isSlotSettingsValid(slotIdx)) return InvUtils.find(ItemStack.EMPTY.getItem());

        SlotType type = getSlotType(slotIdx);
        if (type == SlotType.Item) {
            net.minecraft.item.Item item = getRawItem(slotIdx);
            return item != null ? InvUtils.find(item) : InvUtils.find(ItemStack.EMPTY.getItem());
        } else {
            return findPotionByEffect(getPotionList(slotIdx));
        }
    }

    private FindItemResult findPotionByEffect(List<net.minecraft.entity.effect.StatusEffect> targets) {
        if (targets == null || targets.isEmpty()) return InvUtils.find(ItemStack.EMPTY.getItem());

        return InvUtils.find(stack -> {
            net.minecraft.item.Item item = stack.getItem();
            if (item != Items.POTION && item != Items.SPLASH_POTION && item != Items.LINGERING_POTION) return false;

            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents == null) return false;

            return targets.stream().anyMatch(target ->
                java.util.stream.StreamSupport.stream(contents.getEffects().spliterator(), false)
                    .anyMatch(inst -> inst.getEffectType().value() == target)
            );
        });
    }

    private void stopUsing() {
        if (mc.player != null && mc.player.isUsingItem()) {
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                Direction.DOWN
            ));
            mc.player.stopUsingItem();
        }
    }

    private SlotType getSlotType(int slot) {
        return switch (slot) { case 1 -> type2.get(); case 2 -> type3.get(); default -> type1.get(); };
    }

    private net.minecraft.item.Item getRawItem(int slot) {
        List<net.minecraft.item.Item> list = switch (slot) { case 1 -> item2.get(); case 2 -> item3.get(); default -> item1.get(); };
        return (list == null || list.isEmpty()) ? null : list.getFirst();
    }

    private List<net.minecraft.entity.effect.StatusEffect> getPotionList(int slot) {
        return switch (slot) { case 1 -> pot2.get(); case 2 -> pot3.get(); default -> pot1.get(); };
    }

    private boolean isWeapon(net.minecraft.item.Item item) {
        return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.IRON_SWORD ||
            item == Items.GOLDEN_SWORD || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD ||
            item == Items.WOODEN_AXE || item == Items.STONE_AXE || item == Items.IRON_AXE ||
            item == Items.GOLDEN_AXE || item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE ||
            item == Items.WOODEN_PICKAXE || item == Items.STONE_PICKAXE || item == Items.IRON_PICKAXE ||
            item == Items.GOLDEN_PICKAXE || item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE ||
            item == Items.WOODEN_SHOVEL || item == Items.STONE_SHOVEL || item == Items.IRON_SHOVEL ||
            item == Items.GOLDEN_SHOVEL || item == Items.DIAMOND_SHOVEL || item == Items.NETHERITE_SHOVEL ||
            item == Items.MACE;
    }

    @Override
    public void onDeactivate() {
        stopUsing();
        wasSwapped = false;
        hasThrownSplash = false;
    }

    public int getCurrentSlot() {
        return currentSlot;
    }

    public ItemStack getBindStack(int slot) {
        if (!isSlotSettingsValid(slot)) return ItemStack.EMPTY;

        ItemStack offhand = mc.player != null ? mc.player.getOffHandStack() : ItemStack.EMPTY;
        if (isStackMatchingSlot(offhand, slot)) {
            return offhand;
        }

        FindItemResult res = findInSlot(slot);
        if (res.found()) {
            return mc.player.getInventory().getStack(res.slot());
        }

        return ItemStack.EMPTY;
    }
}
