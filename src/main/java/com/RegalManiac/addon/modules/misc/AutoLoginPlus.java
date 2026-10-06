package com.RegalManiac.addon.modules.misc;

import com.RegalManiac.addon.managers.LobbyManager;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.ClientConnection;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class AutoLoginPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ticks")
        .defaultValue(10)
        .min(0)
        .build()
    );

    private final Setting<List<String>> accounts = sgGeneral.add(new StringListSetting.Builder()
        .name("accounts")
        .defaultValue(new ArrayList<>())
        .visible(() -> false)
        .build()
    );

    private final List<String> messageQueue = new LinkedList<>();
    private int timer = 0;

    public AutoLoginPlus() {
        super(Categories.Misc, "auto-login-+", "Login module based on server + nickname + password.");
        this.runInMainMenu = true;
        LobbyManager.init();
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
            String host = resolveCurrentHost(null);
            String nick = resolveCurrentNick();
            list.add(host + "|" + nick + "|/l ");
            accounts.set(list);
            fillTable(theme, table);
        };
        table.row();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!LobbyManager.isInLobby() && (mc.player == null || mc.world == null)) {
            timer = 0;
            messageQueue.clear();
            return;
        }

        if (LobbyManager.isInLobby()) {
            timer++;
            if (timer >= delay.get()) {
                executeLogin();
                timer = 0;
            }
        } else {
            timer = 0;
        }

        if (!messageQueue.isEmpty() && mc.player != null && mc.world != null) {
            ChatUtils.sendPlayerMsg(messageQueue.removeFirst());
        }
    }

    private void executeLogin() {
        String passOrCmd = findMatchingPasswordOrCommand();
        if (passOrCmd == null) return;

        if (LobbyManager.isInAuthDialog()) {
            String cleanPassword = extractPassword(passOrCmd);
            LobbyManager.sendAuthResponse(cleanPassword);
        } else {
            messageQueue.add(formatChatCommand(passOrCmd));
            LobbyManager.reset();
        }
    }

    private String findMatchingPasswordOrCommand() {
        String currentHost = resolveCurrentHost(LobbyManager.getLastConnection());
        String currentNick = resolveCurrentNick();

        for (String entry : accounts.get()) {
            String[] data = entry.split("\\|", 3);
            if (data.length < 3) continue;

            String entryServer = cleanHost(data[0]);
            String entryNick = data[1].trim();
            String entryPass = data[2].trim();

            if (entryPass.isEmpty()) continue;

            if (entryServer.isEmpty() || entryServer.equalsIgnoreCase(currentHost)) {
                if (entryNick.isEmpty() || entryNick.equalsIgnoreCase(currentNick)) {
                    return entryPass;
                }
            }
        }
        return null;
    }

    private String resolveCurrentHost(ClientConnection connection) {
        if (connection != null && connection.getAddress() instanceof InetSocketAddress inet) {
            String host = cleanHost(inet.getHostString());
            if (!host.isEmpty()) return host;
        }
        if (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null) {
            String host = cleanHost(mc.getCurrentServerEntry().address);
            if (!host.isEmpty()) return host;
        }
        return cleanHost(Utils.getWorldName());
    }

    private String resolveCurrentNick() {
        if (mc.player != null && mc.player.getGameProfile() != null && mc.player.getGameProfile().name() != null) {
            String nick = mc.player.getGameProfile().name().trim();
            if (!nick.isEmpty()) return nick;
        }
        if (mc.getSession() != null && mc.getSession().getUsername() != null) {
            return mc.getSession().getUsername().trim();
        }
        return "";
    }

    private String cleanHost(String host) {
        if (host == null) return "";
        host = host.trim().toLowerCase();
        if (host.startsWith("http://")) host = host.substring(7);
        if (host.startsWith("https://")) host = host.substring(8);
        int slash = host.indexOf('/');
        if (slash != -1) host = host.substring(0, slash);
        int colon = host.lastIndexOf(':');
        if (colon != -1) host = host.substring(0, colon);
        return host.trim();
    }

    private String extractPassword(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase();

        for (String prefix : new String[]{"/login ", "/l ", "/reg ", "/register ", "/auth "}) {
            if (lower.startsWith(prefix)) {
                String remainder = trimmed.substring(prefix.length()).trim();
                int spaceIdx = remainder.indexOf(' ');
                return spaceIdx > 0 ? remainder.substring(0, spaceIdx) : remainder;
            }
        }
        return trimmed;
    }

    private String formatChatCommand(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        if (trimmed.startsWith("/")) return trimmed;
        return "/l " + trimmed;
    }

    @Override
    public void onActivate() {
        LobbyManager.init();
        timer = 0;
        messageQueue.clear();
    }
}
