package com.RegalManiac.addon.modules.misc;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class HitSound extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgHitSound = settings.createGroup("Hit Sound");
    private final SettingGroup sgKillSound = settings.createGroup("Kill Sound");

    private final EntityTypeListSetting entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to apply sounds to.")
        .build()
    );

    private final Setting<Boolean> enableHitSound = sgHitSound.add(new BoolSetting.Builder()
        .name("enable-hit-sound")
        .description("Plays a sound when you hit an entity.")
        .defaultValue(true)
        .build()
    );

    private final Setting<HitMode> hitMode = sgHitSound.add(new EnumSetting.Builder<HitMode>()
        .name("hit-mode")
        .description("Mode for hit sounds.")
        .defaultValue(HitMode.PizzaCombo)
        .visible(enableHitSound::get)
        .build()
    );

    private final Setting<Integer> comboTimeout = sgHitSound.add(new IntSetting.Builder()
        .name("combo-timeout")
        .description("Milliseconds before combo resets automatically.")
        .defaultValue(3000)
        .min(500)
        .sliderMax(5000)
        .visible(() -> enableHitSound.get() && hitMode.get() == HitMode.PizzaCombo)
        .build()
    );

    private final Setting<SimpleSound> simpleSound = sgHitSound.add(new EnumSetting.Builder<SimpleSound>()
        .name("simple-sound")
        .description("Sound to play in Simple mode.")
        .defaultValue(SimpleSound.LoudShotgun)
        .visible(() -> enableHitSound.get() && hitMode.get() == HitMode.Simple)
        .build()
    );

    private final Setting<Boolean> enableKillSound = sgKillSound.add(new BoolSetting.Builder()
        .name("enable-kill-sound")
        .description("Plays a sound when you kill an entity.")
        .defaultValue(true)
        .build()
    );

    private final Setting<KillSound> killSound = sgKillSound.add(new EnumSetting.Builder<KillSound>()
        .name("kill-sound")
        .description("Sound to play when killing an entity.")
        .defaultValue(KillSound.HugeExplode)
        .visible(enableKillSound::get)
        .build()
    );

    private int comboStep = 0;
    private long lastHitTime = 0;
    private int lastHitTick = -1;
    private LivingEntity lastAttackedEntity = null;

    private static final SoundEvent[] PIZZA_COMBOS = {
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo1")),
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo2")),
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo3")),
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo4")),
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo5")),
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo6")),
        SoundEvent.of(Identifier.of("sindiaddon", "pizzacombo7"))
    };

    public HitSound() {
        super(Categories.Misc, "hit-sound", "Plays custom sounds on hitting or killing entities.");
    }

    @Override
    public void onActivate() {
        comboStep = 0;
        lastAttackedEntity = null;
        lastHitTick = -1;
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (!(event.entity instanceof LivingEntity target)) return;
        if (!entities.get().contains(target.getType())) return;

        if (mc.player != null && mc.player.age == lastHitTick) return;

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastHitTime > comboTimeout.get()) {
            comboStep = 0;
        }

        if (enableHitSound.get()) {
            if (hitMode.get() == HitMode.PizzaCombo) {
                playSound(PIZZA_COMBOS[comboStep]);
                comboStep = (comboStep + 1) % 7;
            } else if (hitMode.get() == HitMode.Simple) {
                playSound(simpleSound.get().getSound());
            }

            if (mc.player != null) lastHitTick = mc.player.age;
        }

        lastHitTime = currentTime;
        lastAttackedEntity = target;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (lastAttackedEntity != null) {
            if (lastAttackedEntity.isDead() || lastAttackedEntity.getHealth() <= 0) {
                if (enableKillSound.get()) {
                    playSound(killSound.get().getSound());
                }

                comboStep = 0;
                lastAttackedEntity = null;
            } else if (System.currentTimeMillis() - lastHitTime > 2000) {
                lastAttackedEntity = null;
            }
        }
    }

    private void playSound(SoundEvent sound) {
        if (mc.player != null && mc.world != null) {
            mc.world.playSound(mc.player, mc.player.getBlockPos(), sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
    }

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
}
