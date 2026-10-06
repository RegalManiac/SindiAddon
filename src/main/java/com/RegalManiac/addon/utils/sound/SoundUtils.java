package com.RegalManiac.addon.utils.sound;

import com.RegalManiac.addon.utils.Config;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class SoundUtils {

    public static final SoundEvent MODULE_ON = SoundEvent.of(Identifier.of("sindiaddon", "enable"));
    public static final SoundEvent MODULE_OFF = SoundEvent.of(Identifier.of("sindiaddon", "disable"));

    public static final SoundEvent VR_ENTER = SoundEvent.of(Identifier.of("sindiaddon", "vrenter"));
    public static final SoundEvent VR_LEAVE = SoundEvent.of(Identifier.of("sindiaddon", "vrleave"));
    public static final SoundEvent DEATH_MUSIC = SoundEvent.of(Identifier.of("sindiaddon", "death"));

    public static final SoundEvent SOUND_INFO = SoundEvent.of(Identifier.of("sindiaddon", "fuzz"));
    public static final SoundEvent SOUND_WARNING = SoundEvent.of(Identifier.of("sindiaddon", "klaxon"));
    public static final SoundEvent SOUND_ERROR = SoundEvent.of(Identifier.of("sindiaddon", "alarm"));

    public static final SoundEvent CLICK = SoundEvent.of(Identifier.of("sindiaddon", "click"));
    public static final SoundEvent SCROLLING = SoundEvent.of(Identifier.of("sindiaddon", "scrolling"));

    public static final SoundEvent[] PIZZA_COMBOS = {
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo1")),
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo2")),
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo3")),
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo4")),
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo5")),
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo6")),
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo7"))
    };

    private static long lastClickSoundTime = 0;
    private static long lastHoverSoundTime = 0;

    private static long lastModuleOnTime = 0;
    private static long lastModuleOffTime = 0;

    private static long lastVrEnterTime = 0;
    private static long lastVrLeaveTime = 0;

    private static boolean playedDeathSound = false;

    private static long lastInfoTime = 0;
    private static long lastWarningTime = 0;
    private static long lastErrorTime = 0;

    private static int comboStep = 0;
    private static long lastHitTime = 0;
    private static int lastHitTick = -1;
    private static LivingEntity lastAttackedEntity = null;

    // HitSound
    public enum HitMode { PizzaCombo, Simple }

    public enum SimpleSound {
        LoudShotgun(SoundEvent.of(Identifier.of("sindiaddon", "loud_shotgun"))),
        QuietShotgun(SoundEvent.of(Identifier.of("sindiaddon", "quiet_shotgun"))),
        Revolver(SoundEvent.of(Identifier.of("sindiaddon", "revolver"))),
        Hit(SoundEvent.of(Identifier.of("sindiaddon", "hit"))),
        Punch(SoundEvent.of(Identifier.of("sindiaddon", "punch"))),
        CrowbarClang(SoundEvent.of(Identifier.of("sindiaddon", "crowbar_clang")));

        private final SoundEvent soundEvent;
        SimpleSound(SoundEvent soundEvent) { this.soundEvent = soundEvent; }
        public SoundEvent getSound() { return soundEvent; }
    }

    public enum KillSound {
        LilExplode(SoundEvent.of(Identifier.of("sindiaddon", "lil_explode"))),
        HugeExplode(SoundEvent.of(Identifier.of("sindiaddon", "huge_explode"))),
        MetalBreak(SoundEvent.of(Identifier.of("sindiaddon", "metal_break"))),
        SayKill(SoundEvent.of(Identifier.of("sindiaddon", "saykill"))),
        SayClear(SoundEvent.of(Identifier.of("sindiaddon", "sayclear")));

        private final SoundEvent soundEvent;
        KillSound(SoundEvent soundEvent) { this.soundEvent = soundEvent; }
        public SoundEvent getSound() { return soundEvent; }
    }

    public static void resetHitSoundState() {
        comboStep = 0;
        lastAttackedEntity = null;
        lastHitTick = -1;
    }

    public static void handleAttackEntity(Entity attackedEntity, EntityTypeListSetting entities, boolean enableHitSound, HitMode hitMode, int comboTimeout, SimpleSound simpleSound) {
        if (!(attackedEntity instanceof LivingEntity target)) return;
        if (entities != null && !entities.get().contains(target.getType())) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.age == lastHitTick) return;

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastHitTime > comboTimeout) {
            comboStep = 0;
        }

        if (enableHitSound) {
            if (hitMode == HitMode.PizzaCombo) {
                playSoundInWorld(PIZZA_COMBOS[comboStep]);
                comboStep = (comboStep + 1) % PIZZA_COMBOS.length;
            } else if (hitMode == HitMode.Simple && simpleSound != null) {
                playSoundInWorld(simpleSound.getSound());
            }

            if (mc.player != null) lastHitTick = mc.player.age;
        }

        lastHitTime = currentTime;
        lastAttackedEntity = target;
    }

    public static void handleKillSoundTick(boolean enableKillSound, KillSound killSound) {
        if (lastAttackedEntity != null) {
            if (lastAttackedEntity.isDead() || lastAttackedEntity.getHealth() <= 0) {
                if (enableKillSound && killSound != null) {
                    playSoundInWorld(killSound.getSound());
                }

                comboStep = 0;
                lastAttackedEntity = null;
            } else if (System.currentTimeMillis() - lastHitTime > 2000) {
                lastAttackedEntity = null;
            }
        }
    }

    // Module Toggle
    public static void playModuleToggleSound(boolean active) {
        if (Config.moduleSounds != null && !Config.moduleSounds.get()) return;
        if (!canPlayModuleSound()) return;

        long now = System.currentTimeMillis();
        if (active) {
            if (now - lastModuleOnTime < 50) return;
            lastModuleOnTime = now;
            playSound(MODULE_ON);
        } else {
            if (now - lastModuleOffTime < 50) return;
            lastModuleOffTime = now;
            playSound(MODULE_OFF);
        }
    }

    private static boolean canPlayModuleSound() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return false;
        if (mc.player.age < 20) return false;
        if (mc.getNetworkHandler() == null) return false;
        return !(mc.currentScreen instanceof WidgetScreen);
    }

    // Notifier / World
    public static void checkAndPlayDeathSound() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (Config.deathSounds != null && !Config.deathSounds.get()) return;

        boolean isDead = mc.player.getHealth() <= 0 || mc.player.isDead();
        if (isDead && !playedDeathSound) {
            playSound(DEATH_MUSIC);
            playedDeathSound = true;
        } else if (!isDead && playedDeathSound) {
            playedDeathSound = false;
        }
    }

    public static void playVrEnterSound(ClientWorld world, Entity except, Entity entity, SoundEvent fallbackSound, SoundCategory category, float volume, float pitch) {
        if (Config.visualRangeSounds != null && !Config.visualRangeSounds.get()) {
            world.playSoundFromEntity(except, entity, fallbackSound, category, volume, pitch);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastVrEnterTime < 50) return;
        lastVrEnterTime = now;
        world.playSoundFromEntity(except, entity, VR_ENTER, category, volume, pitch);
    }

    public static void playVrLeaveSound(ClientWorld world, Entity except, Entity entity, SoundEvent fallbackSound, SoundCategory category, float volume, float pitch) {
        if (Config.visualRangeSounds != null && !Config.visualRangeSounds.get()) {
            world.playSoundFromEntity(except, entity, fallbackSound, category, volume, pitch);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastVrLeaveTime < 50) return;
        lastVrLeaveTime = now;
        world.playSoundFromEntity(except, entity, VR_LEAVE, category, volume, pitch);
    }

    // Chat
    public static void playChatMessageSound(String message, Formatting defaultColor) {
        if (Config.chatSounds != null && !Config.chatSounds.get()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (message != null && message.startsWith("Toggled")) return;

        long now = System.currentTimeMillis();

        if (defaultColor == Formatting.GRAY) {
            if (now - lastInfoTime < 50) return;
            lastInfoTime = now;
            playSound(SOUND_INFO);
        } else if (defaultColor == Formatting.YELLOW) {
            if (now - lastWarningTime < 50) return;
            lastWarningTime = now;
            playSound(SOUND_WARNING);
        } else if (defaultColor == Formatting.RED) {
            if (now - lastErrorTime < 50) return;
            lastErrorTime = now;
            playSound(SOUND_ERROR);
        }
    }

    // GUI
    public static void playWidgetClickSound(boolean clickHandled) {
        if (!clickHandled) return;
        if (Config.guiSounds != null && !Config.guiSounds.get()) return;

        long now = System.currentTimeMillis();
        if (now - lastClickSoundTime > 100) {
            lastClickSoundTime = now;
            playSound(CLICK, 1.0f);
        }
    }

    public static void playWidgetHoverSound(WWidget widget, boolean wasMouseOver, boolean currentMouseOver, double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
        if (Config.guiSounds != null && !Config.guiSounds.get()) return;
        if (!isInteractive(widget)) return;
        if (mouseX == lastMouseX && mouseY == lastMouseY) return;

        if (!wasMouseOver && currentMouseOver) {
            long now = System.currentTimeMillis();
            if (now - lastHoverSoundTime > 100) {
                lastHoverSoundTime = now;
                playSound(SCROLLING, 1.0f);
            }
        }
    }

    // Helpers
    public static void playSound(SoundEvent sound, float pitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(PositionedSoundInstance.master(sound, pitch));
        }
    }

    public static void playSound(SoundEvent sound) {
        playSound(sound, 1.0F);
    }

    public static void playSoundInWorld(SoundEvent sound) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.world != null) {
            mc.world.playSound(mc.player, mc.player.getBlockPos(), sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static boolean isInteractive(WWidget widget) {
        if (widget instanceof WContainer || widget instanceof WLabel) {
            return false;
        }
        return widget instanceof WPressable;
    }
}
