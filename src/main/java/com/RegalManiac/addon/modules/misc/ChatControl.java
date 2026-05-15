package com.RegalManiac.addon.modules.misc;

import com.RegalManiac.addon.utils.TextUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.NameProtect;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ChatControl extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFancy = this.settings.createGroup("Fancy Chat");
    private final SettingGroup sgPrefix = this.settings.createGroup("Prefix & Suffix");
    private final SettingGroup sgPrefixControl = this.settings.createGroup("Prefix Control");
    private final SettingGroup sgRainbow = this.settings.createGroup("Rainbow settings");
    private final SettingGroup sgTimestamps = this.settings.createGroup("Timestamps");

    // --- General Group ---
    public final Setting<Boolean> selfHighlight = sgGeneral.add(new BoolSetting.Builder()
        .name("highlight-self")
        .description("Highlights your name in a special color. Compatible with name protect.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sHighlightColor = sgGeneral.add(new ColorSetting.Builder()
        .name("self-highlight-color")
        .defaultValue(new SettingColor(120, 0, 255, 255))
        .visible(selfHighlight::get)
        .build()
    );

    public final Setting<Boolean> friendHighlight = sgGeneral.add(new BoolSetting.Builder()
        .name("highlight-friends")
        .description("Colors your friends names.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> clear = sgGeneral.add(new BoolSetting.Builder()
        .name("clear-chat")
        .description("Makes the chat background transparent.")
        .defaultValue(false)
        .onChanged(v -> handleClear())
        .build()
    );

    public final Setting<Boolean> copyMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("copy-messages")
        .description("Click anywhere on a message to copy it to your clipboard.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> nameClick = sgGeneral.add(new BoolSetting.Builder()
        .name("clickable-names")
        .description("Automatically makes players' names clickable in chat to message them.")
        .defaultValue(true)
        .build()
    );

    public final Setting<String> clickCommand = sgGeneral.add(new StringSetting.Builder()
        .name("click-command")
        .description("The command to prepare when clicking a name (e.g., '/msg ').")
        .defaultValue("/msg ")
        .visible(nameClick::get)
        .build()
    );

    // --- Rainbow Group ---
    public final Setting<Boolean> rainbow = sgRainbow.add(new BoolSetting.Builder()
        .name("rainbow-prefix")
        .description("Enables a proper rainbow prefix.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> synchro = sgRainbow.add(new BoolSetting.Builder()
        .name("synchronized")
        .description("Synchronizes the words.")
        .defaultValue(true)
        .visible(rainbow::get)
        .build()
    );

    public final Setting<Double> rainbowSpeed = sgRainbow.add(new DoubleSetting.Builder()
        .name("rainbow-speed")
        .description("Rainbow speed for the prefix.")
        .defaultValue(0.0035)
        .min(0.0)
        .max(0.02)
        .decimalPlaces(4)
        .visible(rainbow::get)
        .build()
    );

    public final Setting<Double> rainbowWordSpread = sgRainbow.add(new DoubleSetting.Builder()
        .name("rainbow-word-spread")
        .description("Rainbow spread for the prefix inside word.")
        .defaultValue(0.02)
        .min(0.0)
        .sliderMax(0.1)
        .visible(rainbow::get)
        .build()
    );

    public final Setting<Boolean> selfHighlightRainbow = sgRainbow.add(new BoolSetting.Builder()
        .name("self-highlight-rainbow")
        .description("Enables the rainbow for your nickname.")
        .defaultValue(false)
        .visible(selfHighlight::get)
        .build()
    );

    public final Setting<Boolean> friendHighlightRainbow = sgRainbow.add(new BoolSetting.Builder()
        .name("friend-highlight-rainbow")
        .description("Enables the rainbow for friend nickname.")
        .defaultValue(false)
        .visible(friendHighlight::get)
        .build()
    );

    public final Setting<Boolean> meteor = sgRainbow.add(new BoolSetting.Builder()
        .name("apply-to-meteor")
        .description("Enables the rainbow also for the meteor prefix.")
        .defaultValue(false)
        .visible(rainbow::get)
        .build()
    );

    public final Setting<Boolean> rainbowTimestamps = sgRainbow.add(new BoolSetting.Builder()
        .name("apply-to-timestamps")
        .description("Enables the rainbow also for the timestamps.")
        .defaultValue(false)
        .visible(rainbow::get)
        .build()
    );

    public final Setting<Boolean> otherAddons = sgRainbow.add(new BoolSetting.Builder()
        .name("apply-to-other-addons")
        .description("Enables the rainbow also for other addons.")
        .defaultValue(false)
        .visible(rainbow::get)
        .build()
    );

    public final Setting<List<String>> addons = sgRainbow.add(new StringListSetting.Builder()
        .name("addons-list")
        .description("What other addons prefixes to apply the rainbow effect to.")
        .defaultValue(List.of("[BlackOut]", "[Ion]"))
        .visible(() -> rainbow.get() && otherAddons.get())
        .build()
    );

    // --- Timestamps Group ---
    public final Setting<Boolean> timestamps = sgTimestamps.add(new BoolSetting.Builder()
        .name("timestamps")
        .description("Adds client side time stamps to the beginning of chat messages.")
        .defaultValue(false)
        .build()
    );

    private final Setting<TimeFormat> format = sgTimestamps.add(new EnumSetting.Builder<TimeFormat>()
        .name("format")
        .description("What time format to use.")
        .defaultValue(TimeFormat.TWENTY_FOUR_HOUR)
        .visible(timestamps::get)
        .build()
    );

    private final Setting<Boolean> amPm = sgTimestamps.add(new BoolSetting.Builder()
        .name("include-am-pm")
        .description("Whether to add the 'AM' or 'PM' suffix when using 12h format.")
        .defaultValue(true)
        .visible(() -> timestamps.get() && format.get() == TimeFormat.TWELVE_HOUR)
        .build()
    );

    private final Setting<Boolean> timestampsSeconds = sgTimestamps.add(new BoolSetting.Builder()
        .name("include-seconds")
        .description("Whether to add seconds to the timestamps or not.")
        .defaultValue(false)
        .visible(timestamps::get)
        .build()
    );

    private final Setting<SettingColor> timestampsColor = sgTimestamps.add(new ColorSetting.Builder()
        .name("timestamp-color")
        .description("Color of the timestamps.")
        .defaultValue(new SettingColor(170, 170, 170, 255))
        .visible(() -> timestamps.get() && (!rainbow.get() || !rainbowTimestamps.get()))
        .build()
    );

    public final Setting<String> lBracketTimestamps = sgTimestamps.add(new StringSetting.Builder()
        .name("left-bracket")
        .description("Left bracket for the timestamps.")
        .defaultValue("<")
        .visible(timestamps::get)
        .build()
    );

    public final Setting<String> rBracketTimestamps = sgTimestamps.add(new StringSetting.Builder()
        .name("right-bracket")
        .description("Right bracket for the timestamps.")
        .defaultValue(">")
        .visible(timestamps::get)
        .build()
    );

    private final Setting<SettingColor> timestampsBracketsColor = sgTimestamps.add(new ColorSetting.Builder()
        .name("brackets-color")
        .description("Color of the timestamps' brackets.")
        .defaultValue(new SettingColor(170, 170, 170, 255))
        .visible(() -> timestamps.get() && (!rainbow.get() || !rainbowTimestamps.get()))
        .build()
    );

    // --- Prefix Control Group ---
    public final Setting<String> customPrefix = sgPrefixControl.add(new StringSetting.Builder()
        .name("custom-prefix")
        .description("The prefix for modules.")
        .defaultValue("Sindi")
        .build()
    );

    private final Setting<SettingColor> customPrefixColor = sgPrefixControl.add(new ColorSetting.Builder()
        .name("prefix-color")
        .description("Color of the prefix text.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(() -> !rainbow.get())
        .onChanged(this::changePrefix)
        .build()
    );

    public final Setting<String> lBracket = sgPrefixControl.add(new StringSetting.Builder()
        .name("left-bracket")
        .description("Left bracket for the prefix.")
        .defaultValue("[")
        .build()
    );

    public final Setting<String> rBracket = sgPrefixControl.add(new StringSetting.Builder()
        .name("right-bracket")
        .description("Right bracket for the prefix.")
        .defaultValue("]")
        .build()
    );

    private final Setting<SettingColor> prefixBracketsColor = sgPrefixControl.add(new ColorSetting.Builder()
        .name("brackets-color")
        .description("Color of the brackets.")
        .defaultValue(new SettingColor(170, 170, 170, 255))
        .visible(() -> !rainbow.get())
        .onChanged(this::changePrefix)
        .build()
    );

    private final Setting<Boolean> overwriteMeteor = sgPrefixControl.add(new BoolSetting.Builder()
        .name("always-custom-prefix")
        .description("Whether to use the custom prefix also for meteor modules.")
        .defaultValue(false)
        .onChanged(v -> changePrefix())
        .build()
    );

    public final Setting<String> meteorText = sgPrefixControl.add(new StringSetting.Builder()
        .name("meteor-text")
        .description("The text for the meteor prefix.")
        .defaultValue("Meteor")
        .visible(() -> !overwriteMeteor.get())
        .onChanged(v -> changeMeteorPrefix())
        .build()
    );

    private final Setting<SettingColor> meteorColor = sgPrefixControl.add(new ColorSetting.Builder()
        .name("meteor-color")
        .description("Color of the meteor prefix text.")
        .defaultValue(new SettingColor(145, 61, 226, 255))
        .visible(() -> !overwriteMeteor.get() && (!meteor.get() || !rainbow.get()))
        .onChanged(v -> changeMeteorPrefix())
        .build()
    );

    public final Setting<String> lBracketMeteor = sgPrefixControl.add(new StringSetting.Builder()
        .name("meteor-left-bracket")
        .description("Left bracket for the meteor prefix.")
        .defaultValue("[")
        .visible(() -> !overwriteMeteor.get())
        .onChanged(v -> changeMeteorPrefix())
        .build()
    );

    public final Setting<String> rBracketMeteor = sgPrefixControl.add(new StringSetting.Builder()
        .name("meteor-right-bracket")
        .description("Right bracket for the meteor prefix.")
        .defaultValue("]")
        .visible(() -> !overwriteMeteor.get())
        .onChanged(v -> changeMeteorPrefix())
        .build()
    );

    private final Setting<SettingColor> meteorPrefixBracketsColor = sgPrefixControl.add(new ColorSetting.Builder()
        .name("meteor-brackets-color")
        .description("Color of the meteor brackets.")
        .defaultValue(new SettingColor(170, 170, 170, 255))
        .visible(() -> !overwriteMeteor.get() && (!meteor.get() || !rainbow.get()))
        .onChanged(v -> changeMeteorPrefix())
        .build()
    );

    // --- Fancy Group ---
    private final Setting<Boolean> fancy = sgFancy.add(new BoolSetting.Builder()
        .name("fancy-chat")
        .description("Makes your messages ғᴀɴᴄʏ!")
        .defaultValue(false)
        .build()
    );

    private final Setting<TextUtils.Fonts> font = sgFancy.add(new EnumSetting.Builder<TextUtils.Fonts>()
        .name("font")
        .description("Which font to use")
        .defaultValue(TextUtils.Fonts.DEFAULT)
        .visible(fancy::get)
        .build()
    );

    // --- Prefix Group ---
    private final Setting<Boolean> prefix = sgPrefix.add(new BoolSetting.Builder()
        .name("prefix")
        .description("Adds a prefix to your chat messages.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> prefixRandom = sgPrefix.add(new BoolSetting.Builder()
        .name("random")
        .description("Uses a random number as your prefix.")
        .defaultValue(false)
        .visible(prefix::get)
        .build()
    );

    private final Setting<String> prefixText = sgPrefix.add(new StringSetting.Builder()
        .name("text")
        .description("The text to add as your prefix.")
        .defaultValue("> ")
        .visible(() -> prefix.get() && !prefixRandom.get())
        .build()
    );

    private final Setting<TextUtils.Fonts> prefixFont = sgPrefix.add(new EnumSetting.Builder<TextUtils.Fonts>()
        .name("prefix-font")
        .description("Set a font for your prefix.")
        .defaultValue(TextUtils.Fonts.DEFAULT)
        .visible(() -> prefix.get() && !prefixRandom.get())
        .build()
    );

    // --- Suffix Group ---
    private final Setting<Boolean> suffix = sgPrefix.add(new BoolSetting.Builder()
        .name("suffix")
        .description("Adds a suffix to your chat messages.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> suffixRandom = sgPrefix.add(new BoolSetting.Builder()
        .name("random-suffix")
        .description("Uses a random number as your suffix.")
        .defaultValue(false)
        .visible(suffix::get)
        .build()
    );

    private final Setting<String> suffixText = sgPrefix.add(new StringSetting.Builder()
        .name("text-suffix")
        .description("The text to add as your suffix.")
        .defaultValue(" | Sindi")
        .visible(() -> suffix.get() && !suffixRandom.get())
        .build()
    );

    private final Setting<TextUtils.Fonts> suffixFont = sgPrefix.add(new EnumSetting.Builder<TextUtils.Fonts>()
        .name("suffix-font")
        .description("Set a font for your suffix.")
        .defaultValue(TextUtils.Fonts.DEFAULT)
        .visible(() -> suffix.get() && !suffixRandom.get())
        .build()
    );

    public ChatControl() {
        super(Categories.Misc, "chat-control", "Grants you full control over your chat experience.");
    }

    @EventHandler(priority = 199)
    private void onMessageRecieve(ReceiveMessageEvent event) {
        if (!this.timestamps.get() && !this.selfHighlight.get() && !this.friendHighlight.get() && !this.nameClick.get() && !this.copyMessages.get()) return;

        Text message = event.getMessage();

        StringBuilder rawBuilder = new StringBuilder();
        message.asOrderedText().accept((i, style, codePoint) -> {
            rawBuilder.appendCodePoint(codePoint);
            return true;
        });
        String rawText = rawBuilder.toString();

        net.minecraft.text.TextColor[] highlightColors = new net.minecraft.text.TextColor[rawText.length()];
        net.minecraft.text.ClickEvent[] clickEvents = new net.minecraft.text.ClickEvent[rawText.length()];
        net.minecraft.text.HoverEvent[] hoverEvents = new net.minecraft.text.HoverEvent[rawText.length()];

        if (this.copyMessages.get()) {
            net.minecraft.text.ClickEvent copyEvent = new net.minecraft.text.ClickEvent.CopyToClipboard(rawText);
            net.minecraft.text.HoverEvent copyHover = new net.minecraft.text.HoverEvent.ShowText(Text.literal("Copy Message").formatted(Formatting.GRAY));
            for (int k = 0; k < rawText.length(); k++) {
                clickEvents[k] = copyEvent;
                hoverEvents[k] = copyHover;
            }
        }

        if (this.nameClick.get()) {
            String cmd = this.clickCommand.get();

            java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("^<([a-zA-Z0-9_]{3,16})>").matcher(rawText);
            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("^\\[([a-zA-Z0-9_]{3,16})\\]").matcher(rawText);
            java.util.regex.Matcher m3 = java.util.regex.Pattern.compile("^([a-zA-Z0-9_]{3,16})\\s?[>:]").matcher(rawText);

            String senderName = null;
            int sStart = -1;
            if (m1.find()) { senderName = m1.group(1); sStart = m1.start(1); }
            else if (m2.find()) { senderName = m2.group(1); sStart = m2.start(1); }
            else if (m3.find()) { senderName = m3.group(1); sStart = m3.start(1); }

            if (senderName != null && sStart > -1) {
                net.minecraft.text.ClickEvent ce = new net.minecraft.text.ClickEvent.SuggestCommand(cmd + senderName + " ");
                net.minecraft.text.HoverEvent he = new net.minecraft.text.HoverEvent.ShowText(Text.literal("Send Message").formatted(Formatting.GRAY));
                for (int k = 0; k < senderName.length(); k++) {
                    clickEvents[sStart + k] = ce;
                    hoverEvents[sStart + k] = he;
                }
            }

            if (this.mc.getNetworkHandler() != null) {
                for (net.minecraft.client.network.PlayerListEntry entry : this.mc.getNetworkHandler().getPlayerList()) {
                    String name = entry.getProfile().name();
                    if (name == null || name.length() < 3) continue;
                    int idx = rawText.indexOf(name);
                    while (idx > -1) {
                        if (isExactMatch(rawText, name, idx)) {
                            net.minecraft.text.ClickEvent ce = new net.minecraft.text.ClickEvent.SuggestCommand(cmd + name + " ");
                            net.minecraft.text.HoverEvent he = new net.minecraft.text.HoverEvent.ShowText(Text.literal("Send Message").formatted(Formatting.GRAY));
                            for (int k = 0; k < name.length(); k++) {
                                clickEvents[idx + k] = ce;
                                hoverEvents[idx + k] = he;
                            }
                        }
                        idx = rawText.indexOf(name, idx + 1);
                    }
                }
            }
        }

        if (this.selfHighlight.get() && this.mc.player != null) {
            String selfName = Modules.get().get(NameProtect.class).getName(this.mc.player.getStringifiedName());
            if (selfName != null) {
                SettingColor c = this.sHighlightColor.get();
                net.minecraft.text.TextColor selfColor = net.minecraft.text.TextColor.fromRgb((c.r << 16) | (c.g << 8) | c.b);
                int idx = rawText.indexOf(selfName);
                while (idx > -1) {
                    if (isExactMatch(rawText, selfName, idx)) {
                        for (int k = 0; k < selfName.length(); k++) highlightColors[idx + k] = selfColor;
                    }
                    idx = rawText.indexOf(selfName, idx + 1);
                }
            }
        }

        if (this.friendHighlight.get()) {
            SettingColor fC = Config.get().friendColor.get();
            net.minecraft.text.TextColor friendColor = net.minecraft.text.TextColor.fromRgb((fC.r << 16) | (fC.g << 8) | fC.b);
            for (Friend friend : Friends.get()) {
                int idx = rawText.indexOf(friend.name);
                while (idx > -1) {
                    if (isExactMatch(rawText, friend.name, idx)) {
                        for (int k = 0; k < friend.name.length(); k++) highlightColors[idx + k] = friendColor;
                    }
                    idx = rawText.indexOf(friend.name, idx + 1);
                }
            }
        }

        MutableText parsed = Text.literal("");
        class RebuildState {
            StringBuilder buffer = new StringBuilder();
            net.minecraft.text.Style currentStyle = null;

            void flush() {
                if (!buffer.isEmpty()) {
                    parsed.append(Text.literal(buffer.toString()).setStyle(currentStyle));
                    buffer.setLength(0);
                }
            }
        }
        RebuildState state = new RebuildState();
        java.util.concurrent.atomic.AtomicInteger charIdx = new java.util.concurrent.atomic.AtomicInteger(0);

        message.asOrderedText().accept((i, originalStyle, codePoint) -> {
            int index = charIdx.getAndAdd(Character.charCount(codePoint));
            net.minecraft.text.Style targetStyle = originalStyle;

            if (index < highlightColors.length && highlightColors[index] != null) {
                targetStyle = targetStyle.withColor(highlightColors[index]);
            }
            if (index < clickEvents.length && clickEvents[index] != null) {
                targetStyle = targetStyle.withClickEvent(clickEvents[index]);
                targetStyle = targetStyle.withHoverEvent(hoverEvents[index]);
            }

            if (state.currentStyle == null) {
                state.currentStyle = targetStyle;
            } else if (!state.currentStyle.equals(targetStyle)) {
                state.flush();
                state.currentStyle = targetStyle;
            }

            state.buffer.appendCodePoint(codePoint);
            return true;
        });
        state.flush();

        if (this.timestamps.get()) {
            MutableText finalMsg = Text.literal("");
            finalMsg.append(this.timestampText());
            finalMsg.append(parsed);
            event.setMessage(finalMsg);
        } else {
            event.setMessage(parsed);
        }
    }

    @EventHandler(priority = 201)
    private void onMessageSend(SendMessageEvent event) {
        StringBuilder builder = new StringBuilder();
        if (this.prefix.get()) {
            builder.append(this.getAffix(this.prefixText.get(), this.prefixFont.get(), this.prefixRandom.get())).append(" ");
        }

        String message = event.message;

        if (this.fancy.get()) {
            message = this.font.get().apply(message);
        }

        builder.append(message);
        if (this.suffix.get()) {
            builder.append(" ").append(this.getAffix(this.suffixText.get(), this.suffixFont.get(), this.suffixRandom.get()));
        }

        event.message = builder.toString();
    }

    private boolean isExactMatch(String text, String word, int index) {
        if (index < 0) return false;
        boolean startOk = index == 0 || !isWordChar(text.charAt(index - 1));
        int endIdx = index + word.length();
        boolean endOk = endIdx == text.length() || !isWordChar(text.charAt(endIdx));
        return startOk && endOk;
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public MutableText timestampText() {
        StringBuilder timeText = new StringBuilder();
        if (this.format.get() == TimeFormat.TWENTY_FOUR_HOUR) {
            timeText.append("HH:mm");
        } else {
            timeText.append("hh:mm");
        }

        if (this.timestampsSeconds.get()) {
            timeText.append(":ss");
        }

        if (this.format.get() == TimeFormat.TWELVE_HOUR && this.amPm.get()) {
            timeText.append(" aa");
        }

        return TextUtils.coloredText(this.lBracketTimestamps.get(), this.timestampsBracketsColor.get())
            .append(TextUtils.coloredText(new SimpleDateFormat(timeText.toString()).format(new Date()), this.timestampsColor.get()))
            .append(TextUtils.coloredText(this.rBracketTimestamps.get(), this.timestampsBracketsColor.get()))
            .append(" ");
    }

    private String getAffix(String text, TextUtils.Fonts font, boolean random) {
        return random ? String.format("(%03d) ", Utils.random(0, 1000)) : font.apply(text);
    }

    private void handleClear() {
        if (this.clear.get()) {
            this.mc.options.getTextBackgroundOpacity().setValue(0.0);
        } else {
            this.mc.options.getTextBackgroundOpacity().setValue(0.5);
        }
    }

    private void changePrefix(SettingColor color) { this.changePrefix(""); }
    private void changePrefix() { this.changePrefix(""); }

    private void changePrefix(String string) {
        ChatUtils.registerCustomPrefix("com.RegalManiac.addon", this::prefix);
        if (this.overwriteMeteor.get()) {
            ChatUtils.registerCustomPrefix("meteordevelopment", this::prefix);
        } else {
            ChatUtils.registerCustomPrefix("meteordevelopment", this::customMeteorPrefix);
        }
    }

    private void changeMeteorPrefix() {
        ChatUtils.registerCustomPrefix("meteordevelopment", this::customMeteorPrefix);
    }

    private MutableText prefix() {
        MutableText prefix = TextUtils.coloredText(this.lBracket.get(), this.prefixBracketsColor.get());
        prefix.append(TextUtils.coloredText(this.customPrefix.get(), this.customPrefixColor.get()));
        prefix.append(TextUtils.coloredText(this.rBracket.get(), this.prefixBracketsColor.get()));
        return prefix.append(" ");
    }

    private MutableText customMeteorPrefix() {
        MutableText prefix = TextUtils.coloredText(this.lBracketMeteor.get(), this.meteorPrefixBracketsColor.get());
        prefix.append(TextUtils.coloredText(this.meteorText.get(), this.meteorColor.get()));
        prefix.append(TextUtils.coloredText(this.rBracketMeteor.get(), this.meteorPrefixBracketsColor.get()));
        return prefix.append(" ");
    }

    private MutableText defaultPrefix() {
        MutableText prefix = Text.literal("[").formatted(Formatting.GRAY);
        prefix.append(TextUtils.coloredText("Meteor", MeteorClient.ADDON.color));
        prefix.append(Text.literal("]").formatted(Formatting.GRAY));
        return prefix.append(" ");
    }

    @Override
    public void onActivate() {
        this.changePrefix();
        this.handleClear();
    }

    @Override
    public void onDeactivate() {
        ChatUtils.registerCustomPrefix("com.RegalManiac.addon", this::defaultSindiPrefix);
        ChatUtils.registerCustomPrefix("meteordevelopment", this::defaultPrefix);
        if (this.mc.options.getTextBackgroundOpacity().getValue() == 0.0) {
            this.mc.options.getTextBackgroundOpacity().setValue(0.5);
        }
    }

    private MutableText defaultSindiPrefix() {
        MutableText text = Text.empty();
        text.append(Text.literal("[").formatted(Formatting.GRAY));
        text.append(Text.literal("Sindi").formatted(Formatting.DARK_RED));
        text.append(Text.literal("] ").formatted(Formatting.GRAY));
        return text;
    }

    public enum TimeFormat {
        TWENTY_FOUR_HOUR("24 hour"),
        TWELVE_HOUR("12 hour");
        private final String title;
        TimeFormat(String title) { this.title = title; }
        @Override public String toString() { return this.title; }
    }
}
