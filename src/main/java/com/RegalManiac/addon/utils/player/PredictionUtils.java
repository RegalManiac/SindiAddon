package com.RegalManiac.addon.utils.player;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.concurrent.ThreadLocalRandom;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class PredictionUtils {

    public static Vec3d getAimPoint(Entity target, boolean predictMovement) {
        if (target == null || mc.player == null) return null;

        Box box = target.getBoundingBox();
        Box aimBox = predictMovement ? box.offset(target.getVelocity()) : box;
        Vec3d eye = mc.player.getEyePos();

        Vec3d closest = new Vec3d(
            MathHelper.clamp(eye.x, aimBox.minX, aimBox.maxX),
            MathHelper.clamp(eye.y, aimBox.minY, aimBox.maxY),
            MathHelper.clamp(eye.z, aimBox.minZ, aimBox.maxZ)
        );

        closest = closest.lerp(aimBox.getCenter(), 0.1 + 0.15 * ThreadLocalRandom.current().nextDouble());

        return new Vec3d(
            clampInside(closest.x, box.minX, box.maxX),
            clampInside(closest.y, box.minY, box.maxY),
            clampInside(closest.z, box.minZ, box.maxZ)
        );
    }

    private static double clampInside(double v, double lo, double hi) {
        double inset = Math.min(0.05, (hi - lo) * 0.25);
        return MathHelper.clamp(v, lo + inset, hi - inset);
    }
}
