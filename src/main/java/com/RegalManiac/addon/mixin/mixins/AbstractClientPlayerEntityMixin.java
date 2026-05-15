package com.RegalManiac.addon.mixin.mixins;

import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class AbstractClientPlayerEntityMixin {

    @Unique
    private static final Identifier CUSTOM_SKIN_ID = Identifier.of("sindiaddon", "textures/player/skin.png");

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void onGetSkin(CallbackInfoReturnable<SkinTextures> info) {
        if ((Object) this instanceof FakePlayerEntity) {

            AssetInfo.TextureAsset skinAsset = new AssetInfo.SkinAssetInfo(CUSTOM_SKIN_ID, "");

            info.setReturnValue(new SkinTextures(
                skinAsset,
                null,
                null,
                PlayerSkinType.WIDE,
                true
            ));
        }
    }
}
