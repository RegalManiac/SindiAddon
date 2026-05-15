package com.RegalManiac.addon.mixin.sodium;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;


@Mixin(OcclusionCuller.class)
public class OcclusionCullerMixin {

    /**
     * @author FastPlays08
     * @reason Fixes sodium occlusion
     */
    @Overwrite
    private static boolean isWithinRenderDistance(CameraTransform camera, RenderSection section, float maxDistance) {
        int ox = section.getOriginX() - camera.intX;
        int oy = section.getOriginY() - camera.intY;
        int oz = section.getOriginZ() - camera.intZ;

        float dx = nearestToZero(ox - 1, ox + 17) - camera.fracX;
        float dy = nearestToZero(oy - 1, oy + 17) - camera.fracY;
        float dz = nearestToZero(oz - 1, oz + 17) - camera.fracZ;

        if (SodiumClientMod.options().performance.useFogOcclusion) {
            return (((dx * dx) + (dz * dz)) < (maxDistance * maxDistance)) && (Math.abs(dy) < maxDistance);
        } else {
            return (dx * dx + dz * dz) < (maxDistance * maxDistance);
        }
    }

    @SuppressWarnings("ManualMinMaxCalculation")
    private static int nearestToZero(int min, int max) {
        int clamped = 0;
        if (min > 0) { clamped = min; }
        if (max < 0) { clamped = max; }
        return clamped;
    }
}
