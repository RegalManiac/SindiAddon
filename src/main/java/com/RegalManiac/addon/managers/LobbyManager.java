package com.RegalManiac.addon.managers;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.dialog.DialogActionButtonData;
import net.minecraft.dialog.DialogCommonData;
import net.minecraft.dialog.action.DynamicCustomDialogAction;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.dialog.type.DialogInput;
import net.minecraft.dialog.type.MultiActionDialog;
import net.minecraft.dialog.type.SimpleDialog;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.CustomClickActionC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.s2c.common.ShowDialogS2CPacket;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LobbyManager {
    private static final String[] REGISTER_KEYWORDS = {
        "/register", "/reg", "register", "зарегистрируйтесь", "/рег", "создайте пароль"
    };
    private static final String[] LOGIN_KEYWORDS = {
        "/login", "/l ", "login", "авторизуйтесь", "войдите", "/логин", "пароль"
    };
    private static final String[] AUTH_INDICATORS = {
        "please", "type", "use", "welcome", "введите", "используйте"
    };

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean inLobby = false;
    private static boolean inAuthDialog = false;
    private static ShowDialogS2CPacket cachedDialogPacket = null;
    private static ClientConnection lastConnection = null;
    private static boolean subscribed = false;

    public static void init() {
        if (!subscribed) {
            MeteorClient.EVENT_BUS.subscribe(LobbyManager.class);
            subscribed = true;
        }
    }

    @EventHandler
    private static void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ShowDialogS2CPacket packet) {
            if (packet.dialog() == null) return;
            Dialog dialog;
            try {
                dialog = packet.dialog().value();
            } catch (Exception e) { return; }
            if (dialog == null || dialog.common() == null) return;

            DialogCommonData common = dialog.common();
            String titleLower = common.title() != null ? common.title().getString().toLowerCase() : "";

            boolean isAuth = titleLower.contains("вход") || titleLower.contains("login") ||
                titleLower.contains("авториз") || titleLower.contains("auth") ||
                titleLower.contains("пароль");

            if (!isAuth && common.body() != null) {
                StringBuilder bodyText = new StringBuilder();
                for (DialogBody b : common.body()) bodyText.append(b.toString()).append(" ");
                String bodyStr = bodyText.toString().toLowerCase();
                if (bodyStr.contains("пароль") || bodyStr.contains("password") || bodyStr.contains("введи")) {
                    isAuth = true;
                }
            }

            if (isAuth) {
                event.cancel();
                inLobby = true;
                inAuthDialog = true;
                cachedDialogPacket = packet;
                lastConnection = event.connection;
            }
        }
    }

    public static void sendAuthResponse(String cleanPassword) {
        if (!inAuthDialog || cachedDialogPacket == null) return;

        Dialog dialog;
        try { dialog = cachedDialogPacket.dialog().value(); } catch (Exception e) { return; }
        DialogCommonData common = dialog.common();

        String passwordKey = "password";
        if (common.inputs() != null && !common.inputs().isEmpty()) {
            boolean found = false;
            for (DialogInput input : common.inputs()) {
                if (input.key().toLowerCase().contains("pass")) {
                    passwordKey = input.key();
                    found = true;
                    break;
                }
            }
            if (!found) passwordKey = common.inputs().getFirst().key();
        }

        List<DialogActionButtonData> actionButtons = new ArrayList<>();
        if (dialog instanceof MultiActionDialog multiAction) {
            actionButtons.addAll(multiAction.actions());
            multiAction.exitAction().ifPresent(actionButtons::add);
        } else if (dialog instanceof SimpleDialog simpleDialog) {
            actionButtons.addAll(simpleDialog.getButtons());
        }

        Identifier targetActionId = null;
        Optional<NbtCompound> additions = Optional.empty();

        for (DialogActionButtonData btn : actionButtons) {
            if (btn.action().isPresent() && btn.action().get() instanceof DynamicCustomDialogAction(
                Identifier id, Optional<NbtCompound> additions1
            )) {
                String label = btn.data() != null && btn.data().label() != null ? btn.data().label().getString().toLowerCase() : "";

                if (id.getPath().toLowerCase().contains("cancel") || label.contains("отключ") || label.contains("выйти")) {
                    continue;
                }
                targetActionId = id;
                additions = additions1;
                break;
            }
        }

        if (targetActionId == null) return;

        NbtCompound payload = additions.map(NbtCompound::copy).orElseGet(NbtCompound::new);
        payload.putString(passwordKey, cleanPassword);

        CustomClickActionC2SPacket responsePacket = new CustomClickActionC2SPacket(targetActionId, Optional.of(payload));

        try {
            if (lastConnection != null && lastConnection.isOpen()) {
                lastConnection.send(responsePacket);
            } else if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(responsePacket);
            }
        } finally {
            reset();
        }
    }

    @EventHandler
    private static void onMessageReceive(ReceiveMessageEvent event) {
        if (event.getMessage() == null) return;
        if (isAuthMessage(event.getMessage().getString())) {
            inLobby = true;
        }
    }

    @EventHandler
    private static void onPacketSend(PacketEvent.Send event) {
        String commandOrMsg = null;
        if (event.packet instanceof CommandExecutionC2SPacket(String command)) {
            commandOrMsg = command.toLowerCase();
        } else if (event.packet instanceof ChatMessageC2SPacket packet) {
            commandOrMsg = packet.chatMessage().toLowerCase();
            if (commandOrMsg.startsWith("/")) commandOrMsg = commandOrMsg.substring(1);
        }

        if (commandOrMsg != null) {
            if (commandOrMsg.startsWith("login") || commandOrMsg.startsWith("l ") || commandOrMsg.startsWith("reg")) {
                reset();
            }
        }
    }

    public static boolean isInLobby() {
        init();
        if (inLobby) return true;

        if (mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
            if (checkChatHistory()) {
                inLobby = true;
                return true;
            }
        }
        return false;
    }

    public static boolean isInAuthDialog() {
        return inAuthDialog;
    }

    public static ClientConnection getLastConnection() {
        return lastConnection;
    }

    @SuppressWarnings("unchecked")
    private static boolean checkChatHistory() {
        try {
            Field messagesField = mc.inGameHud.getChatHud().getClass().getDeclaredField("messages");
            messagesField.setAccessible(true);
            List<ChatHudLine> messages = (List<ChatHudLine>) messagesField.get(mc.inGameHud.getChatHud());

            if (messages == null || messages.isEmpty()) return false;
            int count = Math.min(messages.size(), 15);
            for (int i = 0; i < count; i++) {
                ChatHudLine line = messages.get(i);
                if (line != null && line.content() != null) {
                    if (isAuthMessage(line.content().getString())) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isAuthMessage(String rawMsg) {
        if (rawMsg == null || rawMsg.isEmpty()) return false;
        String msg = rawMsg.replaceAll("§[0-9a-fk-or]", "").toLowerCase().trim();

        boolean foundRegister = false;
        for (String key : REGISTER_KEYWORDS) if (msg.contains(key)) { foundRegister = true; break; }

        boolean foundLogin = false;
        if (!foundRegister) {
            for (String key : LOGIN_KEYWORDS) if (msg.contains(key)) { foundLogin = true; break; }
        }

        if (!foundRegister && !foundLogin) return false;

        for (String context : AUTH_INDICATORS) if (msg.contains(context)) return true;
        return msg.contains("/") || msg.contains("!");
    }

    public static void reset() {
        inLobby = false;
        inAuthDialog = false;
        cachedDialogPacket = null;
    }
}
