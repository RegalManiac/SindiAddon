package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.utils.ShulkerRenderUtils;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.render.BetterTooltips;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BetterTooltips.class, remap = false)
public abstract class BetterTooltipsMixin {

    @Unique public Setting<Boolean> shulkerOverview;
    @Unique public Setting<Integer> shulkerIconSize;
    @Unique public Setting<ShulkerRenderUtils.IconPosition> shulkerPos;
    @Unique public Setting<Boolean> showMultiple;
    @Unique public Setting<String> shulkerMultipleText;
    @Unique public Setting<Integer> shulkerMultipleSize;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        BetterTooltips module = (BetterTooltips) (Object) this;

        SettingGroup sgShulkerOverview = module.settings.createGroup("Shulker Overview");

        shulkerOverview = sgShulkerOverview.add(new BoolSetting.Builder()
            .name("show-icons")
            .description("Overlays the most common item icon on shulker boxes.")
            .defaultValue(true)
            .build()
        );

        shulkerIconSize = sgShulkerOverview.add(new IntSetting.Builder()
            .name("icon-size")
            .description("Size of the item icon overlay.")
            .defaultValue(12)
            .min(4)
            .max(16)
            .sliderMin(4)
            .sliderMax(16)
            .visible(shulkerOverview::get)
            .build()
        );

        shulkerPos = sgShulkerOverview.add(new EnumSetting.Builder<ShulkerRenderUtils.IconPosition>()
            .name("icon-position")
            .description("Where to place the icon on the slot.")
            .defaultValue(ShulkerRenderUtils.IconPosition.Center)
            .visible(shulkerOverview::get)
            .build()
        );

        showMultiple = sgShulkerOverview.add(new BoolSetting.Builder()
            .name("show-multiple")
            .description("Whether to show the multiple item indicator (+).")
            .defaultValue(true)
            .visible(shulkerOverview::get)
            .build()
        );

        shulkerMultipleText = sgShulkerOverview.add(new StringSetting.Builder()
            .name("multiple-indicator")
            .description("Text to show when shulker contains multiple item types.")
            .defaultValue("+")
            .visible(shulkerOverview::get)
            .build()
        );

        shulkerMultipleSize = sgShulkerOverview.add(new IntSetting.Builder()
            .name("multiple-size")
            .description("Size of the multiple indicator text.")
            .defaultValue(8)
            .min(4)
            .max(16)
            .visible(shulkerOverview::get)
            .build()
        );
    }
}
