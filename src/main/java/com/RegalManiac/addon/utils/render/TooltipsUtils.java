package com.RegalManiac.addon.utils.render;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTooltips;

public class TooltipsUtils {

    public static Setting<Boolean> villagerDemand;

    public static boolean isVillagerDemandEnabled() {
        BetterTooltips betterTooltips = Modules.get().get(BetterTooltips.class);
        if (betterTooltips == null || !betterTooltips.isActive()) return false;

        return villagerDemand != null && villagerDemand.get();
    }

    public static String convertToSubscript(String str) {
        StringBuilder result = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (c >= '0' && c <= '9') {
                result.append((char) (c - '0' + '₀'));
            } else if (c == '-') {
                result.append('₋');
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
