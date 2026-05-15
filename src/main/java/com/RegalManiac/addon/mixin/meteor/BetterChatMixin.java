package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.modules.misc.ChatControl;
import meteordevelopment.meteorclient.mixininterface.IChatHudLineVisible;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.BetterChat;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.util.Identifier;
import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Pattern;

@Mixin(value = BetterChat.class, remap = false)
public abstract class BetterChatMixin {

    @Shadow
    public ChatHudLine.Visible line;

    @Final
    @Shadow
    private Setting<Boolean> playerHeads;

    @Unique
    private static final Identifier METEOR_CHAT_ICON = Identifier.of("meteor-client", "textures/icons/chat/meteor.png");
    @Unique
    private static final Identifier SINDIADDON_CHAT_ICON = Identifier.of("sindiaddon", "textures/icon/sindi.png");
    @Unique
    private static final Identifier BARITONE_CHAT_ICON = Identifier.of("meteor-client", "textures/icons/chat/baritone.png");

    @Unique private static final Pattern PLAYER_NAME_REGEX_1 = Pattern.compile("^<([a-zA-Z0-9_]{3,16})>");
    @Unique private static final Pattern PLAYER_NAME_REGEX_2 = Pattern.compile("^\\[([a-zA-Z0-9_]{3,16})\\]");
    @Unique private static final Pattern PLAYER_NAME_REGEX_3 = Pattern.compile("^([a-zA-Z0-9_]{3,16})\\s?[>:]");

    @Unique
    private boolean sindiPushed = false;

    @Unique
    private DrawContext currentContext = null;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void addHideIcon(CallbackInfo ci) {
        ((BetterChat) (Object) this).settings.getDefaultGroup().add(new BoolSetting.Builder()
            .name("hide-meteor-icon")
            .description("Hide Meteor icon in chat.")
            .defaultValue(true)
            .build()
        );
    }

    @Inject(method = "beforeDrawMessage", at = @At("HEAD"), cancellable = true)
    private void onBeforeDrawMessage(DrawContext context, int y, int color, CallbackInfo ci) {
        this.sindiPushed = false;
        this.currentContext = null;

        BetterChat betterChat = (BetterChat) (Object) this;
        ChatControl cc = Modules.get().get(ChatControl.class);

        if (!betterChat.isActive() || cc == null || !cc.isActive() || this.line == null) return;
        if (!((IChatHudLineVisible) (Object) this.line).meteor$isStartOfEntry()) return;

        StringBuilder sb = new StringBuilder();
        this.line.content().accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        String text = sb.toString().trim();

        String vPrefix = cc.lBracket.get() + cc.customPrefix.get() + cc.rBracket.get();
        String mPrefix = cc.lBracketMeteor.get() + cc.meteorText.get() + cc.rBracketMeteor.get();
        String bPrefix = "[B]";

        Identifier customIcon = null;
        int searchLimit = Math.min(text.length(), 50);
        String startText = text.substring(0, searchLimit);

        if (startText.contains(mPrefix)) customIcon = METEOR_CHAT_ICON;
        else if (startText.contains(bPrefix)) customIcon = BARITONE_CHAT_ICON;
        else if (startText.contains(vPrefix)) customIcon = SINDIADDON_CHAT_ICON;

        if (customIcon != null) {
            context.getMatrices().pushMatrix();
            context.drawTexture(RenderPipelines.GUI_TEXTURED, customIcon, 0, y, 0, 0, 8, 8, 64, 64, 64, 64, color);
            context.getMatrices().popMatrix();

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(10.0F, 0.0F);
            this.sindiPushed = true;
            this.currentContext = context;
            ci.cancel();
            return;
        }

        if (!this.playerHeads.get()) return;

        GameProfile sender = ((IChatHudLineVisible) (Object) this.line).meteor$getSender();

        if (sender == null) {
            String strippedText = text;
            if (cc.timestamps.get() && startText.contains(cc.lBracketTimestamps.get()) && startText.contains(cc.rBracketTimestamps.get())) {
                int rBracketIdx = strippedText.indexOf(cc.rBracketTimestamps.get());
                if (rBracketIdx != -1) {
                    strippedText = strippedText.substring(rBracketIdx + cc.rBracketTimestamps.get().length()).trim();
                }
            }

            java.util.regex.Matcher m1 = PLAYER_NAME_REGEX_1.matcher(strippedText);
            java.util.regex.Matcher m2 = PLAYER_NAME_REGEX_2.matcher(strippedText);
            java.util.regex.Matcher m3 = PLAYER_NAME_REGEX_3.matcher(strippedText);

            String username = null;
            if (m1.find()) username = m1.group(1);
            else if (m2.find()) username = m2.group(1);
            else if (m3.find()) username = m3.group(1);

            if (username != null) {
                net.minecraft.client.network.PlayerListEntry entry = net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(username);
                if (entry != null) sender = entry.getProfile();
            }
        }

        if (sender != null) {
            net.minecraft.client.network.PlayerListEntry entry = net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(sender.id());
            if (entry != null) {
                context.getMatrices().pushMatrix();
                PlayerSkinDrawer.draw(context, entry.getSkinTextures(), 0, y, 8, color);
                context.getMatrices().popMatrix();

                context.getMatrices().pushMatrix();
                context.getMatrices().translate(10.0F, 0.0F);
                this.sindiPushed = true;
                this.currentContext = context;

                ci.cancel();
            }
        }
    }

    @Inject(method = "afterDrawMessage", at = @At("HEAD"))
    private void onAfterDrawMessage(CallbackInfo ci) {
        if (this.sindiPushed && this.currentContext != null) {
            this.currentContext.getMatrices().popMatrix();
            this.sindiPushed = false;
            this.currentContext = null;
        }
    }

    @Unique
    private String stripSindiAddon(String str) {
        if (str == null) return null;
        ChatControl cc = Modules.get().get(ChatControl.class);

        if (cc != null && cc.isActive()) {
            if (cc.timestamps.get()) {
                String lTime = cc.lBracketTimestamps.get();
                String rTime = cc.rBracketTimestamps.get();
                int start = str.indexOf(lTime);
                if (start >= 0) {
                    int end = str.indexOf(rTime, start + lTime.length());
                    if (end > start) {
                        String toRemove = str.substring(start, end + rTime.length());
                        str = str.replace(toRemove, "").trim();
                    }
                }
            }
            String prefix = cc.lBracket.get() + cc.customPrefix.get() + cc.rBracket.get();
            str = str.replace(prefix + " ", "").replace(prefix, "");

            if (cc.meteor.get()) {
                String mPrefix = cc.lBracketMeteor.get() + cc.meteorText.get() + cc.rBracketMeteor.get();
                str = str.replace(mPrefix + " ", "").replace(mPrefix, "");
            }
        }
        return str.trim();
    }

    @ModifyVariable(method = "appendAntiSpam", at = @At("STORE"), name = "textString")
    private String cleanTextStringForAntiSpam(String textString) {
        return stripSindiAddon(textString);
    }

    @ModifyVariable(method = "appendAntiSpam", at = @At("STORE"), name = "stringToCheck")
    private String cleanStringToCheckForAntiSpam(String stringToCheck) {
        return stripSindiAddon(stringToCheck);
    }
}
