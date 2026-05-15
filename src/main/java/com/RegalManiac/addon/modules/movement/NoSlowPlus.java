package com.RegalManiac.addon.modules.movement;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;

import java.util.Objects;

public class NoSlowPlus extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> slotIndex = sgGeneral.add(new IntSetting.Builder()
        .name("slot-index")
        .description("The slotIndex of the Packet")
        .noSlider()
        .defaultValue(36)
        .build()
    );

    private final Setting<Integer> button = sgGeneral.add(new IntSetting.Builder()
        .name("button")
        .description("The button of the Packet")
        .noSlider()
        .defaultValue(0)
        .build()
    );

    private boolean lastUsingItem = false;
    private boolean packetSent;

    public NoSlowPlus() {
        super(Categories.Movement, "no-slow-+", "Bypass Grim NoSlow checks");
    }

    @Override
    public void onActivate() {
        packetSent = false;
    }

    @EventHandler
    public void onTick(TickEvent.Pre e) {
        if(mc.player == null)
            return;

        if(mc.player.isUsingItem() != lastUsingItem) {
            if(mc.player.isUsingItem()) {
                for (int i = 0; i < 2; i++) {
                    Int2ObjectArrayMap<ItemStackHash> map = new Int2ObjectArrayMap<>();
                    map.put(0, ItemStackHash.fromItemStack(new ItemStack(Items.ACACIA_BOAT, 1), Objects.requireNonNull(mc.getNetworkHandler()).getComponentHasher()));
                    mc.player.networkHandler.sendPacket(new ClickSlotC2SPacket(
                        mc.player.currentScreenHandler.syncId,
                        mc.player.currentScreenHandler.getRevision(),
                        slotIndex.get().shortValue(),
                        button.get().byteValue(),
                        SlotActionType.SWAP,
                        map,
                        ItemStackHash.fromItemStack(mc.player.currentScreenHandler.getCursorStack(), mc.getNetworkHandler().getComponentHasher())
                    ));
                }

                packetSent = true;
            } else {
                packetSent = false;
            }
        }

        lastUsingItem = mc.player.isUsingItem();
    }

    public boolean canNoSlow() {
        return isActive() && packetSent;
    }
}
