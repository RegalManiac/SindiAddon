package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.modules.misc.ChatControl;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Screen.class, remap = false)
public abstract class ScreenMixin {

    @Inject(method = "handleClickEvent", at = @At("HEAD"), cancellable = true)
    private static void onHandleClickEvent(ClickEvent clickEvent, MinecraftClient client, Screen screenAfterRun, CallbackInfo ci) {
        if (clickEvent == null) return;

        ChatControl chatControl = Modules.get().get(ChatControl.class);
        boolean moduleActive = chatControl != null && chatControl.isActive();

        if (clickEvent instanceof ClickEvent.CopyToClipboard(String value)) {
            if (!moduleActive || !chatControl.copyMessages.get()) {
                ci.cancel();
                return;
            }

            if (value != null && !value.isEmpty()) {
                client.keyboard.setClipboard(value);
                ci.cancel();
            }
        }

        if (clickEvent instanceof ClickEvent.SuggestCommand(String command)) {
            if (!moduleActive || !chatControl.nameClick.get()) {
                ci.cancel();
                return;
            }
            if (command != null && (command.startsWith("/tell ") || command.startsWith("/w "))) {
                String customCmd = chatControl.clickCommand.get();
                String playerName = command.substring(command.indexOf(' ') + 1);

                if (screenAfterRun != null) {
                    screenAfterRun.insertText(customCmd + playerName, true);
                }
                ci.cancel();
            }
        }
    }
}
