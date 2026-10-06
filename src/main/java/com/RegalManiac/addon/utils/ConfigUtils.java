package com.RegalManiac.addon.utils;

import com.RegalManiac.addon.modules.combat.OffhandPlus;
import com.RegalManiac.addon.modules.misc.*;
import com.RegalManiac.addon.modules.movement.*;
import com.RegalManiac.addon.modules.player.*;
import com.RegalManiac.addon.modules.render.DamageIndicator;
import com.RegalManiac.addon.modules.render.LogoutSpotsPlus;
import com.RegalManiac.addon.modules.world.*;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
import meteordevelopment.meteorclient.events.meteor.ModuleBindChangedEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ConfigUtils {

    private static final File FILE = new File(MeteorClient.FOLDER, "sindiaddon-config.nbt");

    private static boolean canSave = false;
    private static NbtCompound cachedRootTag = new NbtCompound();

    public static final List<Class<? extends Module>> MODULES_TO_SAVE = Arrays.asList(
        OffhandPlus.class,
        AutoLoginPlus.class, ChatControl.class, ChatEncryptionPlus.class, HitSound.class, NBTTooltip.class, RaidfarmAutomation.class, WheelPicker.class,
        AutoJumpPlus.class, BlinkPlus.class, ElytraUtils.class, MovementFix.class, Pitch40.class, VelocityPlus.class,
        AutoCraftPlus.class, AutoEnchantPlus.class, AutoReplenishPlus.class, AutoSmithingPlus.class, AutoTradePlus.class, FakePlayerPlus.class, KitCreator.class,
        DamageIndicator.class, LogoutSpotsPlus.class,
        AutoMountPlus.class, AutoStripper.class, SignScanner.class
    );

    public static void save() {
        if (!canSave) return;

        File parent = FILE.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            MeteorClient.LOG.error("[SindiAddon] Failed to create parent directory for config!");
        }

        for (Class<? extends Module> klass : MODULES_TO_SAVE) {
            Module module = Modules.get().get(klass);
            if (module != null) {
                NbtCompound tag = module.toTag();
                if (tag != null) {
                    cachedRootTag.put(module.name, tag);
                }
            }
        }
        try {
            NbtIo.writeCompressed(cachedRootTag, FILE.toPath());
        } catch (Exception e) {
            MeteorClient.LOG.error("[SindiAddon] Failed to save config", e);
        }
    }

    public static void load() {
        if (!FILE.exists()) {
            canSave = true;
            return;
        }
        canSave = false;
        try {
            NbtCompound rootTag = NbtIo.readCompressed(FILE.toPath(), NbtSizeTracker.ofUnlimitedBytes());

            if (rootTag != null) {
                cachedRootTag = rootTag;

                for (Class<? extends Module> klass : MODULES_TO_SAVE) {
                    Module module = Modules.get().get(klass);
                    if (module != null && cachedRootTag.contains(module.name)) {
                        java.util.Optional<NbtCompound> optionalTag = cachedRootTag.getCompound(module.name);
                        optionalTag.ifPresent(module::fromTag);
                    }
                }
            }
        } catch (Exception e) {
            MeteorClient.LOG.error("[SindiAddon] Failed to load config", e);
        } finally {
            canSave = true;
        }
    }

    @EventHandler
    private static void onModuleToggle(ActiveModulesChangedEvent event) {
        save();
    }

    @EventHandler
    private static void onBindChange(ModuleBindChangedEvent event) {
        save();
    }

    @EventHandler
    private static void onGameLeft(GameLeftEvent event) {
        save();
    }
}
