package com.RegalManiac.addon.modules.movement;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class MovementFix extends Module {

    public MovementFix() {
        super(Categories.Movement, "Movement-Fix", "Fixes movement and rotations for anticheats like GrimAC.");
    }

    public static boolean active() {
        return Modules.get().isActive(MovementFix.class);
    }
}
