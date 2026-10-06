package com.RegalManiac.addon.utils.text;

import com.RegalManiac.addon.modules.world.SignScanner;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import static meteordevelopment.meteorclient.MeteorClient.mc;

import java.util.*;

public class TextRenderUtils {
    private final Vector3d tempVec = new Vector3d();
    private final List<SignRenderData> visibleSigns = new ArrayList<>();
    private final List<AABB> occupiedSpaces = new ArrayList<>();
    private final List<RenderJob> renderJobs = new ArrayList<>();

    private long lastFrameTime = System.currentTimeMillis();
    private final Map<Vec3d, Double> alphaMap = new HashMap<>();
    private final Set<Vec3d> currentFramePositions = new HashSet<>();

    private final Color backgroundQuadColor = new Color();
    private final Color textRenderColor = new Color();

    public void render(SignScanner module, List<SignRenderData> allSigns) {
        if (mc.player == null) return;
        Vec3d playerPos = mc.player.getEntityPos();

        long currentTime = System.currentTimeMillis();
        double dt = (currentTime - lastFrameTime) / 1000.0;
        lastFrameTime = currentTime;

        double fadeSpeed = 3.5;

        visibleSigns.clear();
        for (SignRenderData sign : allSigns) {
            sign.distance = playerPos.distanceTo(sign.worldPos);
            sign.updateScreenPosition(tempVec);
            if (sign.onScreen) visibleSigns.add(sign);
        }

        if (visibleSigns.isEmpty()) return;

        visibleSigns.sort(Comparator.comparingDouble(s -> s.distance));

        TextRenderer textRenderer = TextRenderer.get();
        double baseLineHeight = textRenderer.getHeight();

        occupiedSpaces.clear();
        renderJobs.clear();
        currentFramePositions.clear();

        for (SignRenderData sign : visibleSigns) {
            currentFramePositions.add(sign.worldPos);

            double distFactor = Math.max(1.0, sign.distance / 15.0);
            double scale = module.baseScale.get() * (1.0 / distFactor);
            scale = Math.max(0.3, Math.min(module.baseScale.get(), scale));

            boolean multi = module.multilineDisplay.get() && !sign.lines.isEmpty();
            int lineCount = multi ? sign.lines.size() : 1;
            double maxW = 0;
            double[] widths = new double[lineCount];

            for (int i = 0; i < lineCount; i++) {
                String text = multi ? sign.lines.get(i) : sign.fullText;
                double w = text.isEmpty() ? 0 : textRenderer.getWidth(text);
                widths[i] = w;
                maxW = Math.max(maxW, w);
            }

            double scaledW = (maxW + 8.0) * scale;
            double scaledH = (lineCount * baseLineHeight + 8.0) * scale;

            double baseX = sign.screenX - (scaledW / 2.0);
            double baseY = sign.screenY - (scaledH / 2.0);

            AABB box = new AABB(baseX, baseY, scaledW, scaledH);

            boolean occluded = module.preventOverlap.get() && collides(box, occupiedSpaces);
            double targetAlpha = occluded ? 0.0 : 1.0;

            double currentAlpha = alphaMap.getOrDefault(sign.worldPos, 0.0);

            if (targetAlpha == 1.0) {
                currentAlpha = Math.min(1.0, currentAlpha + (fadeSpeed * dt));
                occupiedSpaces.add(new AABB(box.x - 1, box.y - 1, box.w + 2, box.h + 2));
            } else {
                currentAlpha = Math.max(0.0, currentAlpha - (fadeSpeed * dt));
            }

            alphaMap.put(sign.worldPos, currentAlpha);

            if (currentAlpha > 0.01) {
                renderJobs.add(new RenderJob(sign, box.x, box.y, box.w, box.h, scale, widths, multi, currentAlpha));
            }
        }

        alphaMap.keySet().retainAll(currentFramePositions);

        if (module.showBackground.get()) {
            Renderer2D.COLOR.begin();
            SettingColor bgCol = module.backgroundColor.get();

            for (RenderJob job : renderJobs) {
                backgroundQuadColor.set(bgCol.r, bgCol.g, bgCol.b, (int)(bgCol.a * job.alpha));
                Renderer2D.COLOR.quad(job.x, job.y, job.w, job.h, backgroundQuadColor);
            }
            Renderer2D.COLOR.render();
        }

        renderJobs.sort(Comparator.comparingDouble(j -> j.scale));
        double currentScale = -1;
        SettingColor baseTextCol = module.textColor.get();

        for (RenderJob job : renderJobs) {
            if (job.scale != currentScale) {
                if (textRenderer.isBuilding()) textRenderer.end();
                textRenderer.begin(job.scale, false, true);
                currentScale = job.scale;
            }

            textRenderColor.set(baseTextCol.r, baseTextCol.g, baseTextCol.b, (int)(baseTextCol.a * job.alpha));

            double startY = job.y + (4.0 * job.scale);
            int count = job.multi ? job.sign.lines.size() : 1;

            for (int i = 0; i < count; i++) {
                String text = job.multi ? job.sign.lines.get(i) : job.sign.fullText;
                if (!text.isEmpty()) {
                    double startX = job.x + (job.w / 2.0) - ((job.lineWidths[i] * job.scale) / 2.0);
                    textRenderer.render(text, startX, startY + (i * baseLineHeight * job.scale), textRenderColor);
                }
            }
        }
        if (textRenderer.isBuilding()) textRenderer.end();
    }

    private boolean collides(AABB box, List<AABB> others) {
        for (AABB other : others) {
            if (box.intersects(other)) return true;
        }
        return false;
    }

    private static class AABB {
        double x, y, w, h;
        AABB(double x, double y, double w, double h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        boolean intersects(AABB o) {
            return x < o.x + o.w && x + w > o.x && y < o.y + o.h && y + h > o.y;
        }
    }

    private static class RenderJob {
        SignRenderData sign;
        double x, y, w, h, scale, alpha;
        double[] lineWidths;
        boolean multi;

        RenderJob(SignRenderData sign, double x, double y, double w, double h, double scale, double[] lineWidths, boolean multi, double alpha) {
            this.sign = sign; this.x = x; this.y = y; this.w = w; this.h = h;
            this.scale = scale; this.lineWidths = lineWidths; this.multi = multi; this.alpha = alpha;
        }
    }

    public static class SignRenderData {
        public final List<String> lines;
        public final String fullText;
        public final Vec3d worldPos;
        public final BlockPos pos;
        public double distance, screenX, screenY;
        public boolean onScreen = false;

        public SignRenderData(List<String> lines, String fullText, Vec3d worldPos, BlockPos pos) {
            this.lines = lines;
            this.fullText = fullText;
            this.worldPos = worldPos;
            this.pos = pos;
        }

        public void updateScreenPosition(Vector3d t) {
            t.set(worldPos.x, worldPos.y + 0.5, worldPos.z);
            if (NametagUtils.to2D(t, 1.0)) {
                screenX = t.x; screenY = t.y; onScreen = true;
            } else onScreen = false;
        }
    }
}
