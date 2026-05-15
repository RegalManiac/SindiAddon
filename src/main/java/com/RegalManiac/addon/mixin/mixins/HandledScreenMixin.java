package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.utils.ShulkerRenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(HandledScreen.class)
public class HandledScreenMixin {

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void onDrawSlotTail(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ShulkerRenderUtils.renderShulkerOverlay(context, slot.x, slot.y, slot.getStack());
    }
}
