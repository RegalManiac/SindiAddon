package com.RegalManiac.addon.mixin.mixins;

import com.RegalManiac.addon.modules.combat.OffhandPlus;
import com.RegalManiac.addon.utils.ShulkerRenderUtils;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow @Final private MinecraftClient client;

    @Unique
    private static final Identifier OFFHAND_LEFT = Identifier.ofVanilla("hud/hotbar_offhand_left");

    @Unique
    private static final Identifier OFFHAND_RIGHT = Identifier.ofVanilla("hud/hotbar_offhand_right");

    @Unique
    private static final Identifier SELECTION = Identifier.ofVanilla("hud/hotbar_selection");

    @Inject(method = "renderHotbarItem", at = @At("HEAD"), cancellable = true)
    private void hideVanillaOffhand(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        OffhandPlus module = Modules.get().get(OffhandPlus.class);
        if (module != null && module.isActive() && module.isSwapKeyHeld()) {
            int center = context.getScaledWindowWidth() / 2;
            if (x < center - 90 || x > center + 90) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void renderCustomOffhand(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        OffhandPlus module = Modules.get().get(OffhandPlus.class);
        if (module == null || !module.isActive() || !module.isSwapKeyHeld()) return;

        int center = context.getScaledWindowWidth() / 2;
        int y = context.getScaledWindowHeight() - 23;
        Arm arm = client.player.getMainArm().getOpposite();

        int step = 21;
        int slotCount = 3;

        int activeSlots = 0;
        for (int i = 0; i < 3; i++) {
            if (!module.getBindStack(i).isEmpty()) {
                activeSlots++;
            }
        }
        if (activeSlots == 0) return;

        int baseOffhandX = (arm == Arm.LEFT) ? (center - 91 - 29) : (center + 91);
        int startX = (arm == Arm.LEFT) ? (baseOffhandX - ((activeSlots - 1) * step)) : baseOffhandX;

        int drawIdx = 0;
        for (int i = 0; i < 3; i++) {
            ItemStack stack = module.getBindStack(i);
            if (stack.isEmpty()) continue;

            int x = startX + (drawIdx * step);

            if (x != baseOffhandX) {
                Identifier bg = (arm == Arm.LEFT) ? OFFHAND_LEFT : OFFHAND_RIGHT;
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, bg, x, y, 29, 24);
            }
            drawIdx++;
        }

        drawIdx = 0;
        for (int i = 0; i < 3; i++) {
            ItemStack stack = module.getBindStack(i);
            if (stack.isEmpty()) continue;

            if (module.getCurrentSlot() == i) {
                int x = startX + (drawIdx * step);
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SELECTION, x - 1, y, 24, 23);
            }
            drawIdx++;
        }

        drawIdx = 0;
        for (int i = 0; i < 3; i++) {
            ItemStack stack = module.getBindStack(i);
            if (stack.isEmpty()) continue;

            int x = startX + (drawIdx * step);

            context.drawItem(stack, x + 3, y + 4);
            context.drawStackOverlay(client.textRenderer, stack, x + 3, y + 4);
            drawIdx++;
        }
    }

    // Shulker Overview method
    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void onRenderHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        OffhandPlus offhandModule = Modules.get().get(OffhandPlus.class);
        if (offhandModule != null && offhandModule.isActive() && offhandModule.isSwapKeyHeld()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;
        int scaledWidth = mc.getWindow().getScaledWidth();
        int scaledHeight = mc.getWindow().getScaledHeight();
        int center = scaledWidth / 2;
        int hotbarY = scaledHeight - 19;
        for (int i = 0; i < 9; i++) {
            int posX = center - 90 + i * 20 + 2;
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock))
                continue;
            ShulkerRenderUtils.renderShulkerOverlay(context, posX, hotbarY, stack);
        }

        ItemStack offhandStack = player.getOffHandStack();
        if (!offhandStack.isEmpty() && offhandStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            int offY = scaledHeight - 23;
            int offX;
            if (player.getMainArm() == Arm.LEFT) {
                offX = center + 91 + 9;
            } else {
                offX = center - 91 - 29;
            }
            ShulkerRenderUtils.renderShulkerOverlay(context, offX + 3, offY + 3, offhandStack);
        }
    }
}
