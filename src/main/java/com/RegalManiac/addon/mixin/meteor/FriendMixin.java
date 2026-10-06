package com.RegalManiac.addon.mixin.meteor;

import com.RegalManiac.addon.utils.Config;
import com.mojang.util.UndashedUuid;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.utils.network.FailedHttpResponse;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.render.PlayerHeadTexture;
import meteordevelopment.meteorclient.utils.render.PlayerHeadUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.net.http.HttpResponse;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = Friend.class, remap = false)
public abstract class FriendMixin {
    @Shadow public volatile String name;
    @Shadow private volatile @Nullable UUID id;
    @Shadow private volatile @Nullable PlayerHeadTexture headTexture;
    @Shadow private volatile boolean updating;

    /**
     * @author RegalManiac
     * @reason Prevent Mojang API from overwriting user's typed name casing
     */
    @Overwrite
    public void updateInfo() {
        updating = true;
        HttpResponse<Config.APIResponse> res = null;

        if (id != null) {
            res = Http.get("https://sessionserver.mojang.com/session/minecraft/profile/" + UndashedUuid.toString(id))
                .exceptionHandler(e -> MeteorClient.LOG.error("Error while trying to connect session server for friend '{}'", name))
                .sendJsonResponse(Config.APIResponse.class);
        }

        if (res == null || res.statusCode() != 200) {
            res = Http.get("https://api.mojang.com/users/profiles/minecraft/" + name)
                .exceptionHandler(e -> MeteorClient.LOG.error("Error while trying to update info for friend '{}'", name))
                .sendJsonResponse(Config.APIResponse.class);
        }

        if (res != null && res.statusCode() == 200) {
            id = UndashedUuid.fromStringLenient(res.body().id);

            byte[] head = PlayerHeadUtils.fetchHead(id);
            mc.execute(() -> {
                if (head != null) headTexture = new PlayerHeadTexture(head, true);
            });
        }
        else if (!(res instanceof FailedHttpResponse)) {
            id = null;
        }

        updating = false;
    }
}
