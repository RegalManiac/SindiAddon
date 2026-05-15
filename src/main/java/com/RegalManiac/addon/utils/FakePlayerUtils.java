package com.RegalManiac.addon.utils;

import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.systems.modules.combat.Criticals;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

public class FakePlayerUtils extends PlayerEntity {
    FakePlayerUtils(World var1, GameProfile var4) {
        super(var1, var4);
    }

    @Override
    public @Nullable GameMode getGameMode() {
        return null;
    }

    public boolean isSpectator() {
        return false;
    }

    public boolean isCreative() {
        return false;
    }

    public static float getHitDamage(PlayerEntity attacker, PlayerEntity target) {
        float damage = 1.0F;
        ItemStack stack = attacker.getMainHandStack();

        if (!stack.isEmpty()) {
            try {
                if (stack.contains(net.minecraft.component.DataComponentTypes.ATTRIBUTE_MODIFIERS)) {
                    net.minecraft.component.type.AttributeModifiersComponent modifiers = stack.get(net.minecraft.component.DataComponentTypes.ATTRIBUTE_MODIFIERS);
                    if (modifiers != null) {
                        for (net.minecraft.component.type.AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
                            if (entry.attribute().equals(EntityAttributes.ATTACK_DAMAGE) ||
                                entry.attribute().value().getTranslationKey().contains("attack_damage")) {
                                damage += (float) entry.modifier().value();
                            }
                        }
                    }
                } else {
                    String name = stack.getItem().toString().toLowerCase();
                    if (name.contains("sword")) {
                        if (name.contains("netherite")) damage += 8.0f;
                        else if (name.contains("diamond")) damage += 7.0f;
                        else if (name.contains("iron")) damage += 6.0f;
                        else if (name.contains("stone")) damage += 5.0f;
                        else damage += 4.0f;
                    } else if (name.contains("axe")) {
                        if (name.contains("netherite")) damage += 10.0f;
                        else if (name.contains("diamond") || name.contains("iron") || name.contains("stone")) damage += 9.0f;
                        else damage += 7.0f;
                    } else if (name.contains("mace")) {
                        damage += 6.0f;
                        if (attacker.fallDistance > 1.5f) {
                            damage += (attacker.fallDistance - 1.5f) * 3.0f;
                        }
                    } else if (name.contains("spear") || name.contains("lance")) {
                        if (name.contains("netherite")) damage += 8.0f;
                        else if (name.contains("diamond")) damage += 7.0f;
                        else if (name.contains("iron")) damage += 6.0f;
                        else if (name.contains("stone")) damage += 5.0f;
                        else damage += 4.0f;

                        double speed = Math.sqrt(attacker.getVelocity().x * attacker.getVelocity().x + attacker.getVelocity().z * attacker.getVelocity().z);
                        if (speed > 0.15) {
                            damage += (float) (speed * 15.0);
                        }
                        if (attacker.fallDistance > 1.5f) {
                            damage += (attacker.fallDistance - 1.5f) * 2.0f;
                        }
                    }
                }
            } catch (Exception ignored) {
                damage = (float) attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
            }
        }

        float[] enchantDamage = {0.0f};
        EnchantmentHelper.forEachEnchantment(stack, (enchantment, level) -> {
            enchantment.getKey().ifPresent(key -> {
                String id = key.getValue().getPath();
                if (id.equals("sharpness")) {
                    enchantDamage[0] += 0.5f * level + 0.5f;
                }
            });
        });

        damage += enchantDamage[0];

        float cooldown = attacker.getAttackCooldownProgress(0.5F);
        float currentDamage = damage * (0.2F + cooldown * cooldown * 0.8F);

        if (isCrit(attacker, cooldown, target)) {
            currentDamage *= 1.5F;
        }

        return currentDamage;
    }

    public static boolean isCrit(PlayerEntity attacker, float cooldown, Entity target) {
        boolean hackCrit = false;
        try {
            meteordevelopment.meteorclient.systems.modules.Modules modules = meteordevelopment.meteorclient.systems.modules.Modules.get();
            if (modules != null) {
                meteordevelopment.meteorclient.systems.modules.Module crit1 = modules.get(Criticals.class);
                if ((crit1 != null && crit1.isActive())) {
                    hackCrit = true;
                }
            }
        } catch (Exception ignored) {}

        boolean vanillaCrit = attacker.fallDistance > 0.0F
            && !attacker.isOnGround()
            && !attacker.isClimbing()
            && !attacker.isTouchingWater()
            && !attacker.hasStatusEffect(StatusEffects.BLINDNESS)
            && !attacker.hasVehicle()
            && target instanceof LivingEntity
            && !attacker.isSprinting();

        return cooldown > 0.9F && (vanillaCrit || hackCrit);
    }

    public static void playHitSound(PlayerEntity attacker, PlayerEntity target, float damage) {
        if (damage <= 0.0F || target.isDead()) {
            attacker.getEntityWorld().playSound(attacker, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS, 1.0F, 1.0F);
            return;
        }

        boolean fullCooldown = attacker.getAttackCooldownProgress(0.5F) > 0.9F;
        boolean sprintHit = attacker.isSprinting() && fullCooldown;

        if (sprintHit) {
            attacker.getEntityWorld().playSound(attacker, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }

        boolean critical = isCrit(attacker, attacker.getAttackCooldownProgress(0.5F), target);

        double horizontalSpeed = Math.sqrt(attacker.getVelocity().x * attacker.getVelocity().x + attacker.getVelocity().z * attacker.getVelocity().z);
        double prevX = attacker.getX() - attacker.getVelocity().x;
        double prevZ = attacker.getZ() - attacker.getVelocity().z;
        double prevHorizontalSpeed = Math.sqrt(Math.pow(attacker.getX() - prevX, 2) + Math.pow(attacker.getZ() - prevZ, 2));
        double deltaSpeed = horizontalSpeed - prevHorizontalSpeed;

        boolean sweep = fullCooldown
            && !critical
            && !sprintHit
            && attacker.isOnGround()
            && deltaSpeed < attacker.getMovementSpeed()
            && attacker.getMainHandStack().getItem().toString().contains("sword");

        if (sweep) {
            attacker.getEntityWorld().playSound(attacker, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 1.0F);
        } else if (!critical) {
            net.minecraft.sound.SoundEvent soundEvent = fullCooldown ? SoundEvents.ENTITY_PLAYER_ATTACK_STRONG : SoundEvents.ENTITY_PLAYER_ATTACK_WEAK;
            attacker.getEntityWorld().playSound(attacker, target.getBlockPos(), soundEvent, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }

        if (critical) {
            attacker.getEntityWorld().playSound(attacker, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
    }

    public static float calcDamageReduction(LivingEntity target, float baseDamage, DamageSource source) {
        float totalArmor = 0;
        float totalToughness = 0;
        float[] epf = {0.0f};

        EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        for (EquipmentSlot slot : armorSlots) {
            ItemStack stack = target.getEquippedStack(slot);
            if (stack == null || stack.isEmpty()) continue;

            try {
                boolean hasStats = false;
                if (stack.contains(net.minecraft.component.DataComponentTypes.ATTRIBUTE_MODIFIERS)) {
                    net.minecraft.component.type.AttributeModifiersComponent modifiers = stack.get(net.minecraft.component.DataComponentTypes.ATTRIBUTE_MODIFIERS);
                    if (modifiers != null) {
                        for (net.minecraft.component.type.AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
                            String attrName = entry.attribute().value().getTranslationKey().toLowerCase();
                            if (attrName.contains("armor_toughness")) {
                                totalToughness += (float) entry.modifier().value();
                                hasStats = true;
                            } else if (attrName.contains("armor")) {
                                totalArmor += (float) entry.modifier().value();
                                hasStats = true;
                            }
                        }
                    }
                }

                if (!hasStats) {
                    String name = stack.getItem().toString().toLowerCase();
                    if (name.contains("netherite")) {
                        totalToughness += 3;
                        if (name.contains("chestplate")) totalArmor += 8;
                        else if (name.contains("leggings")) totalArmor += 6;
                        else if (name.contains("helmet")) totalArmor += 3;
                        else if (name.contains("boots")) totalArmor += 3;
                    }
                    else if (name.contains("diamond")) {
                        totalToughness += 2;
                        if (name.contains("chestplate")) totalArmor += 8;
                        else if (name.contains("leggings")) totalArmor += 6;
                        else if (name.contains("helmet")) totalArmor += 3;
                        else if (name.contains("boots")) totalArmor += 3;
                    }
                    else if (name.contains("iron")) {
                        if (name.contains("chestplate")) totalArmor += 6;
                        else if (name.contains("leggings")) totalArmor += 5;
                        else if (name.contains("helmet")) totalArmor += 2;
                        else if (name.contains("boots")) totalArmor += 2;
                    }
                    else if (name.contains("golden")) {
                        if (name.contains("chestplate")) totalArmor += 5;
                        else if (name.contains("leggings")) totalArmor += 3;
                        else if (name.contains("helmet")) totalArmor += 2;
                        else if (name.contains("boots")) totalArmor += 1;
                    }
                    else if (name.contains("chainmail")) {
                        if (name.contains("chestplate")) totalArmor += 5;
                        else if (name.contains("leggings")) totalArmor += 4;
                        else if (name.contains("helmet")) totalArmor += 2;
                        else if (name.contains("boots")) totalArmor += 1;
                    }
                    else if (name.contains("leather")) {
                        if (name.contains("chestplate")) totalArmor += 3;
                        else if (name.contains("leggings")) totalArmor += 2;
                        else if (name.contains("helmet")) totalArmor += 1;
                        else if (name.contains("boots")) totalArmor += 1;
                    }
                }
            } catch (Exception ignored) {}

            EnchantmentHelper.forEachEnchantment(stack, (enchantment, level) -> {
                enchantment.getKey().ifPresent(key -> {
                    String id = key.getValue().getPath();
                    boolean isExplosion = source.getName().toLowerCase().contains("explosion");

                    if (id.equals("protection")) {
                        epf[0] += level;
                    } else if (id.equals("blast_protection") && isExplosion) {
                        epf[0] += level * 2;
                    } else if (id.equals("projectile_protection") && source.getName().toLowerCase().contains("arrow")) {
                        epf[0] += level * 2;
                    }
                });
            });
        }

        float damage = DamageUtil.getDamageLeft(target, baseDamage, source, totalArmor, totalToughness);

        float totalEpf = Math.min(20.0f, epf[0]);
        if (totalEpf > 0) {
            damage = damage * (1.0f - totalEpf / 25.0f);
        }

        return damage;
    }
}
