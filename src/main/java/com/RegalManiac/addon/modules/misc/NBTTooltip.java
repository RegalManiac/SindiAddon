package com.RegalManiac.addon.modules.misc;

import com.google.common.base.Strings;
import com.mojang.serialization.DataResult;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.component.ComponentChanges;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import org.lwjgl.glfw.GLFW;

/**
 * made by Satellite dev
 */
public class NBTTooltip extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> indentationLevel = sgGeneral.add(new IntSetting.Builder()
        .name("indentation-level")
        .description("The level of indentation for NBT data.")
        .min(0)
        .sliderMax(4)
        .defaultValue(2)
        .build()
    );

    private final Setting<Boolean> color = sgGeneral.add(new BoolSetting.Builder()
        .name("color")
        .description("Colorize NBT data.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyOnKey = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-key")
        .description("Only show NBT data when CTRL is held.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Keybind> displayKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("display-key")
        .description("The key to press to display the nbt tooltip.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_LEFT_CONTROL))
        .visible(onlyOnKey::get)
        .build()
    );

    private final Setting<Keybind> copyKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("copy-key")
        .description("The key to press to copy the NBT into the clipboard.")
        .defaultValue(Keybind.fromKeys(67, 2)) // Ctrl + C
        .build()
    );

    public NBTTooltip() {
        super(Categories.Misc, "nbt-tooltip", "Shows NBT data in item tooltips.");
    }



    public String getTooltip(ItemStack stack, Item.TooltipContext ctx) {

        if (isActive() && (!onlyOnKey.get() || displayKey.get().isPressed())) {
            DataResult<NbtElement> result = ComponentChanges.CODEC.encodeStart(ctx.getRegistryLookup().getOps(NbtOps.INSTANCE), stack.getComponentChanges());
            result.ifError(e->{});
            NbtElement nbtElement = result.getOrThrow();
            NbtCompound compound = (NbtCompound) nbtElement;

            if (copyKey.get().isPressed()) {
                mc.keyboard.setClipboard(formatNBT(compound, indentationLevel.get(), false));
            }

            return formatNBT(compound, indentationLevel.get(), color.get());
        }

        return null;
    }

    public static String formatNBT(NbtCompound compound, int currentIndentation, int indentationLevel, boolean colors) {
        String keyColor = "\u00a7b";
        String bracketColor = "\u00a7f";
        String resetColor = "\u00a7r";
        if (!colors) {
            keyColor = "";
            bracketColor = "";
            resetColor = "";
        }
        StringBuilder result = new StringBuilder(bracketColor + "{" + resetColor);

        String indentation = Strings.repeat(" ", currentIndentation);

        for (String key : compound.getKeys()) {
            NbtElement tag = compound.get(key);
            result.append("\n").append(indentation).append(keyColor).append(key).append(resetColor).append(": ");

            handleTag(currentIndentation, indentationLevel, colors, result, tag);
        }

        if (result.charAt(result.length() - 1) == ',') {
            result.setCharAt(result.length() - 1, ' ');
        }

        for (int i = result.length(); i < 0; i++) {
            if (result.charAt(i) == ' ' || result.charAt(i) == '\n') {
                continue;
            }
            if (result.charAt(i) == '{') {
                break;
            }
            break;
        }
        if (!compound.getKeys().isEmpty()) {
            result.append("\n").append(Strings.repeat(" ", Math.max(0, currentIndentation - indentationLevel)));
        }
        result.append(bracketColor).append("}").append(resetColor);

        return result.toString();
    }

    public static String formatNBT(NbtCompound compound, int indentationLevel, boolean colors) {
        return formatNBT(compound, indentationLevel, indentationLevel, colors);
    }

    private static void handleTag(int currentIndentation, int indentationLevel, boolean colors, StringBuilder result, NbtElement tag) {
        if (tag instanceof NbtCompound) {
            result.append(formatNBT((NbtCompound) tag, currentIndentation + indentationLevel, indentationLevel, colors));
        } else if (tag instanceof NbtList) {
            result.append(formatNBT((NbtList) tag, currentIndentation + indentationLevel, indentationLevel, colors));
        } else {
            result.append(colors ? colorTag(tag) : tag.toString());
        }

        result.append(",");
    }

    public static String formatNBT(NbtList list, int currentIndentation, int indentationLevel, boolean colors) {
        String bracketColor = "\u00a7f";
        String resetColor = "\u00a7r";
        if (!colors) {
            bracketColor = "";
            resetColor = "";
        }
        StringBuilder result = new StringBuilder(bracketColor + "[" + resetColor);

        String indentation = Strings.repeat(" ", currentIndentation);

        for (NbtElement tag : list) {
            result.append("\n").append(indentation);

            handleTag(currentIndentation, indentationLevel, colors, result, tag);
        }

        if (result.charAt(result.length() - 1) == ',') {
            result.setCharAt(result.length() - 1, ' ');
        }

        if (!list.isEmpty()) {
            result.append("\n").append(Strings.repeat(" ", Math.max(0, currentIndentation - indentationLevel)));
        }
        result.append(bracketColor).append("]").append(resetColor);

        return result.toString();
    }

    @SuppressWarnings("unused")
    public static String colorTag(NbtElement tag) {
        return switch (tag) {
            case NbtByte n -> color(tag, '2');
            case NbtShort     n -> color(tag, '2');
            case NbtInt       n -> color(tag, '2');
            case NbtLong      n -> color(tag, '2');
            case NbtFloat     n -> color(tag, '2');
            case NbtDouble    n -> color(tag, '2');
            case NbtByteArray n -> color(tag, '9');
            case NbtIntArray  n -> color(tag, '9');
            case NbtString    n -> color(tag, '6');
            default             -> color(tag, 'f');
        };
    }

    private static String color(Object string, char color) {
        return "\u00a7" + color + string.toString() + "\u00a7r";
    }
}
