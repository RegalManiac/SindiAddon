package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.utils.render.TooltipsUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.RegalManiac.addon.utils.render.TooltipsUtils.convertToSubscript;

@Mixin(MerchantScreen.class)
public class MerchantScreenMixin {

    @Inject(method = {"renderArrow"}, at = {@At("HEAD")})
    private void onRenderArrow(DrawContext context, TradeOffer tradeOffer, int x, int y, CallbackInfo ci) {
        if (!TooltipsUtils.isVillagerDemandEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer textRenderer = mc.textRenderer;
        int demand = tradeOffer.getDemandBonus();
        int color = demand < 0 ? -16717456 : (demand == 0 ? -1585120 : -2670024);
        String text = convertToSubscript(String.valueOf(demand));
        context.drawText(textRenderer, text, x + 64 - textRenderer.getWidth(text) / 2, y + 10, color, false);
    }
}
