package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.modules.misc.ChatControl;
import com.RegalManiac.addon.utils.TextUtils;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.NameProtect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Arrays;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @ModifyArg(
        method = "addVisibleMessage",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;<init>(ILnet/minecraft/text/OrderedText;Lnet/minecraft/client/gui/hud/MessageIndicator;Z)V"
        ),
        index = 1
    )
    private OrderedText applyRainbowToMessage(OrderedText original) {
        ChatControl cc = Modules.get().get(ChatControl.class);
        if (cc == null || !cc.isActive() || !cc.rainbow.get()) return original;

        StringBuilder sb = new StringBuilder();
        original.accept((index, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });

        String text = sb.toString();
        if (text.isEmpty()) return original;

        double[] hueOffsets = new double[text.length()];
        Arrays.fill(hueOffsets, 999.0);
        boolean[] needsRainbow = {false};
        double[] accumulated = {0};

        double spread = cc.rainbowWordSpread.get();
        boolean synchro = cc.synchro.get();

        class Marker {
            void mark(int start, int len) {
                for (int i = 0; i < len; i++) {
                    int idx = start + i;
                    if (idx < hueOffsets.length) {
                        hueOffsets[idx] = synchro ? (idx * spread) : accumulated[0];
                        if (!synchro) accumulated[0] += spread;
                    }
                }
                needsRainbow[0] = true;
            }
        }
        Marker marker = new Marker();

        String vPrefix = cc.lBracket.get() + cc.customPrefix.get() + cc.rBracket.get();
        if (text.contains(vPrefix)) marker.mark(text.indexOf(vPrefix), vPrefix.length());

        if (cc.meteor.get()) {
            String mPrefix = cc.lBracketMeteor.get() + cc.meteorText.get() + cc.rBracketMeteor.get();
            if (text.contains(mPrefix)) marker.mark(text.indexOf(mPrefix), mPrefix.length());
        }

        if (cc.timestamps.get() && cc.rainbowTimestamps.get()) {
            String lTime = cc.lBracketTimestamps.get();
            String rTime = cc.rBracketTimestamps.get();
            String timeStr = cc.timestampText().getString();
            int stampIndexBegin = text.indexOf(lTime);
            if (stampIndexBegin > -1 && lTime.isEmpty() && text.startsWith("  ")) stampIndexBegin = 2;
            if (stampIndexBegin > -1) {
                int min = Math.min(stampIndexBegin + timeStr.length() - 1, text.length());
                String time = text.substring(stampIndexBegin, min);
                if (time.startsWith(lTime) && time.endsWith(rTime)) {
                    marker.mark(stampIndexBegin, time.length());
                }
            }
        }

        if (cc.otherAddons.get()) {
            for (String addon : cc.addons.get()) {
                int idx = text.indexOf(addon);
                if (idx > -1) marker.mark(idx, addon.length());
            }
        }

        String selfName = (MinecraftClient.getInstance().player != null) ? Modules.get().get(NameProtect.class).getName(MinecraftClient.getInstance().player.getStringifiedName()) : null;
        if (cc.selfHighlight.get() && cc.selfHighlightRainbow.get() && selfName != null) {
            int idx = text.indexOf(selfName);
            while (idx > -1) {
                if (isExactMatch(text, selfName, idx)) marker.mark(idx, selfName.length());
                idx = text.indexOf(selfName, idx + 1);
            }
        }

        if (cc.friendHighlight.get() && cc.friendHighlightRainbow.get()) {
            for (Friend friend : Friends.get()) {
                int idx = text.indexOf(friend.name);
                while (idx > -1) {
                    if (isExactMatch(text, friend.name, idx)) marker.mark(idx, friend.name.length());
                    idx = text.indexOf(friend.name, idx + 1);
                }
            }
        }

        if (!needsRainbow[0]) return original;

        double speedMult = cc.rainbowSpeed.get();
        return TextUtils.create(original, hueOffsets, speedMult);
    }

    @Unique
    private boolean isExactMatch(String text, String word, int index) {
        if (index < 0) return false;
        boolean startOk = index == 0 || !isWordChar(text.charAt(index - 1));
        int endIdx = index + word.length();
        boolean endOk = endIdx == text.length() || !isWordChar(text.charAt(endIdx));
        return startOk && endOk;
    }

    @Unique
    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    @ModifyArg(
        method = "render(Lnet/minecraft/client/gui/hud/ChatHud$Backend;IIZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud$Backend;fill(IIIII)V"),
        index = 4
    )
    private int hideChatRectangles(int originalColor) {
        ChatControl cc = Modules.get().get(ChatControl.class);
        if (cc != null && cc.isActive() && cc.clear.get()) {
            return 0;
        }
        return originalColor;
    }
}
