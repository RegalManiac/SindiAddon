package com.RegalManiac.addon.utils;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class Config {

    public static Setting<SettingColor> skullColor;
    public static Setting<Boolean> showKeybinds;
    public static Setting<Boolean> moduleSounds;
    public static Setting<Boolean> chatSounds;
    public static Setting<Boolean> visualRangeSounds;
    public static Setting<Boolean> deathSounds;
    public static Setting<Boolean> guiSounds;

    public static void init() {
        SettingGroup sgSindi = meteordevelopment.meteorclient.systems.config.Config.get().settings.createGroup("SindiAddon");

        skullColor = sgSindi.add(new ColorSetting.Builder()
            .name("skull-color")
            .description("Color of the skull icon.")
            .defaultValue(new SettingColor(225, 45, 45, 255))
            .build()
        );

        showKeybinds = sgSindi.add(new BoolSetting.Builder()
            .name("show-keybinds")
            .description("Displays module keybinds in GUI.")
            .defaultValue(true)
            .build()
        );

        moduleSounds = sgSindi.add(new BoolSetting.Builder()
            .name("custom-module-sound")
            .description("Plays sounds when toggling modules.")
            .defaultValue(true)
            .build()
        );

        visualRangeSounds = sgSindi.add(new BoolSetting.Builder()
            .name("custom-visual-range-sound")
            .description("Use custom sounds for Visual Range alerts.")
            .defaultValue(true)
            .build()
        );

        deathSounds = sgSindi.add(new BoolSetting.Builder()
            .name("custom-death-sound")
            .description("Plays custom sound upon death.")
            .defaultValue(true)
            .build()
        );

        chatSounds = sgSindi.add(new BoolSetting.Builder()
            .name("custom-chat-sound")
            .description("Plays sounds for chat messages (info, warning, error).")
            .defaultValue(true)
            .build()
        );

        guiSounds = sgSindi.add(new BoolSetting.Builder()
            .name("custom-gui-sounds")
            .description("Plays sound effects when interacting with Meteor GUI.")
            .defaultValue(true)
            .build()
        );
    }

    public static class APIResponse {
        public String name;
        public String id;
    }
}
