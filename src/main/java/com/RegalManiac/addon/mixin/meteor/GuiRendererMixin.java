package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.utils.Config;
import com.RegalManiac.addon.utils.meteor.ModuleWidgetUtils;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiRenderer.class, remap = false)
public class GuiRendererMixin {

    @Inject(method = "text(Ljava/lang/String;DDLmeteordevelopment/meteorclient/utils/render/color/Color;Z)V", at = @At("HEAD"), cancellable = true)
    private void onText(String text, double x, double y, Color color, boolean shadow, CallbackInfo ci) {
        Module module = ModuleWidgetUtils.CURRENT_MODULE.get();
        if (module == null || !text.equals(module.title)) {
            return;
        }
        ci.cancel();

        GuiRenderer renderer = (GuiRenderer) (Object) this;
        GuiTheme theme = renderer.theme != null ? renderer.theme : ModuleWidgetUtils.getGuiTheme(null);

        boolean isAddonModule = ModuleWidgetUtils.isSindiModule(module);
        double skullWidth = (isAddonModule && theme != null) ? ModuleWidgetUtils.VANILLA_TEXT_RENDERER.getWidth(ModuleWidgetUtils.SKULL_TEXT) * theme.scale(1) : 0;
        double titleX = x + skullWidth;

        if (isAddonModule && theme != null) {
            Color skullColor = Config.skullColor != null ? Config.skullColor.get() : Color.WHITE;
            renderer.post(() -> {
                ModuleWidgetUtils.VANILLA_TEXT_RENDERER.begin(theme.scale(1));
                ModuleWidgetUtils.VANILLA_TEXT_RENDERER.render(ModuleWidgetUtils.SKULL_TEXT, x, y, skullColor, shadow);
                ModuleWidgetUtils.VANILLA_TEXT_RENDERER.end();
            });
        }

        renderer.post(() -> {
            renderer.theme.textRenderer().begin(renderer.theme.scale(1));
            renderer.theme.textRenderer().render(text, titleX, y, color, shadow);
            renderer.theme.textRenderer().end();
        });

        if (Config.showKeybinds != null && Config.showKeybinds.get() && module.keybind != null && module.keybind.isSet()) {
            String keyText = " [" + module.keybind.toString() + "]";
            double keyX = titleX + renderer.theme.textWidth(text);
            double yOffset = (renderer.theme.textHeight() * (1.0 - ModuleWidgetUtils.KEYBIND_SCALE)) / 2.0;
            double keyY = y + yOffset;
            renderer.post(() -> {
                renderer.theme.textRenderer().begin(renderer.theme.scale(ModuleWidgetUtils.KEYBIND_SCALE));
                renderer.theme.textRenderer().render(keyText, keyX, keyY, color, shadow);
                renderer.theme.textRenderer().end();
            });
        }
    }
}
