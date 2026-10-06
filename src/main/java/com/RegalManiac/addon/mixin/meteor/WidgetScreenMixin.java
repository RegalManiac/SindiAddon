package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.utils.sound.SoundUtils;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import net.minecraft.client.gui.Click;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WidgetScreen.class, remap = false)
public abstract class WidgetScreenMixin {

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void onMouseClickedReturn(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        SoundUtils.playWidgetClickSound(cir.getReturnValue());
    }
}
