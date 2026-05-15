package com.RegalManiac.addon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class OffhandPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBindSwap = settings.createGroup("Bind Swap");
    private final SettingGroup sgSafety = settings.createGroup("Safety Factors");

    // --- General ---
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

    // --- Bind Swap ---
    private final Setting<Boolean> bindSwapActive = sgBindSwap.add(new BoolSetting.Builder()
        .name("enable-bind-swap")
        .description("Enables the bind swapping mode.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> bindSwapHealth = sgBindSwap.add(new DoubleSetting.Builder()
        .name("bind-swap-health")
        .description("Health threshold specifically for bind swapping.")
        .defaultValue(15).range(0, 36).sliderMax(36)
        .visible(bindSwapActive::get)
        .build()
    );

    private final Setting<Keybind> swapKeybind = sgBindSwap.add(new KeybindSetting.Builder()
        .name("swap-keybind")
        .description("Hold this key to use the item.")
        .defaultValue(Keybind.none())
        .visible(bindSwapActive::get)
        .build()
    );

    private final Setting<Boolean> weaponGapple = sgBindSwap.add(new BoolSetting.Builder()
        .name("tool-item")
        .description("Allow default bind swap ONLY when holding a tool.")
        .defaultValue(false)
        .visible(bindSwapActive::get)
        .build()
    );

    private final Setting<SlotType> type1 = sgBindSwap.add(new EnumSetting.Builder<SlotType>().name("slot-1-type").defaultValue(SlotType.Item).visible(bindSwapActive::get).build());
    private final Setting<java.util.List<net.minecraft.item.Item>> item1 = sgBindSwap.add(new ItemListSetting.Builder().name("item-1").visible(() -> bindSwapActive.get() && type1.get() == SlotType.Item).build());
    private final Setting<java.util.List<net.minecraft.entity.effect.StatusEffect>> pot1 = sgBindSwap.add(new StatusEffectListSetting.Builder().name("potions-1").visible(() -> bindSwapActive.get() && type1.get() == SlotType.Potion).build());

    private final Setting<SlotType> type2 = sgBindSwap.add(new EnumSetting.Builder<SlotType>().name("slot-2-type").defaultValue(SlotType.Item).visible(bindSwapActive::get).build());
    private final Setting<java.util.List<net.minecraft.item.Item>> item2 = sgBindSwap.add(new ItemListSetting.Builder().name("item-2").visible(() -> bindSwapActive.get() && type2.get() == SlotType.Item).build());
    private final Setting<java.util.List<net.minecraft.entity.effect.StatusEffect>> pot2 = sgBindSwap.add(new StatusEffectListSetting.Builder().name("potions-2").visible(() -> bindSwapActive.get() && type2.get() == SlotType.Potion).build());

    private final Setting<SlotType> type3 = sgBindSwap.add(new EnumSetting.Builder<SlotType>().name("slot-3-type").defaultValue(SlotType.Potion).visible(bindSwapActive::get).build());
    private final Setting<java.util.List<net.minecraft.item.Item>> item3 = sgBindSwap.add(new ItemListSetting.Builder().name("item-3").visible(() -> bindSwapActive.get() && type3.get() == SlotType.Item).build());
    private final Setting<java.util.List<net.minecraft.entity.effect.StatusEffect>> pot3 = sgBindSwap.add(new StatusEffectListSetting.Builder().name("potions-3").visible(() -> bindSwapActive.get() && type3.get() == SlotType.Potion).build());

    private final Setting<Boolean> autoUse = sgBindSwap.add(new BoolSetting.Builder()
        .name("auto-use")
        .description("Automatically use the item when swapped.")
        .defaultValue(true)
        .visible(bindSwapActive::get)
        .build()
    );

    // --- Safety Factors ---
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

    public OffhandPlus() {
        super(Categories.Combat, "offhand-+", "Advanced offhand with bind swap.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (ticks > 0) { ticks--; return; }

        ItemStack offhand = mc.player.getOffHandStack();

        if (isSwapKeyHeld()) {
            if (!wasSwapped) {
                currentSlot = -1;
                for (int i = 0; i < 3; i++) {
                    if (isSlotSettingsValid(i) && findInSlot(i).found()) {
                        currentSlot = i;
                        break;
                    }
                }
            }
            if (currentSlot != -1) {
                FindItemResult result = findInSlot(currentSlot);

                if (!result.found()) {
                    if (offhand.getItem() != Items.TOTEM_OF_UNDYING) forceEquip(Items.TOTEM_OF_UNDYING);
                } else {
                    ItemStack targetStack = mc.player.getInventory().getStack(result.slot());

                    if (!ItemStack.areItemsAndComponentsEqual(offhand, targetStack)) {
                        stopUsing();
                        forceEquip(currentSlot);
                    } else if (autoUse.get()) {
                        boolean isFood = targetStack.get(DataComponentTypes.FOOD) != null;
                        boolean isPotion = targetStack.getItem() instanceof net.minecraft.item.PotionItem;

                        if (isFood || isPotion) {
                            if (!mc.player.isUsingItem()) {
                                mc.interactionManager.interactItem(mc.player, net.minecraft.util.Hand.OFF_HAND);
                            }
                            mc.options.useKey.setPressed(true);
                        }
                    }
                }
            } else {
                if (offhand.getItem() != Items.TOTEM_OF_UNDYING) {
                    forceEquip(Items.TOTEM_OF_UNDYING);
                }
            }
            if (offhand.getItem() == Items.GLASS_BOTTLE) InvUtils.drop().slotId(45);
            wasSwapped = true;
        }
        else {
            if (wasSwapped) {
                stopUsing();
                forceEquip(Items.TOTEM_OF_UNDYING);
                wasSwapped = false;
            }
            else if (!checkSafety(false)) {
                if (offhand.getItem() != Items.TOTEM_OF_UNDYING) forceEquip(Items.TOTEM_OF_UNDYING);
            }
        }
        ticks = delay.get();
    }

    public boolean isSwapKeyHeld() {
        if (!isActive() || !bindSwapActive.get() || !checkSafety(true)) return false;

        if (mc.currentScreen != null) {
            if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) return false;
            if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)) return false;
        }

        boolean customBindSet = swapKeybind.get().isSet();
        boolean pressed = customBindSet ? swapKeybind.get().isPressed() : mc.options.useKey.isPressed();

        if (!pressed) return false;

        if (!customBindSet && weaponGapple.get()) {
            net.minecraft.item.Item mainHandItem = mc.player.getMainHandStack().getItem();
            if (!isWeapon(mainHandItem)) {
                return false;
            }
        }

        if (mc.currentScreen == null && (!customBindSet || mc.options.useKey.isPressed())) {
            if (mc.crosshairTarget != null) {
                if (mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                    BlockPos pos = ((net.minecraft.util.hit.BlockHitResult) mc.crosshairTarget).getBlockPos();
                    net.minecraft.block.BlockState state = mc.world.getBlockState(pos);
                    net.minecraft.block.Block block = state.getBlock();

                    if (state.hasBlockEntity() ||
                        block instanceof net.minecraft.block.CraftingTableBlock ||
                        block instanceof net.minecraft.block.EnderChestBlock ||
                        block instanceof net.minecraft.block.AnvilBlock) {
                        return false;
                    }
                } else if (mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY) {
                    net.minecraft.entity.Entity entity = ((net.minecraft.util.hit.EntityHitResult) mc.crosshairTarget).getEntity();
                    if (entity instanceof net.minecraft.entity.passive.MerchantEntity ||
                        entity instanceof net.minecraft.entity.vehicle.StorageMinecartEntity) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @EventHandler
    private void onMouseScroll(meteordevelopment.meteorclient.events.meteor.MouseScrollEvent event) {
        if (isSwapKeyHeld()) {
            event.cancel();

            if (mc.player.isUsingItem()) {
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
                    BlockPos.ORIGIN,
                    Direction.DOWN
                ));
                mc.player.stopUsingItem();
            }

            if (swapKeybind.get().isSet()) {
                mc.options.useKey.setPressed(false);
            }

            int direction = event.value > 0 ? -1 : 1;
            int nextSlot = currentSlot;

            for (int i = 0; i < 3; i++) {
                nextSlot = (nextSlot + direction + 3) % 3;

                if (isSlotSettingsValid(nextSlot) && findInSlot(nextSlot).found()) {
                    currentSlot = nextSlot;
                    break;
                }
            }

            ItemStack selectedStack = getBindStack(currentSlot);
            if (!selectedStack.isEmpty() && !ItemStack.areItemsAndComponentsEqual(mc.player.getOffHandStack(), selectedStack)) {
                forceEquip(currentSlot);
            }

            ticks = 2;
        }
    }

    private boolean checkSafety(boolean isBindCheck) {
        double totalHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        double threshold = isBindCheck ? bindSwapHealth.get() : health.get();

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
            if (mc.player.fallDistance > 3) {
                return false;
            }
        }
        return true;
    }

    private void forceEquip(net.minecraft.item.Item item) {
        FindItemResult result = InvUtils.find(item);
        if (result.found()) {
            InvUtils.move().from(result.slot()).toOffhand();
        }
    }

    private void forceEquip(int slotIdx) {
        FindItemResult result = findInSlot(slotIdx);
        if (result.found()) {
            InvUtils.move().from(result.slot()).toOffhand();
        }
    }

    private boolean isSlotSettingsValid(int slot) {
        SlotType type = getSlotType(slot);
        if (type == SlotType.Item) {
            java.util.List<net.minecraft.item.Item> items = (slot == 1) ? item2.get() : (slot == 2) ? item3.get() : item1.get();
            return items != null && !items.isEmpty();
        } else {
            java.util.List<net.minecraft.entity.effect.StatusEffect> effects = getPotionList(slot);
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

    private FindItemResult findPotionByEffect(java.util.List<net.minecraft.entity.effect.StatusEffect> targets) {
        if (targets == null || targets.isEmpty()) return InvUtils.find(ItemStack.EMPTY.getItem());

        return InvUtils.find(stack -> {
            net.minecraft.item.Item item = stack.getItem();
            if (item != Items.POTION && item != Items.SPLASH_POTION && item != Items.LINGERING_POTION) return false;

            PotionContentsComponent contents = stack.get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS);
            if (contents == null) return false;

            return targets.stream().anyMatch(target ->
                java.util.stream.StreamSupport.stream(contents.getEffects().spliterator(), false)
                    .anyMatch(inst -> inst.getEffectType().value() == target)
            );
        });
    }

    private void stopUsing() {
        if (swapKeybind.get().isSet()) {
            mc.options.useKey.setPressed(false);
        }

        if (mc.player.isUsingItem()) {
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
        java.util.List<net.minecraft.item.Item> list = switch (slot) { case 1 -> item2.get(); case 2 -> item3.get(); default -> item1.get(); };
        return (list == null || list.isEmpty()) ? null : list.getFirst();
    }

    private java.util.List<net.minecraft.entity.effect.StatusEffect> getPotionList(int slot) {
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
            item == Items.WOODEN_SPEAR || item == Items.STONE_SPEAR || item == Items.IRON_SPEAR ||
            item == Items.GOLDEN_SPEAR || item == Items.DIAMOND_SPEAR || item == Items.NETHERITE_SPEAR ||
            item == Items.TRIDENT || item == Items.MACE;
    }

    @Override
    public void onDeactivate() {
        if (swapKeybind.get().isSet()) {
            mc.options.useKey.setPressed(false);
        }
    }

    public int getCurrentSlot() {
        return currentSlot;
    }

    public ItemStack getBindStack(int slot) {
        if (!isSlotSettingsValid(slot)) return ItemStack.EMPTY;

        FindItemResult res = findInSlot(slot);

        if (res.found()) {
            return mc.player.getInventory().getStack(res.slot());
        }

        return ItemStack.EMPTY;
    }
}
