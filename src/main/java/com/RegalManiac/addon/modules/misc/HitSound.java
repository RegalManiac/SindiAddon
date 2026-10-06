package com.RegalManiac.addon.modules.misc;

import com.RegalManiac.addon.utils.sound.SoundUtils;
import com.RegalManiac.addon.utils.sound.SoundUtils.HitMode;
import com.RegalManiac.addon.utils.sound.SoundUtils.KillSound;
import com.RegalManiac.addon.utils.sound.SoundUtils.SimpleSound;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

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

    public HitSound() {
        super(Categories.Misc, "hit-sound", "Plays custom sounds on hitting or killing entities.");
    }

    @Override
    public void onActivate() {
        SoundUtils.resetHitSoundState();
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        SoundUtils.handleAttackEntity(
            event.entity,
            entities,
            enableHitSound.get(),
            hitMode.get(),
            comboTimeout.get(),
            simpleSound.get()
        );
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        SoundUtils.handleKillSoundTick(
            enableKillSound.get(),
            killSound.get()
        );
    }
}
