package com.RegalManiac.addon.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class AutoStripper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum SwapMode {
        Normal,
        Silent
    }

    private final Setting<SwapMode> swapMode = sgGeneral.add(new EnumSetting.Builder<SwapMode>()
        .name("swap-mode")
        .description("How to switch to the axe. Silent won't visually change your held item.")
        .defaultValue(SwapMode.Normal)
        .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Blocks to strip or scrape (only valid blocks are shown).")
        .defaultValue(STRIPPABLE)
        .filter(STRIPPABLE::contains)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum distance to reach blocks.")
        .defaultValue(4.5)
        .min(0).max(6)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("The delay between actions in ticks.")
        .defaultValue(2)
        .min(0).max(20)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Automatically rotates towards the block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> saveTool = sgGeneral.add(new BoolSetting.Builder()
        .name("save-tool")
        .description("Swaps the axe before it breaks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minDurability = sgGeneral.add(new IntSetting.Builder()
        .name("min-durability")
        .description("The minimum durability at which to swap the tool.")
        .defaultValue(10)
        .visible(saveTool::get)
        .build()
    );

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Disables the module if no suitable axe is found.")
        .defaultValue(false)
        .build()
    );

    private int timer;
    private static final List<Block> STRIPPABLE = getStrippableList();

    public AutoStripper() {
        super(Categories.World, "auto-stripper", "Automatically strips wood and scrapes wax from copper.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0) {
            timer--;
            return;
        }

        BlockPos target = findTarget();

        if (target != null) {
            int axeSlot = findAxeSlot();

            if (axeSlot == -1) {
                if (autoDisable.get()) {
                    error("Axe not found! Disabling...");
                    toggle();
                }
                return;
            }

            int preSlot = mc.player.getInventory().getSelectedSlot();

            if (axeSlot >= 9) {
                InvUtils.move().from(axeSlot).to(preSlot);
                axeSlot = preSlot;
            }

            if (swapMode.get() == SwapMode.Normal) {
                mc.player.getInventory().setSelectedSlot(axeSlot);
            } else if (axeSlot != preSlot) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
            }

            int finalAxeSlot = axeSlot;

            Runnable action = () -> {
                interact(target);
                if (swapMode.get() == SwapMode.Silent && finalAxeSlot != preSlot) {
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(preSlot));
                }
            };

            if (rotate.get()) {
                Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), action);
            } else {
                action.run();
            }

            timer = delay.get();
        }
    }

    private BlockPos findTarget() {
        BlockPos.Mutable bp = new BlockPos.Mutable();
        int r = (int) Math.ceil(range.get());

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    bp.set(mc.player.getX() + x, mc.player.getY() + y, mc.player.getZ() + z);

                    if (mc.player.squaredDistanceTo(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5) > range.get() * range.get()) continue;

                    Block block = mc.world.getBlockState(bp).getBlock();

                    if (blocks.get().contains(block) && STRIPPABLE.contains(block)) {
                        return bp.toImmutable();
                    }
                }
            }
        }
        return null;
    }

    private void interact(BlockPos pos) {
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos, false));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int findAxeSlot() {
        int bestHotbarSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (isGoodAxe(mc.player.getInventory().getStack(i))) {
                bestHotbarSlot = i;
                break;
            }
        }
        if (bestHotbarSlot != -1) return bestHotbarSlot;

        for (int i = 9; i < mc.player.getInventory().size(); i++) {
            if (isGoodAxe(mc.player.getInventory().getStack(i))) return i;
        }

        return -1;
    }

    private boolean isGoodAxe(ItemStack stack) {
        if (!(stack.getItem() instanceof AxeItem)) return false;
        if (!saveTool.get()) return true;
        return (stack.getMaxDamage() - stack.getDamage()) > minDurability.get();
    }

    private static List<Block> getStrippableList() {
        List<Block> b = new ArrayList<>();

        b.add(Blocks.OAK_LOG); b.add(Blocks.OAK_WOOD);
        b.add(Blocks.SPRUCE_LOG); b.add(Blocks.SPRUCE_WOOD);
        b.add(Blocks.BIRCH_LOG); b.add(Blocks.BIRCH_WOOD);
        b.add(Blocks.JUNGLE_LOG); b.add(Blocks.JUNGLE_WOOD);
        b.add(Blocks.ACACIA_LOG); b.add(Blocks.ACACIA_WOOD);
        b.add(Blocks.DARK_OAK_LOG); b.add(Blocks.DARK_OAK_WOOD);
        b.add(Blocks.MANGROVE_LOG); b.add(Blocks.MANGROVE_WOOD);
        b.add(Blocks.CHERRY_LOG); b.add(Blocks.CHERRY_WOOD);
        b.add(Blocks.BAMBOO_BLOCK);

        b.add(Blocks.CRIMSON_STEM); b.add(Blocks.CRIMSON_HYPHAE);
        b.add(Blocks.WARPED_STEM); b.add(Blocks.WARPED_HYPHAE);

        b.add(Blocks.WAXED_COPPER_BLOCK); b.add(Blocks.WAXED_EXPOSED_COPPER);
        b.add(Blocks.WAXED_WEATHERED_COPPER); b.add(Blocks.WAXED_OXIDIZED_COPPER);
        b.add(Blocks.WAXED_CUT_COPPER); b.add(Blocks.WAXED_EXPOSED_CUT_COPPER);
        b.add(Blocks.WAXED_WEATHERED_CUT_COPPER); b.add(Blocks.WAXED_OXIDIZED_CUT_COPPER);
        b.add(Blocks.WAXED_CHISELED_COPPER);
        b.add(Blocks.WAXED_CUT_COPPER_STAIRS); b.add(Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS);
        b.add(Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS); b.add(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS);
        b.add(Blocks.WAXED_CUT_COPPER_SLAB); b.add(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB);
        b.add(Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB); b.add(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB);

        return b;
    }
}
