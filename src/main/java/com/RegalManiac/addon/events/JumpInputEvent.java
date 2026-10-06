package com.RegalManiac.addon.events;

public class JumpInputEvent {
    private static final JumpInputEvent INSTANCE = new JumpInputEvent();

    private boolean jumping;
    private boolean overridden;

    public static JumpInputEvent get(boolean jumping) {
        INSTANCE.jumping = jumping;
        INSTANCE.overridden = false;
        return INSTANCE;
    }

    public boolean isJumping() {
        return jumping;
    }

    public void setJumping(boolean jumping) {
        this.jumping = jumping;
        this.overridden = true;
    }

    public boolean isOverridden() {
        return overridden;
    }
}
