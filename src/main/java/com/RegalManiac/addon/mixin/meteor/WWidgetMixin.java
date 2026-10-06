package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.utils.Config;
import com.RegalManiac.addon.utils.meteor.ModuleWidgetUtils;
import com.RegalManiac.addon.utils.sound.SoundUtils;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(value = WWidget.class, remap = false)
public abstract class WWidgetMixin {

    @Shadow public boolean mouseOver;
    @Shadow public double width;
    @Unique private boolean wasMouseOver = false;
    @Unique private String lastKeybindText = null;
    @Unique private Boolean lastShowKeybinds = null;
    @Unique private double appliedExtraWidth = 0;

    @Inject(method = "mouseMoved", at = @At("HEAD"))
    private void onMouseMovedHead(double mouseX, double mouseY, double lastMouseX, double lastMouseY, CallbackInfo ci) {
        this.wasMouseOver = this.mouseOver;
    }

    @Inject(method = "mouseMoved", at = @At("TAIL"))
    private void onMouseMovedTail(double mouseX, double mouseY, double lastMouseX, double lastMouseY, CallbackInfo ci) {
        SoundUtils.playWidgetHoverSound((WWidget) (Object) this, this.wasMouseOver, this.mouseOver, mouseX, mouseY, lastMouseX, lastMouseY);
    }

    @Inject(method = "calculateSize", at = @At("TAIL"))
    private void onCalculateSizeTail(CallbackInfo ci) {
        WWidget widget = (WWidget) (Object) this;
        if (widget instanceof WContainer) return;

        Module module = ModuleWidgetUtils.getModule(widget);
        if (module == null) {
            if (this.appliedExtraWidth != 0) {
                this.width -= this.appliedExtraWidth;
                ModuleWidgetUtils.updateTitleWidthField(widget, -this.appliedExtraWidth);
                this.appliedExtraWidth = 0;
            }
            return;
        }

        GuiTheme theme = ModuleWidgetUtils.getGuiTheme(widget);
        if (theme == null) return;

        boolean isAddonModule = ModuleWidgetUtils.isSindiModule(module);
        double skullWidth = isAddonModule ? ModuleWidgetUtils.VANILLA_TEXT_RENDERER.getWidth(ModuleWidgetUtils.SKULL_TEXT) * theme.scale(1) : 0;

        double keyWidth = 0;
        if (Config.showKeybinds != null && Config.showKeybinds.get() && module.keybind != null && module.keybind.isSet()) {
            String keyText = " [" + module.keybind.toString() + "]";
            keyWidth = theme.textWidth(keyText) * ModuleWidgetUtils.KEYBIND_SCALE;
        }

        double targetExtraWidth = skullWidth + keyWidth;
        double delta = targetExtraWidth - this.appliedExtraWidth;

        if (Math.abs(delta) > 0.0001) {
            this.width += delta;
            ModuleWidgetUtils.updateTitleWidthField(widget, delta);
            this.appliedExtraWidth = targetExtraWidth;
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(GuiRenderer renderer, double mouseX, double mouseY, double delta, CallbackInfoReturnable<Boolean> cir) {
        WWidget widget = (WWidget) (Object) this;
        if (widget instanceof WContainer) return;

        Module module = ModuleWidgetUtils.getModule(widget);
        if (module != null) {
            ModuleWidgetUtils.CURRENT_MODULE.set(module);
            boolean showKeybinds = Config.showKeybinds != null && Config.showKeybinds.get();
            String currentKeybind = (module.keybind != null && module.keybind.isSet()) ? module.keybind.toString() : "";
            if (this.lastKeybindText != null && (!Objects.equals(this.lastKeybindText, currentKeybind) || !Objects.equals(this.lastShowKeybinds, showKeybinds))) {
                widget.invalidate();
            }
            this.lastKeybindText = currentKeybind;
            this.lastShowKeybinds = showKeybinds;
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderTail(GuiRenderer renderer, double mouseX, double mouseY, double delta, CallbackInfoReturnable<Boolean> cir) {
        ModuleWidgetUtils.CURRENT_MODULE.remove();
    }
}
