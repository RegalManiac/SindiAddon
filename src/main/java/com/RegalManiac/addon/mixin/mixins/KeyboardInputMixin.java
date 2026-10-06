package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.events.JumpInputEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        JumpInputEvent event = MeteorClient.EVENT_BUS.post(JumpInputEvent.get(this.playerInput.jump()));

        if (event.isOverridden()) {
            PlayerInput pi = this.playerInput;
            this.playerInput = new PlayerInput(
                pi.forward(),
                pi.backward(),
                pi.left(),
                pi.right(),
                event.isJumping(),
                pi.sneak(),
                pi.sprint()
            );
        }
    }
}
