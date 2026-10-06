package com.RegalManiac.addon.utils.automation;

import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.screen.ingame.SmithingScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AutomationUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public enum ClickMode {
        PACKET,
        METEOR
    }

    public static void quickMove(int slot, ClickMode mode) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.currentScreenHandler == null || mc.world == null) return;

        if (mode == ClickMode.METEOR) {
            InvUtils.shiftClick().slotId(slot);
        } else if (mode == ClickMode.PACKET) {
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slot,
                0,
                SlotActionType.QUICK_MOVE,
                mc.player
            );
        }
    }

    public static void pickup(int slot, int button, ClickMode mode) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.currentScreenHandler == null || mc.world == null) return;

        if (mode == ClickMode.METEOR || mode == ClickMode.PACKET) {
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slot,
                button,
                SlotActionType.PICKUP,
                mc.player
            );
        }
    }

    public static List<BlockPos> findContainers(double range) {
        List<BlockPos> list = new ArrayList<>();
        if (mc.player == null || mc.world == null) return list;
        BlockPos p = mc.player.getBlockPos();

        int r = (int) Math.ceil(range);
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = p.add(x, y, z);
                    if (getDistanceToBlock(pos) <= range) {
                        var block = mc.world.getBlockState(pos).getBlock();
                        if (block instanceof ShulkerBoxBlock || block instanceof ChestBlock || block instanceof BarrelBlock) {
                            list.add(pos);
                        }
                    }
                }
            }
        }
        return list;
    }

    public static double getDistanceToBlock(BlockPos pos) {
        if (mc.player == null) return 0;
        Vec3d eyes = mc.player.getEyePos();
        double dx = Math.max(0, Math.max(pos.getX() - eyes.x, eyes.x - (pos.getX() + 1)));
        double dy = Math.max(0, Math.max(pos.getY() - eyes.y, eyes.y - (pos.getY() + 1)));
        double dz = Math.max(0, Math.max(pos.getZ() - eyes.z, eyes.z - (pos.getZ() + 1)));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static void open(BlockPos p, boolean rotate) {
        if (p == null || mc.player == null) return;

        if (rotate) {
            double yaw = Rotations.getYaw(p);
            double pitch = Rotations.getPitch(p);
            Rotations.rotate(yaw, pitch);
        }

        BlockHitResult hitResult = new BlockHitResult(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), Direction.UP, p, true);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    public static boolean isInventoryFull(Set<Integer> personalSlots) {
        return getFreeInventorySlots(personalSlots) == 0;
    }

    public static int getFreeInventorySlots(Set<Integer> personalSlots) {
        if (mc.player == null) return 0;
        int free = 0;
        for (int i = 0; i < 36; i++) {
            if (personalSlots != null && personalSlots.contains(i)) continue;
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    public static boolean hasSpace(List<ItemStack> items) {
        if (items == null) return false;
        for (ItemStack s : items) {
            if (s.isEmpty()) return true;
        }
        return false;
    }

    public static int invToGui(int i, int offset) {
        return (i < 9) ? i + offset + 27 : i + offset - 9;
    }

    public static boolean isPaused() {
        if (mc.currentScreen == null) return false;
        return !(mc.currentScreen instanceof ShulkerBoxScreen) &&
            !(mc.currentScreen instanceof GenericContainerScreen) &&
            !(mc.currentScreen instanceof AnvilScreen) &&
            !(mc.currentScreen instanceof SmithingScreen);
    }
}
