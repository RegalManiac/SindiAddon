package com.RegalManiac.addon.utils;

import com.RegalManiac.addon.mixin.accessors.ClientPlayerInteractionManagerAccessor;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

import java.util.Arrays;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class InventoryUtils {

    private static int cachedSlot = -1;
    public static int serverSideSlot;

    public static void switchTo(int slot, boolean b) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (mc.player.getInventory().getSelectedSlot() == slot && serverSideSlot == slot)
            return;
        mc.player.getInventory().setSelectedSlot(slot);
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).syncSlot();
    }

    public static void switchToSilent(int slot) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    public static void returnSlot() {
        if (cachedSlot != -1)
            switchTo(cachedSlot, true);
        cachedSlot = -1;
    }

    public static void saveSlot() {
        cachedSlot = mc.player.getInventory().getSelectedSlot();
    }

    public static SearchInvResultUtils findItemInHotBar(Item... items) {
        return findItemInHotBar(Arrays.asList(items));
    }

    public static SearchInvResultUtils findItemInHotBar(List<Item> items) {
        return findInHotBar(stack -> items.contains(stack.getItem()));
    }

    public static SearchInvResultUtils findInHotBar(Searcher searcher) {
        if (mc.player != null) {
            for (int i = 0; i < 9; ++i) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (searcher.isValid(stack)) {
                    return new SearchInvResultUtils(i, true, stack);
                }
            }
        }

        return SearchInvResultUtils.notFound();
    }

    public static SearchInvResultUtils findItemInInventory(Item... items) {
        return findItemInInventory(Arrays.asList(items));
    }

    public static SearchInvResultUtils findItemInInventory(List<Item> items) {
        return findInInventory(stack -> items.contains(stack.getItem()));
    }

    public static SearchInvResultUtils findInInventory(Searcher searcher) {
        if (mc.player != null) {
            for (int i = 36; i >= 0; i--) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (searcher.isValid(stack)) {
                    if (i < 9) i += 36;
                    return new SearchInvResultUtils(i, true, stack);
                }
            }
        }

        return SearchInvResultUtils.notFound();
    }

    public static void sendPacket(Packet<?> packet) {
        if (mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().sendPacket(packet);
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        if (mc.getNetworkHandler() == null || mc.world == null) return;
        try (PendingUpdateManager pendingUpdateManager = mc.world.getPendingUpdateManager().incrementSequence();) {
            int i = pendingUpdateManager.getSequence();
            mc.getNetworkHandler().sendPacket(packetCreator.predict(i));
        }
    }

    public interface Searcher {
        boolean isValid(ItemStack stack);
    }
}
