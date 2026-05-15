package com.RegalManiac.addon.modules.movement;

import com.RegalManiac.addon.utils.InventoryUtils;
import com.RegalManiac.addon.utils.SearchInvResultUtils;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import static com.RegalManiac.addon.utils.InventoryUtils.sendPacket;
import static com.RegalManiac.addon.utils.InventoryUtils.sendSequencedPacket;

public class ElytraUtils extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> forceElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("force-elytra")
        .description("Equips elytra immediately when the module is enabled.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoGlide = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-gliding")
        .description("Automatically start gliding and boost if you are falling.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> boostDelay = sgGeneral.add(new IntSetting.Builder()
        .name("firework-delay")
        .description("Delay between firework uses in ticks.")
        .defaultValue(20)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> elytraReplace = sgGeneral.add(new BoolSetting.Builder()
        .name("elytra-replace")
        .description("Automatically replaces elytra before it breaks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> replaceDurability = sgGeneral.add(new IntSetting.Builder()
        .name("replace-durability")
        .description("The durability at which the elytra will be swapped.")
        .defaultValue(10)
        .min(1)
        .sliderMax(50)
        .visible(elytraReplace::get)
        .build()
    );

    private final Setting<Boolean> logAtYToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("log-at-y")
        .description("Disconnect from the server when reaching a specific height.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> yLevel = sgGeneral.add(new IntSetting.Builder()
        .name("y-level")
        .description("The Y-level at or below which to disconnect.")
        .defaultValue(120)
        .sliderRange(-64, 320)
        .visible(logAtYToggle::get)
        .build()
    );

    private final Setting<Boolean> minSpeedToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("min-speed-fire")
        .description("Only use fireworks when speed drops below the specified value.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> minSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-speed")
        .description("The minimum speed to maintain.")
        .defaultValue(1.5)
        .min(0.1)
        .sliderMax(3.0)
        .visible(minSpeedToggle::get)
        .build()
    );

    private final Setting<Boolean> toggleOnLeave = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-leave")
        .description("Whether to disable the module after automatically disconnecting.")
        .defaultValue(true)
        .visible(logAtYToggle::get)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Enable rotation locking.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> rotationMinY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to start rotating.")
        .defaultValue(30)
        .sliderRange(-64, 320)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Boolean> yawToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("yaw-lock")
        .description("Lock your yaw rotation.")
        .defaultValue(false)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Double> yawAngle = sgGeneral.add(new DoubleSetting.Builder()
        .name("yaw-angle")
        .description("Yaw angle in degrees.")
        .defaultValue(0)
        .sliderMax(360)
        .visible(() -> rotate.get() && yawToggle.get())
        .build()
    );

    private final Setting<Boolean> pitchToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("pitch-lock")
        .description("Lock your pitch rotation.")
        .defaultValue(false)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Double> pitchAngle = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch-angle")
        .description("Pitch angle in degrees.")
        .defaultValue(0)
        .range(-90, 90)
        .sliderRange(-90, 90)
        .visible(() -> rotate.get() && pitchToggle.get())
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

    private int fireworkTimer = 0;
    private boolean inLobby = false;
    private int lobbyGraceTimer = 0;

    public ElytraUtils() {
        super(Categories.Movement, "elytra-utils", "Various utilities for elytra flight.");
    }

    @Override
    public void onActivate() {
        fireworkTimer = 0;
        inLobby = false;
        lobbyGraceTimer = 0;

        if (forceElytra.get() && mc.player != null) {
            ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestStack.getItem() != Items.ELYTRA) {
                FindItemResult elytra = InvUtils.find(itemStack -> itemStack.getItem() == Items.ELYTRA);
                if (elytra.found()) {
                    InvUtils.move().from(elytra.slot()).toArmor(2);
                }
            }
        }
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
        if (event.packet instanceof CommandExecutionC2SPacket(String command)) {
            String cmd = command.toLowerCase();
            if (cmd.startsWith("login") || cmd.startsWith("l ") || cmd.startsWith("reg")) {
                inLobby = false;
                lobbyGraceTimer = 100;
            }
        } else if (event.packet instanceof ChatMessageC2SPacket packet) {
            String msg = packet.chatMessage().toLowerCase();
            if (msg.startsWith("/login") || msg.startsWith("/l ") || msg.startsWith("/reg")) {
                inLobby = false;
                lobbyGraceTimer = 100;
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (fireworkTimer > 0) fireworkTimer--;
        if (lobbyGraceTimer > 0) lobbyGraceTimer--;

        if (forceElytra.get()) {
            ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestStack.getItem() != Items.ELYTRA) {
                FindItemResult elytra = InvUtils.find(itemStack -> itemStack.getItem() == Items.ELYTRA);
                if (elytra.found()) {
                    InvUtils.move().from(elytra.slot()).toArmor(2);
                }
            }
        }

        if (rotate.get() && mc.player.getY() >= rotationMinY.get()) {
            if (yawToggle.get()) {
                float yaw = yawAngle.get().floatValue();
                mc.player.setYaw(yaw);
                mc.player.headYaw = yaw;
                mc.player.bodyYaw = yaw;
            }
            if (pitchToggle.get()) {
                mc.player.setPitch(pitchAngle.get().floatValue());
            }
        }

        if (logAtYToggle.get() && !inLobby && lobbyGraceTimer == 0 && mc.player.age > 60 && mc.player.getY() <= yLevel.get()) {
            mc.player.networkHandler.getConnection().disconnect(Text.literal("Logged out at Y: " + (int)mc.player.getY()));
            if (toggleOnLeave.get()) toggle();
            return;
        }

        if (elytraReplace.get()) {
            ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestStack.getItem() == Items.ELYTRA) {
                int currentDurability = chestStack.getMaxDamage() - chestStack.getDamage();
                if (currentDurability <= replaceDurability.get()) {
                    FindItemResult newElytra = InvUtils.find(itemStack ->
                        itemStack.getItem() == Items.ELYTRA &&
                            (itemStack.getMaxDamage() - itemStack.getDamage()) > replaceDurability.get()
                    );
                    if (newElytra.found()) InvUtils.move().from(newElytra.slot()).toArmor(2);
                }
            }
        }

        if (autoGlide.get() && !mc.player.isOnGround() && mc.player.fallDistance > 0.2f && !mc.player.isGliding() && !mc.player.isTouchingWater() && !mc.player.isInLava()) {
            ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);

            if (chestStack.getItem() == Items.ELYTRA && InventoryUtils.findItemInInventory(Items.FIREWORK_ROCKET).found()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                useFirework();
            }
        }

        if (mc.player.isGliding()) {
            double currentSpeed = mc.player.getVelocity().length();
            boolean isFastEnough = minSpeedToggle.get() && currentSpeed >= minSpeed.get();
            if (!isFastEnough) useFirework();
        }
    }

    private void useFirework() {
        if (fireworkTimer > 0) return;

        SearchInvResultUtils anyFirework = InventoryUtils.findItemInInventory(Items.FIREWORK_ROCKET);
        if (!anyFirework.found()) {
            ChatUtils.info("No fireworks available! Disabling.");
            toggle();
            return;
        }

        SearchInvResultUtils hotbarFireWorkResult = InventoryUtils.findItemInHotBar(Items.FIREWORK_ROCKET);
        SearchInvResultUtils inventoryFireWorkResult = InventoryUtils.findItemInInventory(Items.FIREWORK_ROCKET);

        InventoryUtils.saveSlot();

        boolean usedFromInventory = false;

        if (hotbarFireWorkResult.found()) {
            hotbarFireWorkResult.switchTo();
        } else {
            usedFromInventory = true;
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                inventoryFireWorkResult.slot() < 9 ? inventoryFireWorkResult.slot() + 36 : inventoryFireWorkResult.slot(),
                mc.player.getInventory().getSelectedSlot(),
                SlotActionType.SWAP,
                mc.player
            );
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        }

        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
        sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        InventoryUtils.returnSlot();

        if (usedFromInventory) {
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                inventoryFireWorkResult.slot() < 9 ? inventoryFireWorkResult.slot() + 36 : inventoryFireWorkResult.slot(),
                mc.player.getInventory().getSelectedSlot(),
                SlotActionType.SWAP,
                mc.player
            );
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        }

        fireworkTimer = boostDelay.get();
    }
}
