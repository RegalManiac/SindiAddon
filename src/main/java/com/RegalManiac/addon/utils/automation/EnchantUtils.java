package com.RegalManiac.addon.utils.automation;

import com.RegalManiac.addon.modules.player.AutoEnchantPlus;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.RegalManiac.addon.utils.automation.EnchantTreeUtils.getRepairCost;

public class EnchantUtils {

    public static boolean isEnchantable(ItemStack s) {
        String n = s.getItem().toString().toLowerCase();
        boolean isHighTier = n.contains("diamond") || n.contains("netherite") ||
            n.contains("elytra") || n.contains("bow") || n.contains("crossbow") ||
            n.contains("trident") || n.contains("mace") || n.contains("shears") ||
            n.contains("fishing-rod") || n.contains("flint-and-steel");

        if (!isHighTier) return false;
        return n.contains("helmet") || n.contains("chestplate") || n.contains("leggings") || n.contains("boots") ||
            n.contains("sword") || n.contains("spear") || n.contains("mace") || n.contains("pickaxe") || n.contains("axe") ||
            n.contains("shovel") || n.contains("bow") || n.contains("trident") || n.contains("elytra") ||
            n.contains("crossbow") || n.contains("hoe") || n.contains("fishing-rod") ||
            n.contains("shears") || n.contains("flint-and-steel");
    }

    public static Set<RegistryKey<Enchantment>> getTargetEnchants(ItemStack stack, AutoEnchantPlus module) {
        if (stack.isEmpty()) return Collections.emptySet();
        String n = stack.getItem().toString().toLowerCase();

        if (n.contains("helmet")) return module.helmetEnchants.get();
        if (n.contains("chestplate")) return module.chestplateEnchants.get();
        if (n.contains("leggings")) return module.leggingsEnchants.get();
        if (n.contains("boots")) return module.bootsEnchants.get();
        if (n.contains("elytra")) return module.elytraEnchants.get();
        if (n.contains("sword")) return module.swordEnchants.get();
        if (n.contains("spear")) return module.spearEnchants.get();
        if (n.contains("mace")) return module.maceEnchants.get();
        if (n.contains("trident")) return module.tridentEnchants.get();
        if (n.contains("bow")) return module.bowEnchants.get();
        if (n.contains("crossbow")) return module.crossbowEnchants.get();
        if (n.contains("pickaxe")) return module.pickaxeEnchants.get();
        if (n.contains("shovel")) return module.shovelEnchants.get();
        if (n.contains("axe")) return module.axeEnchants.get();
        if (n.contains("hoe")) return module.hoeEnchants.get();
        if (n.contains("fishing-rod")) return module.fishingRodEnchants.get();
        if (n.contains("shears")) return module.shearEnchants.get();
        if (n.contains("flint-and-steel")) return module.flintEnchants.get();

        return Collections.emptySet();
    }

    public static List<RegistryKey<Enchantment>> getNeeded(ItemStack stack, AutoEnchantPlus module) {
        if (stack.isEmpty()) return Collections.emptyList();
        var current = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT)
            .getEnchantments().stream().map(e -> e.getKey().get()).collect(Collectors.toSet());

        Set<RegistryKey<Enchantment>> wanted = getTargetEnchants(stack, module);

        return wanted.stream().filter(e -> !current.contains(e)).collect(Collectors.toList());
    }

    public static boolean hasUnwantedEnchants(ItemStack stack, AutoEnchantPlus module) {
        if (stack.isEmpty()) return false;
        var currentEnchants = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT)
            .getEnchantments().stream().map(e -> e.getKey().get()).collect(Collectors.toSet());

        if (currentEnchants.isEmpty()) return false;

        Set<RegistryKey<Enchantment>> wantedEnchants = getTargetEnchants(stack, module);

        for (RegistryKey<Enchantment> current : currentEnchants) {
            if (!wantedEnchants.contains(current)) {
                return true;
            }
        }
        return false;
    }

    public static ItemEnchantmentsComponent getEnchantments(ItemStack stack) {
        if (stack.isOf(Items.ENCHANTED_BOOK)) {
            return stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        }
        return stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
    }

    public static boolean isSingleEnchantBook(ItemStack s) {
        if (!s.isOf(Items.ENCHANTED_BOOK)) return false;
        return getEnchantments(s).getEnchantments().size() == 1;
    }

    public static boolean isTargetBook(ItemStack s, RegistryKey<Enchantment> ench) {
        if (!isSingleEnchantBook(s)) return false;
        var comp = getEnchantments(s);
        for (var entry : comp.getEnchantmentEntries()) {
            if (entry.getKey().getKey().get().equals(ench)) return true;
        }
        return false;
    }

    public static boolean containsAllEnchantments(ItemStack stack, ItemStack target) {
        ItemEnchantmentsComponent stackEnchs = getEnchantments(stack);
        ItemEnchantmentsComponent targetEnchs = getEnchantments(target);

        for (var entry : targetEnchs.getEnchantmentEntries()) {
            if (stackEnchs.getLevel(entry.getKey()) < entry.getIntValue()) return false;
        }
        return true;
    }

    public static boolean isSameForAnvil(ItemStack anvilStack, ItemStack neededStack) {
        if (anvilStack.isEmpty() || neededStack == null) return false;
        if (anvilStack.getItem() != neededStack.getItem()) return false;

        int anvilRC = getRepairCost(anvilStack);
        int neededRC = getRepairCost(neededStack);

        if (anvilRC != neededRC) return false;

        if (neededStack.isOf(Items.ENCHANTED_BOOK)) {
            return containsAllEnchantments(anvilStack, neededStack);
        }

        return Objects.equals(getEnchantments(anvilStack), getEnchantments(neededStack));
    }
}
