package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.modules.movement.BlinkPlus;
import io.netty.channel.Channel;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.network.ClientConnection;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    @Shadow
    private Channel channel;

    @Redirect(
        method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;isOpen()Z")
    )
    private boolean blink$isOpenSend(ClientConnection instance) {
        BlinkPlus blink = Modules.get().get(BlinkPlus.class);

        if (blink != null && blink.isActive() && blink.onSend()) {
            return false;
        }
        return instance.isOpen();
    }

    @Redirect(
        method = "flush",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;isOpen()Z")
    )
    private boolean blink$isOpenFlush(ClientConnection instance) {
        BlinkPlus blink = Modules.get().get(BlinkPlus.class);

        if (blink != null && blink.isActive() && blink.shouldDelay()) {
            return false;
        }
        return instance.isOpen();
    }

    @Redirect(
        method = "handleQueuedTasks",
        at = @At(value = "FIELD", target = "Lnet/minecraft/network/ClientConnection;channel:Lio/netty/channel/Channel;", opcode = Opcodes.GETFIELD)
    )
    private Channel blink$isChannelOpen(ClientConnection instance) {
        BlinkPlus blink = Modules.get().get(BlinkPlus.class);

        if (blink != null && blink.isActive() && blink.shouldDelay()) {
            return null;
        }
        return this.channel;
    }
}
