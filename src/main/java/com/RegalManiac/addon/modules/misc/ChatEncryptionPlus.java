package com.RegalManiac.addon.modules.misc;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Original code made by cqb13
 */
public class ChatEncryptionPlus extends Module {
    public static ChatEncryptionPlus INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgChat = settings.createGroup("Chat");
    private final SettingGroup sgMsg = settings.createGroup("Private Messages");

    private final Setting<String> key = sgGeneral.add(new StringSetting.Builder().name("key").defaultValue("Cathack").build());
    private final Setting<String> secretKey = sgGeneral.add(new StringSetting.Builder().name("secret-key").defaultValue("Key").build());
    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder().name("prefix").defaultValue("BOG:").build());
    private final Setting<Boolean> chatEncryption = sgChat.add(new BoolSetting.Builder().name("chat-encryption").defaultValue(true).build());
    private final Setting<Boolean> replaceOriginal = sgChat.add(new BoolSetting.Builder().name("replace-original").defaultValue(false).build());
    public final Setting<SettingColor> feedbackColor = sgChat.add(new ColorSetting.Builder().name("feedback-color").defaultValue(new SettingColor(73, 107, 190)).build());
    private final Setting<Boolean> enableMsgEncryption = sgMsg.add(new BoolSetting.Builder().name("msg-encryption").defaultValue(true).build());
    private final Setting<Boolean> msgSelectedPlayers = sgMsg.add(new BoolSetting.Builder().name("msg-selected-players").defaultValue(false).visible(enableMsgEncryption::get).build());
    private final Setting<List<String>> playersList = sgMsg.add(new StringListSetting.Builder().name("players-list").defaultValue(new ArrayList<>(0)).visible(() -> enableMsgEncryption.get() && msgSelectedPlayers.get()).build());

    public ChatEncryptionPlus() {
        super(Categories.Misc, "chat-encryption-+", "Encrypt messages. Include msg encryption.");
        INSTANCE = this;
    }

    public String processCommand(String command) {
        if (!isActive() || !enableMsgEncryption.get()) return command;

        String[] parts = command.split(" ", 2);
        if (parts.length < 2) return command;

        String cmd = parts[0].toLowerCase();
        String args = parts[1];

        try {
            SecretKey aesKey = deriveKeyFromStrings(key.get(), secretKey.get());

            if (cmd.matches("msg|tell|w|m|pm|whisper")) {
                String[] argParts = args.split(" ", 2);
                if (argParts.length < 2) return command;

                String target = argParts[0];
                String message = argParts[1];

                if (msgSelectedPlayers.get()) {
                    if (playersList.get().stream().noneMatch(f -> f.equalsIgnoreCase(target))) return command;
                }

                String encrypted = encrypt(message, aesKey);
                String result = cmd + " " + target + " " + prefix.get() + encrypted;

                if (result.length() > 256) {
                    ChatUtils.warning("Message is too long after encryption. Canceled.", result.length());
                    return null;
                }
                return result;
            }

            if (cmd.matches("r|reply")) {
                String encrypted = encrypt(args, aesKey);
                String result = cmd + " " + prefix.get() + encrypted;

                if (result.length() > 256) {
                    ChatUtils.warning("Message is too long after encryption. Canceled.", result.length());
                    return null;
                }
                return result;
            }
        } catch (Exception ignored) {}

        return command;
    }

    public String processChatMessage(String message) {
        if (!isActive() || !chatEncryption.get() || message.startsWith("/")) return message;

        try {
            SecretKey aesKey = deriveKeyFromStrings(key.get(), secretKey.get());
            String encrypted = encrypt(message, aesKey);
            String result = prefix.get() + encrypted;

            if (result.length() > 256) {
                ChatUtils.warning("Message is too long after encryption. Canceled.", result.length());
                return null;
            }
            return result;
        } catch (Exception ignored) {}

        return message;
    }

    @EventHandler
    public void onReceiveMessage(ReceiveMessageEvent event) {
        Text originalText = event.getMessage();
        String fullRaw = originalText.getString();
        String p = prefix.get();

        if (!fullRaw.contains(p)) return;

        try {
            String encryptedPart = fullRaw.substring(fullRaw.indexOf(p) + p.length());
            String decrypted = decrypt(encryptedPart, deriveKeyFromStrings(key.get(), secretKey.get()));

            if (decrypted != null) {
                Style feedbackStyle = Style.EMPTY.withColor(TextColor.fromRgb(feedbackColor.get().getPacked()));

                if (replaceOriginal.get()) {
                    MutableText result = Text.empty();
                    boolean prefixFound = false;

                    for (Text part : originalText.getWithStyle(Style.EMPTY)) {
                        String content = part.getString();

                        if (!prefixFound) {
                            if (content.contains(p)) {
                                prefixFound = true;
                                int index = content.indexOf(p);
                                String before = content.substring(0, index);
                                if (!before.isEmpty()) {
                                    result.append(Text.literal(before).setStyle(part.getStyle()));
                                }
                                result.append(Text.literal(decrypted).setStyle(part.getStyle()));
                            } else {
                                result.append(part.copy());
                            }
                        }
                    }
                    result.append(Text.literal(" [Decrypted]").setStyle(feedbackStyle));
                    event.setMessage(result);

                } else {
                    event.setMessage(originalText.copy().append(
                        Text.literal(" ").append(
                            Text.literal("[Encrypted]").setStyle(feedbackStyle.withHoverEvent(
                                new HoverEvent.ShowText(Text.literal(decrypted).setStyle(feedbackStyle))
                            ))
                        )
                    ));
                }
            }
        } catch (Exception ignored) {}
    }

    public static SecretKey deriveKeyFromStrings(String k1, String k2) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest((k1 + ":" + k2).getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(Arrays.copyOf(keyBytes, 16), "AES");
    }

    public static String encrypt(String plaintext, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    public static String decrypt(String input, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return new String(cipher.doFinal(Base64.getDecoder().decode(input)), StandardCharsets.UTF_8);
    }
}
