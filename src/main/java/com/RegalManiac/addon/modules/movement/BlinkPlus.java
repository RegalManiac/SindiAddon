package com.RegalManiac.addon.modules.movement;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class BlinkPlus extends Module {
    public enum BlinkMode {
        Damage,
        Normal
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<BlinkMode> blinkMode = sgGeneral.add(new EnumSetting.Builder<BlinkMode>()
        .name("blink-mode")
        .description("The condition under which packets are delayed.")
        .defaultValue(BlinkMode.Normal)
        .build()
    );

    private final Setting<Integer> packets = sgGeneral.add(new IntSetting.Builder()
        .name("max-packets")
        .description("Auto-disables after accumulating this many packets (0 - no limit).")
        .defaultValue(10)
        .min(0).max(100)
        .build()
    );

    private final Setting<Integer> ticks = sgGeneral.add(new IntSetting.Builder()
        .name("max-ticks")
        .description("Maximum duration of operation in ticks.")
        .defaultValue(100)
        .min(0).max(500)
        .build()
    );

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("render-ghost")
        .description("Renders a box at your server-side position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("render-shape")
        .description("The render style of the ghost player.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The color of the outline.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The color of the sides.")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );

    private int delayedPackets = 0;
    private int timer = 0;
    private Box ghostBox = null;

    public BlinkPlus() {
        super(Categories.Movement, "blink-+", "Delays movement packets to simulate lag.");
    }

    @Override
    public void onActivate() {
        delayedPackets = 0;
        timer = 0;
        if (mc.player != null) {
            Vec3d pos = mc.player.getEntityPos();
            double w = mc.player.getWidth() / 2.0;
            double h = mc.player.getHeight();
            ghostBox = new Box(pos.x - w, pos.y, pos.z - w, pos.x + w, pos.y + h, pos.z + w);
        }
    }

    @Override
    public String getInfoString() {
        return delayedPackets + " / " + packets.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        timer++;

        if (ticks.get() > 0 && timer > ticks.get()) {
            toggle();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (render.get() && ghostBox != null) {
            event.renderer.box(ghostBox, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    public boolean onSend() {
        if (!shouldDelay()) return false;
        delayedPackets++;
        if (packets.get() > 0 && delayedPackets >= packets.get()) {
            if (blinkMode.get() == BlinkMode.Normal) {
                mc.execute(this::toggle);
            }
        }
        return true;
    }

    public boolean shouldDelay() {
        if (!isActive() || mc.player == null || mc.world == null) return false;

        if (blinkMode.get() == BlinkMode.Damage) {
            return mc.player.hurtTime > 0;
        }
        return true;
    }
}
