package com.RegalManiac.addon;

import com.RegalManiac.addon.commands.*;
import com.RegalManiac.addon.managers.*;
import com.RegalManiac.addon.modules.combat.*;
import com.RegalManiac.addon.modules.misc.*;
import com.RegalManiac.addon.modules.movement.*;
import com.RegalManiac.addon.modules.player.*;
import com.RegalManiac.addon.modules.render.*;
import com.RegalManiac.addon.modules.world.*;
import com.RegalManiac.addon.utils.Config;
import com.RegalManiac.addon.utils.ConfigUtils;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

public class SindiAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final String PREFIX_TEXT = "SindiAddon";
    public static final Formatting PREFIX_COLOR = Formatting.DARK_RED;


    @Override
    public void onInitialize() {
        LOG.info("Initializing [SindiAddon]");

        Config.init();

        RotationManager.init();
        LobbyManager.init();

        ChatUtils.registerCustomPrefix("com.RegalManiac.addon", () -> {
            MutableText text = Text.empty();
            text.append(Text.literal("[").formatted(Formatting.GRAY));
            text.append(Text.literal(PREFIX_TEXT).formatted(PREFIX_COLOR));
            text.append(Text.literal("] ").formatted(Formatting.GRAY));
            return text;
        });

        meteordevelopment.meteorclient.systems.modules.misc.BetterChat.registerCustomHead(
            "[" + PREFIX_TEXT + "]",
            Identifier.of("sindiaddon", "textures/icon/sindi.png")
        );

        initializeModules(Modules.get());

        Commands.add(new PanicCommand());
        Commands.add(new KitCreatorCommand());

        MeteorClient.EVENT_BUS.subscribe(ConfigUtils.class);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ConfigUtils.load();
            LOG.info("[SindiAddon] Config loaded successfully!");
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ConfigUtils.save();
            LOG.info("[SindiAddon] Config saved successfully!");
        });
    }

    private void initializeModules(Modules modules) {
        //Combat
        modules.add(new OffhandPlus());

        //Player
        modules.add(new AutoCraftPlus());
        modules.add(new AutoEnchantPlus());
        modules.add(new AutoReplenishPlus());
        modules.add(new AutoSmithingPlus());
        modules.add(new AutoTradePlus());
        modules.add(new FakePlayerPlus());
        modules.add(new KitCreator());

        //Movement
        modules.add(new AutoJumpPlus());
        modules.add(new BlinkPlus());
        modules.add(new ElytraUtils());
        modules.add(new MovementFix());
        modules.add(new Pitch40());
        modules.add(new VelocityPlus());

        //Render
        modules.add(new DamageIndicator());
        modules.add(new LogoutSpotsPlus());

        //World
        modules.add(new AutoMountPlus());
        modules.add(new AutoStripper());
        modules.add(new SignScanner());

        //Misc
        modules.add(new ChatControl());
        modules.add(new ChatEncryptionPlus());
        modules.add(new HitSound());
        modules.add(new RaidfarmAutomation());

        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("meteor-satellite-addon")) {
            modules.add(new NBTTooltip());
        } else {
            LOG.info("[SindiAddon] Satellite detected. NBTTooltip will not be initialized.");
        }
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("auto-login-plus-plus")) {
            modules.add(new AutoLoginPlus());
        } else {
            LOG.info("[SindiAddon] AutoLoginPlusPlus detected. AutoLoginPlus will not be initialized.");
        }
    }

    @Override
    public String getPackage() {
        return "com.RegalManiac.addon";
    }
}
