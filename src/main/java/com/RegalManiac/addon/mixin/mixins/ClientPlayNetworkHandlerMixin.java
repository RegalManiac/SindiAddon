package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.modules.misc.ChatEncryptionPlus;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @ModifyVariable(method = "sendChatMessage", at = @At("HEAD"), argsOnly = true)
    private String onSendChatMessageModify(String content) {
        if (ChatEncryptionPlus.INSTANCE != null && ChatEncryptionPlus.INSTANCE.isActive()) {
            String processed = ChatEncryptionPlus.INSTANCE.processChatMessage(content);
            return processed == null ? "" : processed;
        }
        return content;
    }

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessageCancel(String content, CallbackInfo ci) {
        if (content != null && content.isEmpty()) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "sendChatCommand", at = @At("HEAD"), argsOnly = true)
    private String onSendChatCommandModify(String command) {
        if (ChatEncryptionPlus.INSTANCE != null && ChatEncryptionPlus.INSTANCE.isActive()) {
            String processed = ChatEncryptionPlus.INSTANCE.processCommand(command);
            return processed == null ? "" : processed;
        }
        return command;
    }

    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void onSendChatCommandCancel(String command, CallbackInfo ci) {
        if (command != null && command.isEmpty()) {
            ci.cancel();
        }
    }
}
