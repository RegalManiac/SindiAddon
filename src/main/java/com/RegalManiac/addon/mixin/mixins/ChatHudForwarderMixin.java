package com.RegalManiac.addon.mixin.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.font.Alignment;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.RegalManiac.addon.utils.text.TextUtils.SHIFTED_TEXTS;

@Mixin(targets = "net.minecraft.client.gui.hud.ChatHud$Forwarder")
public abstract class ChatHudForwarderMixin {

    @WrapOperation(method = "text", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/DrawnTextConsumer;text(Lnet/minecraft/client/font/Alignment;IILnet/minecraft/text/OrderedText;)V"))
    private void fixForwarderClickOffset(DrawnTextConsumer consumer, Alignment alignment, int x, int y, OrderedText text, Operation<Void> original) {
        if (SHIFTED_TEXTS.contains(text)) {
            x += 10;
        }
        original.call(consumer, alignment, x, y, text);
    }
}
