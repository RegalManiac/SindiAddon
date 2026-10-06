package com.RegalManiac.addon.utils.meteor;

import com.RegalManiac.addon.utils.ConfigUtils;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ModuleWidgetUtils {
    public static final ThreadLocal<Module> CURRENT_MODULE = new ThreadLocal<>();
    public static final double KEYBIND_SCALE = 0.55;
    public static final String SKULL_TEXT = "☠ ";
    public static final VanillaTextRenderer VANILLA_TEXT_RENDERER = VanillaTextRenderer.INSTANCE;

    private static final Map<Class<?>, Optional<Field>> MODULE_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Field>> TITLE_WIDTH_FIELD_CACHE = new ConcurrentHashMap<>();

    private static final Set<Class<? extends Module>> SINDI_MODULES = new HashSet<>(ConfigUtils.MODULES_TO_SAVE);

    public static boolean isSindiModule(Module module) {
        if (module == null) return false;
        return SINDI_MODULES.contains(module.getClass());
    }

    public static Module getModule(WWidget widget) {
        if (widget == null || widget instanceof WContainer) return null;

        Field field = MODULE_FIELD_CACHE.computeIfAbsent(widget.getClass(), clazz -> {
            for (Class<?> c = clazz; c != null && c != WWidget.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getName().equals("module") && Module.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        return Optional.of(f);
                    }
                }
            }
            return Optional.empty();
        }).orElse(null);

        if (field != null) {
            try {
                return (Module) field.get(widget);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static GuiTheme getGuiTheme(WWidget widget) {
        if (widget != null && widget.theme != null) {
            return widget.theme;
        }
        return GuiThemes.get();
    }

    public static void updateTitleWidthField(WWidget widget, double delta) {
        Field field = TITLE_WIDTH_FIELD_CACHE.computeIfAbsent(widget.getClass(), clazz -> {
            for (Class<?> c = clazz; c != null && c != WWidget.class; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField("titleWidth");
                    f.setAccessible(true);
                    return Optional.of(f);
                } catch (NoSuchFieldException ignored) {}
            }
            return Optional.empty();
        }).orElse(null);

        if (field != null) {
            try {
                double current = field.getDouble(widget);
                field.setDouble(widget, current + delta);
            } catch (Exception ignored) {}
        }
    }
}
