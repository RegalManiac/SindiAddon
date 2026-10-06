package com.RegalManiac.addon.utils.automation;

import com.RegalManiac.addon.modules.player.AutoSmithingPlus;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.equipment.trim.*;
import net.minecraft.registry.RegistryKey;

public class SmithingUtils {

    public record SmithingTask(Item template, Item material) {}

    public static boolean isAnyTrackedItem(Item item) {
        return isDiamondEquipment(item) || isNetheriteEquipment(item);
    }

    public static boolean isDiamondEquipment(Item item) {
        return item == Items.DIAMOND_SPEAR || item == Items.DIAMOND_SWORD || item == Items.DIAMOND_PICKAXE ||
            item == Items.DIAMOND_AXE || item == Items.DIAMOND_SHOVEL ||
            item == Items.DIAMOND_HOE || item == Items.DIAMOND_HELMET ||
            item == Items.DIAMOND_CHESTPLATE || item == Items.DIAMOND_LEGGINGS ||
            item == Items.DIAMOND_BOOTS;
    }

    public static boolean isNetheriteEquipment(Item item) {
        return item == Items.NETHERITE_SPEAR || item == Items.NETHERITE_SWORD || item == Items.NETHERITE_PICKAXE ||
            item == Items.NETHERITE_AXE || item == Items.NETHERITE_SHOVEL ||
            item == Items.NETHERITE_HOE || item == Items.NETHERITE_HELMET ||
            item == Items.NETHERITE_CHESTPLATE || item == Items.NETHERITE_LEGGINGS ||
            item == Items.NETHERITE_BOOTS;
    }

    public static boolean isDiamondUpgradable(Item item, AutoSmithingPlus module) {
        if (item == Items.DIAMOND_SPEAR && module.upgradeSpears.get()) return true;
        if (item == Items.DIAMOND_SWORD && module.upgradeSwords.get()) return true;
        if (item == Items.DIAMOND_PICKAXE && module.upgradePickaxes.get()) return true;
        if (item == Items.DIAMOND_AXE && module.upgradeAxes.get()) return true;
        if (item == Items.DIAMOND_SHOVEL && module.upgradeShovels.get()) return true;
        if (item == Items.DIAMOND_HOE && module.upgradeHoes.get()) return true;
        if (item == Items.DIAMOND_HELMET && module.upgradeHelmets.get()) return true;
        if (item == Items.DIAMOND_CHESTPLATE && module.upgradeChestplates.get()) return true;
        if (item == Items.DIAMOND_LEGGINGS && module.upgradeLeggings.get()) return true;
        if (item == Items.DIAMOND_BOOTS && module.upgradeBoots.get()) return true;
        return false;
    }

    public static boolean isTrimmableArmor(Item item, AutoSmithingPlus module) {
        boolean isDiamond = item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS;
        boolean isNetherite = item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS;
        return (module.trimDiamondArmor.get() && isDiamond) || (module.trimNetheriteArmor.get() && isNetherite);
    }

    public static TrimTemplateType getTrimTemplateForArmor(Item item, AutoSmithingPlus module) {
        if (item == Items.DIAMOND_HELMET || item == Items.NETHERITE_HELMET) return module.helmetTrimTemplate.get();
        if (item == Items.DIAMOND_CHESTPLATE || item == Items.NETHERITE_CHESTPLATE) return module.chestplateTrimTemplate.get();
        if (item == Items.DIAMOND_LEGGINGS || item == Items.NETHERITE_LEGGINGS) return module.leggingsTrimTemplate.get();
        if (item == Items.DIAMOND_BOOTS || item == Items.NETHERITE_BOOTS) return module.bootsTrimTemplate.get();
        return TrimTemplateType.NONE;
    }

    public static TrimMaterialType getTrimMaterialForArmor(Item item, AutoSmithingPlus module) {
        if (item == Items.DIAMOND_HELMET || item == Items.NETHERITE_HELMET) return module.helmetTrimMaterial.get();
        if (item == Items.DIAMOND_CHESTPLATE || item == Items.NETHERITE_CHESTPLATE) return module.chestplateTrimMaterial.get();
        if (item == Items.DIAMOND_LEGGINGS || item == Items.NETHERITE_LEGGINGS) return module.leggingsTrimMaterial.get();
        if (item == Items.DIAMOND_BOOTS || item == Items.NETHERITE_BOOTS) return module.bootsTrimMaterial.get();
        return TrimMaterialType.NONE;
    }

    public static SmithingTask getNextTask(ItemStack stack, AutoSmithingPlus module) {
        if (stack.isEmpty()) return null;
        Item item = stack.getItem();

        if (isDiamondUpgradable(item, module)) {
            Item template = module.useTemplates.get() ? Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE : null;
            return new SmithingTask(template, Items.NETHERITE_INGOT);
        }

        if (module.applyTrims.get() && isTrimmableArmor(item, module)) {
            TrimTemplateType tType = getTrimTemplateForArmor(item, module);
            TrimMaterialType mType = getTrimMaterialForArmor(item, module);

            if (tType != TrimTemplateType.NONE && mType != TrimMaterialType.NONE) {
                ArmorTrim existingTrim = stack.get(DataComponentTypes.TRIM);
                if (existingTrim == null || !existingTrim.pattern().matchesKey(tType.patternKey) || !existingTrim.material().matchesKey(mType.materialKey)) {
                    return new SmithingTask(tType.item, mType.item);
                }
            }
        }
        return null;
    }

    public enum TrimTemplateType {
        NONE("None", null, null),
        SENTRY("Sentry", Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.SENTRY),
        DUNE("Dune", Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.DUNE),
        COAST("Coast", Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.COAST),
        WILD("Wild", Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.WILD),
        WARD("Ward", Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.WARD),
        EYE("Eye", Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.EYE),
        VEX("Vex", Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.VEX),
        TIDE("Tide", Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.TIDE),
        SNOUT("Snout", Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.SNOUT),
        RIB("Rib", Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.RIB),
        SPIRE("Spire", Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.SPIRE),
        SILENCE("Silence", Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.SILENCE),
        WAYFINDER("Wayfinder", Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.WAYFINDER),
        RAISER("Raiser", Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.RAISER),
        SHAPER("Shaper", Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.SHAPER),
        HOST("Host", Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.HOST),
        FLOW("Flow", Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.FLOW),
        BOLT("Bolt", Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, ArmorTrimPatterns.BOLT);

        public final String title;
        public final Item item;
        public final RegistryKey<ArmorTrimPattern> patternKey;

        TrimTemplateType(String title, Item item, RegistryKey<ArmorTrimPattern> patternKey) {
            this.title = title; this.item = item; this.patternKey = patternKey;
        }
        @Override public String toString() { return title; }
    }

    public enum TrimMaterialType {
        NONE("None", null, null),
        QUARTZ("Quartz", Items.QUARTZ, ArmorTrimMaterials.QUARTZ),
        IRON("Iron", Items.IRON_INGOT, ArmorTrimMaterials.IRON),
        NETHERITE("Netherite", Items.NETHERITE_INGOT, ArmorTrimMaterials.NETHERITE),
        REDSTONE("Redstone", Items.REDSTONE, ArmorTrimMaterials.REDSTONE),
        COPPER("Copper", Items.COPPER_INGOT, ArmorTrimMaterials.COPPER),
        GOLD("Gold", Items.GOLD_INGOT, ArmorTrimMaterials.GOLD),
        EMERALD("Emerald", Items.EMERALD, ArmorTrimMaterials.EMERALD),
        DIAMOND("Diamond", Items.DIAMOND, ArmorTrimMaterials.DIAMOND),
        LAPIS("Lapis", Items.LAPIS_LAZULI, ArmorTrimMaterials.LAPIS),
        AMETHYST("Amethyst", Items.AMETHYST_SHARD, ArmorTrimMaterials.AMETHYST),
        RESIN("Resin", Items.RESIN_BRICK, ArmorTrimMaterials.RESIN);

        public final String title;
        public final Item item;
        public final RegistryKey<ArmorTrimMaterial> materialKey;

        TrimMaterialType(String title, Item item, RegistryKey<ArmorTrimMaterial> materialKey) {
            this.title = title; this.item = item; this.materialKey = materialKey;
        }
        @Override public String toString() { return title; }
    }
}
