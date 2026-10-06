package com.RegalManiac.addon.commands;

import com.RegalManiac.addon.modules.player.KitCreator;
import com.RegalManiac.addon.utils.automation.KitUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

import java.io.File;

public class KitCreatorCommand extends Command {

    private static final SuggestionProvider<CommandSource> PRESET_SUGGESTIONS = (context, builder) -> {
        File kitsFolder = KitUtils.getKitsFolder();
        if (kitsFolder.exists() && kitsFolder.isDirectory()) {
            File[] files = kitsFolder.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                String remaining = builder.getRemaining().toLowerCase();
                for (File file : files) {
                    String presetName = file.getName().replace(".json", "");
                    if (presetName.toLowerCase().startsWith(remaining)) {
                        builder.suggest(presetName);
                    }
                }
            }
        }
        return builder.buildFuture();
    };

    public KitCreatorCommand() {
        super("kitcreator", "Manages KitCreator presets.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("create")
            .then(argument("name", StringArgumentType.word())
                .executes(ctx -> {
                    KitCreator module = Modules.get().get(KitCreator.class);
                    String name = StringArgumentType.getString(ctx, "name");
                    module.startCreatingPreset(name);
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("set")
            .then(argument("name", StringArgumentType.word())
                .suggests(PRESET_SUGGESTIONS)
                .executes(ctx -> {
                    KitCreator module = Modules.get().get(KitCreator.class);
                    String name = StringArgumentType.getString(ctx, "name");
                    module.setActivePreset(name);
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("remove")
            .then(argument("name", StringArgumentType.word())
                .suggests(PRESET_SUGGESTIONS)
                .executes(ctx -> {
                    KitCreator module = Modules.get().get(KitCreator.class);
                    String name = StringArgumentType.getString(ctx, "name");
                    module.removePreset(name);
                    return SINGLE_SUCCESS;
                })));
    }
}
