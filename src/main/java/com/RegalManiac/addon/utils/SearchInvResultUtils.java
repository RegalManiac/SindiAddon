package com.RegalManiac.addon.utils;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import static meteordevelopment.meteorclient.MeteorClient.mc;


public record SearchInvResultUtils(int slot, boolean found, ItemStack stack) {
    private static final SearchInvResultUtils NOT_FOUND_RESULT = new SearchInvResultUtils(-1, false, null);

    public static SearchInvResultUtils notFound() {
        return NOT_FOUND_RESULT;
    }

    public static @NotNull SearchInvResultUtils inOffhand(ItemStack stack) {
        return new SearchInvResultUtils(999, true, stack);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isHolding() {
        if (mc.player == null) return false;

        return mc.player.getInventory().getSelectedSlot() == slot;
    }

    public boolean isInHotBar() {
        return slot < 9;
    }

    public void switchTo() {
        if (found && isInHotBar())
            InventoryUtils.switchTo(slot, true);
    }

    public void switchToSilent() {
        if (found && isInHotBar())
            InventoryUtils.switchToSilent(slot);
    }
}
