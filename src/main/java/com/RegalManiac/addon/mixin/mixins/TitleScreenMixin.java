package com.RegalManiac.addon.mixin.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Unique
    private static final int TOTAL_FRAMES = 300;
    @Unique
    private static final int FPS = 60;
    @Unique
    private static final Identifier[] FRAMES = new Identifier[TOTAL_FRAMES];

    @Unique
    private static long sessionStartTime = -1;

    static {
        for (int i = 0; i < TOTAL_FRAMES; i++) {
            String fileName = String.format("%03d", i);
            FRAMES[i] = Identifier.of("sindiaddon", "animations/title/" + fileName + ".png");
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderRunningPlayer(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        long currentTime = Util.getMeasuringTimeMs();

        if (sessionStartTime == -1) {
            sessionStartTime = currentTime;
        }

        long elapsed = currentTime - sessionStartTime;
        int currentFrame = (int) (elapsed / (1000 / FPS));

        if (currentFrame >= TOTAL_FRAMES) {
            return;
        }

        Identifier texture = FRAMES[currentFrame];

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int drawSize = 100;
        int margin = 15;
        int x1 = screenWidth - drawSize - margin;
        int y1 = screenHeight - drawSize - margin;
        int x2 = x1 + drawSize;
        int y2 = y1 + drawSize;

        context.drawTexturedQuad(texture, x1, y1, x2, y2, 0.0f, 1.0f, 0.0f, 1.0f);
    }
}
