package com.RegalManiac.addon.modules.misc;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;

import java.util.*;

public class AutoLoginPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ticks")
        .defaultValue(40)
        .min(0)
        .build()
    );

    private final Setting<List<String>> accounts = sgGeneral.add(new StringListSetting.Builder()
        .name("accounts")
        .defaultValue(new ArrayList<>())
        .visible(() -> false)
        .build()
    );

    private static final String[] REGISTER_KEYWORDS = {
        "/register", "/reg", "register", "зарегистрируйтесь", "/рег", "создайте пароль"
    };
    private static final String[] LOGIN_KEYWORDS = {
        "/login", "/l ", "login", "авторизуйтесь", "войдите", "/логин", "пароль"
    };
    private static final String[] AUTH_PROMPT_INDICATORS = {
        "please", "type", "use", "welcome", "введите", "используйте"
    };

    private final List<String> messageQueue = new LinkedList<>();
    private int timer = 0;
    private boolean inLobby = false;

    public AutoLoginPlus() {
        super(Categories.Misc, "auto-login-+", "Login module based on server + nickname + password.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        fillTable(theme, table);
        return table;
    }

    private void fillTable(GuiTheme theme, WTable table) {
        table.clear();

        table.add(theme.label(""));
        table.row();

        List<String> list = new ArrayList<>(accounts.get());

        if (!list.isEmpty()) {
            table.add(theme.label("SERVER IP"));
            table.add(theme.label("NICKNAME"));
            table.add(theme.label("COMMAND"));
            table.add(theme.label(""));
            table.row();
        }

        for (int i = 0; i < list.size(); i++) {
            int index = i;
            String[] data = list.get(i).split("\\|", 3);

            String valIp = data.length > 0 ? data[0] : "";
            String valNick = data.length > 1 ? data[1] : "";
            String valPass = data.length > 2 ? data[2] : "/l ";

            WTextBox wIp = table.add(theme.textBox(valIp)).minWidth(150).expandX().widget();
            WTextBox wNick = table.add(theme.textBox(valNick)).minWidth(120).expandX().widget();
            WTextBox wPass = table.add(theme.textBox(valPass)).minWidth(150).expandX().widget();

            Runnable update = () -> {
                list.set(index, wIp.get() + "|" + wNick.get() + "|" + wPass.get());
                accounts.set(list);
            };

            wIp.action = update;
            wNick.action = update;
            wPass.action = update;

            WButton del = table.add(theme.button(" × ")).widget();
            del.action = () -> {
                list.remove(index);
                accounts.set(list);
                fillTable(theme, table);
            };
            table.row();
        }

        table.add(theme.label(""));
        table.row();

        WButton addEmpty = table.add(theme.button(" + Add Row ")).expandX().widget();
        addEmpty.action = () -> {
            list.add("||/l ");
            accounts.set(list);
            fillTable(theme, table);
        };

        WButton autoAdd = table.add(theme.button(" + Current Data ")).expandX().widget();
        autoAdd.action = () -> {
            if (mc.player != null) {
                list.add(Utils.getWorldName() + "|" + mc.player.getGameProfile().name() + "|/l ");
                accounts.set(list);
                fillTable(theme, table);
            }
        };
        table.row();
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString().replaceAll("§[0-9a-fk-or]", "").toLowerCase().trim();

        boolean foundLogin = false;
        boolean foundRegister = false;

        for (String key : REGISTER_KEYWORDS) {
            if (msg.contains(key)) {
                foundRegister = true;
                break;
            }
        }

        if (!foundRegister) {
            for (String key : LOGIN_KEYWORDS) {
                if (msg.contains(key)) {
                    foundLogin = true;
                    break;
                }
            }
        }

        boolean hasContext = false;
        for (String context : AUTH_PROMPT_INDICATORS) {
            if (msg.contains(context)) {
                hasContext = true;
                break;
            }
        }

        boolean isCommand = msg.contains("/") || msg.contains("!");

        if ((foundRegister || foundLogin) && (hasContext || isCommand)) {
            inLobby = true;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof CommandExecutionC2SPacket packet) {
            String cmd = packet.command().toLowerCase();
            if (cmd.startsWith("login") || cmd.startsWith("l ") || cmd.startsWith("reg")) {
                inLobby = false;
                timer = 0;
            }
        } else if (event.packet instanceof ChatMessageC2SPacket packet) {
            String msg = packet.chatMessage().toLowerCase();
            if (msg.startsWith("/login") || msg.startsWith("/l ") || msg.startsWith("/reg")) {
                inLobby = false;
                timer = 0;
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            inLobby = false;
            timer = 0;
            messageQueue.clear();
            return;
        }

        if (inLobby) {
            timer++;
            if (timer >= delay.get()) {
                executeLogin();
                inLobby = false;
                timer = 0;
            }
        }

        if (!messageQueue.isEmpty()) {
            ChatUtils.sendPlayerMsg(messageQueue.removeFirst());
        }
    }

    private void executeLogin() {
        String currentIp = Utils.getWorldName();
        String currentNick = mc.player.getGameProfile().name();

        for (String entry : accounts.get()) {
            String[] data = entry.split("\\|", 3);
            if (data.length < 3) continue;

            if (data[0].equalsIgnoreCase(currentIp) && data[1].equalsIgnoreCase(currentNick)) {
                messageQueue.add(data[2]);
                break;
            }
        }
    }

    @Override
    public void onActivate() {
        timer = 0;
        inLobby = false;
        messageQueue.clear();
    }
}
