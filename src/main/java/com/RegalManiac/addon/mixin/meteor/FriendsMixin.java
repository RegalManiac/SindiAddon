package com.RegalManiac.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(value = Friends.class, remap = false)
public abstract class FriendsMixin {
    @Final
    @Shadow private List<Friend> friends;

    /**
     * @author RegalManiac
     * @reason Strict case-sensitive lookup so kois and Kois are different people
     */
    @Overwrite
    public Friend get(String name) {
        if (name == null) return null;

        for (Friend friend : friends) {
            if (friend.getName().equals(name)) {
                return friend;
            }
        }

        return null;
    }
}
