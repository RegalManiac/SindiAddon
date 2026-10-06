package com.RegalManiac.addon.events;

import meteordevelopment.meteorclient.events.Cancellable;
import net.minecraft.entity.player.PlayerEntity;

public class TotemPopEvent extends Cancellable {
    private final PlayerEntity entity;
    private final int pops;

    public TotemPopEvent(PlayerEntity entity, int pops) {
        this.entity = entity;
        this.pops = pops;
    }

    public PlayerEntity getEntity() {
        return this.entity;
    }

    public int getPops() {
        return this.pops;
    }
}
