package com.RegalManiac.addon.modules.misc;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class WheelPicker extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgChat = settings.createGroup("Chat");
    private final SettingGroup sgSlots = settings.createGroup("Slot Actions");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Keybind> activationKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("activation-key")
        .description("Key to activate the wheel picker.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_V))
        .build());

    private final Setting<Integer> chatDelay = sgChat.add(new IntSetting.Builder()
        .name("chat-delay")
        .description("Delay in milliseconds between sending multiple commands or messages.")
        .defaultValue(500)
        .min(0)
        .max(5000)
        .sliderRange(0, 2000)
        .build());

    private final Setting<Integer> wheelRadius = sgGeneral.add(new IntSetting.Builder()
        .name("wheel-radius")
        .description("Radius of the wheel in pixels. Textures and hitboxes scale with this.")
        .defaultValue(150)
        .min(60)
        .max(1000)
        .sliderRange(60, 500)
        .build());

    private final Setting<Integer> wheelX = sgGeneral.add(new IntSetting.Builder()
        .name("wheel-x-offset")
        .description("X offset from center of screen (negative = left, positive = right).")
        .defaultValue(0)
        .min(-10000)
        .max(10000)
        .sliderRange(-1000, 1000)
        .build());

    private final Setting<Integer> wheelY = sgGeneral.add(new IntSetting.Builder()
        .name("wheel-y-offset")
        .description("Y offset from center of screen (negative = up, positive = down).")
        .defaultValue(0)
        .min(-10000)
        .max(10000)
        .sliderRange(-1000, 1000)
        .build());

    private final Setting<Boolean> showText = sgRender.add(new BoolSetting.Builder()
        .name("show-text")
        .description("Show text labels on the wheel.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> showIcons = sgRender.add(new BoolSetting.Builder()
        .name("show-icons")
        .description("Show item icons on the wheel.")
        .defaultValue(true)
        .build());

    private final Setting<Double> textScale = sgRender.add(new DoubleSetting.Builder()
        .name("text-scale")
        .description("Scale of the text labels.")
        .defaultValue(1.0)
        .min(0.1)
        .max(5.0)
        .sliderRange(0.1, 3.0)
        .build());

    private final Setting<Double> iconScale = sgRender.add(new DoubleSetting.Builder()
        .name("icon-scale")
        .description("Scale of the item icons.")
        .defaultValue(1.0)
        .min(0.1)
        .max(5.0)
        .sliderRange(0.1, 3.0)
        .build());

    private final Setting<SettingColor> textColor = sgRender.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Text color for labels.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build());

    private final Setting<SettingColor> moduleActiveColor = sgRender.add(new ColorSetting.Builder()
        .name("module-active-color")
        .description("Text color for active modules.")
        .defaultValue(new SettingColor(100, 255, 100, 255))
        .build());

    private final Setting<Double> textureOpacity = sgRender.add(new DoubleSetting.Builder()
        .name("wheel-opacity")
        .description("Global opacity for the wheel textures.")
        .defaultValue(0.85)
        .min(0.1)
        .max(1.0)
        .sliderRange(0.1, 1.0)
        .build());


    private final SlotConfig[] slots = new SlotConfig[8];
    private boolean wheelActive = false;
    private int selectedSlot = -1;
    private boolean wasGrabbed = false;

    private final Queue<QueuedAction> actionQueue = new ArrayDeque<>();
    private long lastActionTime = 0;

    private static final String[] SLOT_NAMES = {
        "Top", "Top-Right", "Right", "Bottom-Right",
        "Bottom", "Bottom-Left", "Left", "Top-Left"
    };

    public WheelPicker() {
        super(Categories.Misc, "wheel-picker", "GTA-style wheel menu for quick macros and actions.");
        for (int i = 0; i < 8; i++) {
            slots[i] = new SlotConfig(i);
        }
    }

    @Override
    public void onActivate() {
        wheelActive = false;
        selectedSlot = -1;
        actionQueue.clear();
    }

    @Override
    public void onDeactivate() {
        wheelActive = false;
        selectedSlot = -1;
        actionQueue.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (!actionQueue.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastActionTime >= chatDelay.get()) {
                QueuedAction action = actionQueue.poll();
                if (action != null && mc.player.networkHandler != null) {
                    if (action.type == QueuedType.MESSAGE) {
                        mc.player.networkHandler.sendChatMessage(action.value);
                    } else if (action.type == QueuedType.COMMAND) {
                        mc.player.networkHandler.sendChatCommand(action.value);
                    }
                    lastActionTime = currentTime;
                }
            }
        }

        if (mc.currentScreen != null) {
            wheelActive = false;
            return;
        }

        boolean keyPressed = activationKey.get().isPressed();
        if (keyPressed && !wheelActive) {
            wheelActive = true;
            selectedSlot = -1;
            wasGrabbed = mc.mouse.isCursorLocked();
            if (wasGrabbed) {
                mc.mouse.unlockCursor();
            }
            GLFW.glfwSetCursorPos(mc.getWindow().getHandle(),
                mc.getWindow().getWidth() / 2.0,
                mc.getWindow().getHeight() / 2.0);
        } else if (!keyPressed && wheelActive) {
            if (selectedSlot >= 0 && selectedSlot < 8) {
                executeSlotAction(selectedSlot);
            }
            if (wasGrabbed) {
                mc.mouse.lockCursor();
            }
            wheelActive = false;
        }

        if (wheelActive) {
            updateSelectedSlot();
        }
    }

    private void updateSelectedSlot() {
        int scaledWidth = mc.getWindow().getScaledWidth();
        int scaledHeight = mc.getWindow().getScaledHeight();
        double mouseX = mc.mouse.getX() * scaledWidth / (double)mc.getWindow().getWidth();
        double mouseY = mc.mouse.getY() * scaledHeight / (double)mc.getWindow().getHeight();
        double centerX = scaledWidth / 2.0;
        double centerY = scaledHeight / 2.0;

        double deltaX = mouseX - (centerX + wheelX.get());
        double deltaY = mouseY - (centerY + wheelY.get());
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

        if (distance < 20 || distance > wheelRadius.get()) {
            selectedSlot = -1;
            return;
        }

        double angle = Math.atan2(deltaY, deltaX);
        double degrees = Math.toDegrees(angle);
        if (degrees < 0) degrees += 360;
        degrees = (degrees + 90) % 360;
        selectedSlot = (int)((degrees + 22.5) / 45) % 8;
    }

    private void executeSlotAction(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 8) return;
        SlotConfig slot = slots[slotIndex];
        MacroAction action = slot.action.get();
        if (action == MacroAction.NONE) return;

        switch (action) {
            case TOGGLE_MODULE -> {
                List<String> moduleNames = slot.modules.get();
                for (String moduleName : moduleNames) {
                    if (moduleName.isEmpty()) continue;
                    Module module = Modules.get().get(moduleName);
                    if (module != null) {
                        module.toggle();
                        module.sendToggledMsg();
                    } else {
                        warning("Module not found: " + moduleName);
                    }
                }
            }
            case SEND_MESSAGE -> {
                List<String> messages = slot.messages.get();
                for (String msg : messages) {
                    if (!msg.isEmpty()) {
                        actionQueue.add(new QueuedAction(QueuedType.MESSAGE, msg));
                    }
                }
            }
            case RUN_COMMAND -> {
                List<String> commands = slot.commands.get();
                for (String cmd : commands) {
                    if (!cmd.isEmpty()) {
                        actionQueue.add(new QueuedAction(QueuedType.COMMAND, cmd));
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!wheelActive) return;
        DrawContext context = event.drawContext;
        int scaledWidth = mc.getWindow().getScaledWidth();
        int scaledHeight = mc.getWindow().getScaledHeight();
        int centerX = scaledWidth / 2 + wheelX.get();
        int centerY = scaledHeight / 2 + wheelY.get();
        int radius = wheelRadius.get();
        renderWheel(context, centerX, centerY, radius);
    }

    private void renderWheel(DrawContext context, int centerX, int centerY, int radius) {
        int size = radius * 2;
        int startX = centerX - radius;
        int startY = centerY - radius;
        int alpha = (int) (textureOpacity.get() * 255.0) & 0xFF;
        int color = (alpha << 24) | 0x00FFFFFF;

        Identifier baseTexture = Identifier.of("sindiaddon", "textures/wheel/" + "/base.png");

        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            baseTexture,
            startX, startY,
            0.0F, 0.0F,
            size, size,
            size, size,
            color
        );

        if (selectedSlot >= 0 && selectedSlot < 8) {
            Identifier sectorTexture = Identifier.of("sindiaddon", "textures/wheel/" + "/sector_" + selectedSlot + ".png");
            context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                sectorTexture,
                startX, startY,
                0.0F, 0.0F,
                size, size,
                size, size,
                color
            );
        }

        for (int i = 0; i < 8; i++) {
            drawSectionLabel(context, centerX, centerY, radius, i);
        }
    }

    private void drawSectionLabel(DrawContext context, int centerX, int centerY, int radius, int sectionIndex) {
        SlotConfig slot = slots[sectionIndex];
        double midAngle = Math.toRadians(sectionIndex * 45 - 90);
        int labelRadius = radius * 2 / 3;
        int labelX = centerX + (int)(Math.cos(midAngle) * labelRadius);
        int labelY = centerY + (int)(Math.sin(midAngle) * labelRadius);

        boolean isModuleActive = false;
        if (slot.action.get() == MacroAction.TOGGLE_MODULE) {
            for (String modName : slot.modules.get()) {
                Module m = Modules.get().get(modName);
                if (m != null && m.isActive()) {
                    isModuleActive = true;
                    break;
                }
            }
        }

        boolean hasIcon = showIcons.get() && slot.icon.get() != Items.AIR;
        String label = getSlotLabel(slot);
        boolean hasText = showText.get() && !label.isEmpty();

        if (!hasIcon && !hasText) return;

        float iconScaleValue = iconScale.get().floatValue();
        float textScaleValue = textScale.get().floatValue();
        int iconSize = (int)(16 * iconScaleValue);
        int spacing = 2;
        int textHeight = (int)(mc.textRenderer.fontHeight * textScaleValue);
        int totalHeight = 0;

        if (hasIcon) totalHeight += iconSize;
        if (hasIcon && hasText) totalHeight += spacing;
        if (hasText) totalHeight += textHeight;

        int currentY = labelY - totalHeight / 2;

        if (hasIcon) {
            Item item = slot.icon.get();
            ItemStack stack = new ItemStack(item);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(labelX, currentY);
            context.getMatrices().scale(iconScaleValue, iconScaleValue);
            context.drawItem(stack, -8, 0);
            context.getMatrices().popMatrix();
            currentY += iconSize + spacing;
        }

        if (hasText) {
            Color textColor = isModuleActive ? moduleActiveColor.get() : this.textColor.get();
            int textWidth = mc.textRenderer.getWidth(label);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(labelX, currentY);
            context.getMatrices().scale(textScaleValue, textScaleValue);
            context.drawText(mc.textRenderer, label,
                -textWidth / 2,
                0,
                textColor.getPacked(), false);
            context.getMatrices().popMatrix();
        }
    }

    private String getSlotLabel(SlotConfig slot) {
        MacroAction action = slot.action.get();
        if (action == MacroAction.NONE) return "";
        String custom = slot.customText.get();
        if (!custom.isEmpty()) return custom;

        switch (action) {
            case TOGGLE_MODULE -> {
                List<String> mods = slot.modules.get();
                if (mods.isEmpty()) return "";
                if (mods.size() == 1) {
                    String module = mods.getFirst();
                    Module m = Modules.get().get(module);
                    String state = (m != null && m.isActive()) ? " ✓" : "";
                    return (module.length() > 8 ? module.substring(0, 8) : module) + state;
                }
                return mods.size() + " Modules";
            }
            case SEND_MESSAGE -> {
                List<String> msgs = slot.messages.get();
                if (msgs.isEmpty()) return "";
                if (msgs.size() == 1) {
                    String msg = msgs.getFirst();
                    return msg.length() > 10 ? msg.substring(0, 8) + ".." : msg;
                }
                return msgs.size() + " Msgs";
            }
            case RUN_COMMAND -> {
                List<String> cmds = slot.commands.get();
                if (cmds.isEmpty()) return "";
                if (cmds.size() == 1) {
                    String cmd = cmds.getFirst();
                    return "/" + (cmd.length() > 9 ? cmd.substring(0, 7) + ".." : cmd);
                }
                return cmds.size() + " Cmds";
            }
            default -> {
                return "";
            }
        }
    }

    private class SlotConfig {
        public final Setting<MacroAction> action;
        public final Setting<List<String>> modules;
        public final Setting<List<String>> messages;
        public final Setting<List<String>> commands;
        public final Setting<String> customText;
        public final Setting<Item> icon;

        public SlotConfig(int index) {
            String slotName = SLOT_NAMES[index];
            String prefix = slotName.toLowerCase().replace("-", "");

            action = sgSlots.add(new EnumSetting.Builder<MacroAction>()
                .name(prefix + "-action")
                .description("Action for " + slotName + " slot")
                .defaultValue(MacroAction.NONE)
                .build());

            icon = sgSlots.add(new ItemSetting.Builder()
                .name(prefix + "-icon")
                .description("Icon item for " + slotName + " slot")
                .defaultValue(Items.AIR)
                .visible(() -> action.get() != MacroAction.NONE)
                .build());

            customText = sgSlots.add(new StringSetting.Builder()
                .name(prefix + "-custom-text")
                .description("Custom display text for " + slotName + " slot (leave empty for auto)")
                .defaultValue("")
                .visible(() -> action.get() != MacroAction.NONE)
                .build());

            modules = sgSlots.add(new StringListSetting.Builder()
                .name(prefix + "-modules")
                .description("Module names to toggle for " + slotName)
                .defaultValue(new ArrayList<>())
                .visible(() -> action.get() == MacroAction.TOGGLE_MODULE)
                .build());

            messages = sgSlots.add(new StringListSetting.Builder()
                .name(prefix + "-messages")
                .description("Messages to send for " + slotName)
                .defaultValue(new ArrayList<>())
                .visible(() -> action.get() == MacroAction.SEND_MESSAGE)
                .build());

            commands = sgSlots.add(new StringListSetting.Builder()
                .name(prefix + "-commands")
                .description("Commands to run for " + slotName + " (without /)")
                .defaultValue(new ArrayList<>())
                .visible(() -> action.get() == MacroAction.RUN_COMMAND)
                .build());
        }
    }

    private enum QueuedType {
        MESSAGE,
        COMMAND
    }

    private record QueuedAction(QueuedType type, String value) {
    }

    public enum MacroAction {
        NONE("None"),
        TOGGLE_MODULE("Toggle Module"),
        SEND_MESSAGE("Send Message"),
        RUN_COMMAND("Run Command");

        private final String name;

        MacroAction(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
