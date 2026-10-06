package com.RegalManiac.addon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PanicCommand extends Command {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    public PanicCommand() {
        super("panic", "Disables all modules immediately or for a specified duration in seconds.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            panicPermanent();
            return SINGLE_SUCCESS;
        });

        builder.then(argument("seconds", IntegerArgumentType.integer(1)).executes(context -> {
            int seconds = IntegerArgumentType.getInteger(context, "seconds");
            panicTemporary(seconds);
            return SINGLE_SUCCESS;
        }));
    }

    private void panicPermanent() {
        int count = 0;
        for (Module module : Modules.get().getAll()) {
            if (module.isActive()) {
                module.toggle();
                count++;
            }
        }
        info("Panic! Forcefully disabled %d active modules.", count);
    }

    private void panicTemporary(int seconds) {
        List<Module> activeModules = new ArrayList<>();

        for (Module module : Modules.get().getAll()) {
            if (module.isActive()) {
                activeModules.add(module);
                module.toggle();
            }
        }

        if (activeModules.isEmpty()) {
            info("Panic: No active modules to disable.");
            return;
        }

        info("Panic! Disabled %d modules for %d seconds.", activeModules.size(), seconds);

        SCHEDULER.schedule(() -> {
            if (MeteorClient.mc != null) {
                MeteorClient.mc.execute(() -> {
                    for (Module module : activeModules) {
                        module.toggle();
                    }
                    info("Panic expired! Restored %d modules.", activeModules.size());
                });
            }
        }, seconds, TimeUnit.SECONDS);
    }
}
