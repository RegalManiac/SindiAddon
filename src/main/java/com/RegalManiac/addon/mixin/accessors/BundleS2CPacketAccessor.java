package com.RegalManiac.addon.mixin.accessors;

import net.minecraft.network.packet.BundlePacket;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BundlePacket.class)
public interface BundleS2CPacketAccessor {
    @Mutable
    @Accessor("packets")
    void sindiaddon$setPackets(Iterable<Packet<?>> packets);
}
