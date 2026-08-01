package com.example.reallyworldautoreconnect;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ReallyWorldAutoReconnect implements ClientModInitializer {
    // ===== НАСТРОЙКИ =====
    public static boolean enabled = true;
    public static String command = "spawn";
    public static int intervalMinutes = 5;
    public static boolean autoReconnect = true;

    private static int tickCounter = 0;
    public static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        // Клавиша R для открытия меню
        openGuiKey = new KeyBinding(
                "key.reallyworldautoreconnect.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.reallyworldautoreconnect"
        );
        KeyBindingHelper.registerKeyBinding(openGuiKey);

        // Загрузка конфига
        Config.load();

        // Обработчик тиков
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Открыть GUI по нажатию R
            while (openGuiKey.wasPressed()) {
                client.openScreen(new ConfigScreen());
            }

            // Если игрок не в мире или не на сервере — пропускаем
            if (client.player == null || client.world == null) return;
            if (client.getCurrentServerEntry() == null) {
                tickCounter = 0;
                return;
            }

            // Основная логика
            if (enabled) {
                tickCounter++;
                int ticksPerInterval = 20 * 60 * intervalMinutes; // 20 тиков = 1 сек
                if (tickCounter >= ticksPerInterval) {
                    tickCounter = 0;
                    executeAction(client);
                }
            } else {
                tickCounter = 0;
            }
        });
    }

    private void executeAction(MinecraftClient client) {
        // 1. Отправить команду
        if (command != null && !command.isEmpty()) {
            client.player.sendChatMessage("/" + command);
        }

        // 2. Авто-реконнект
        if (autoReconnect && client.getCurrentServerEntry() != null) {
            var entry = client.getCurrentServerEntry();
            ServerAddress address = ServerAddress.parse(entry.address);
            client.openScreen(new ConnectScreen(
                    null,
                    client,
                    address,
                    entry,
                    false
            ));
        }
    }

    // ===== ВНУТРЕННИЙ КЛАСС ДЛЯ КОНФИГА =====
    public static class Config {
        private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
                .resolve("reallyworldautoreconnect.properties");

        public static void load() {
            if (!Files.exists(CONFIG_PATH)) {
                save();
                return;
            }
            try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
                Properties props = new Properties();
                props.load(input);
                enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
                command = props.getProperty("command", "spawn");
                intervalMinutes = Integer.parseInt(props.getProperty("intervalMinutes", "5"));
                autoReconnect = Boolean.parseBoolean(props.getProperty("autoReconnect", "true"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public static void save() {
            Properties props = new Properties();
            props.setProperty("enabled", String.valueOf(enabled));
            props.setProperty("command", command);
            props.setProperty("intervalMinutes", String.valueOf(intervalMinutes));
            props.setProperty("autoReconnect", String.valueOf(autoReconnect));
            try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
                props.store(output, "ReallyWorld AutoReconnect Config");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ===== ВНУТРЕННИЙ КЛАСС — ЭКРАН НАСТРОЕК =====
    public static class ConfigScreen extends Screen {
        private TextFieldWidget commandField;
        private TextFieldWidget intervalField;
        private ButtonWidget toggleEnabledButton;
        private ButtonWidget toggleReconnectButton;

        protected ConfigScreen() {
            super(new LiteralText("ReallyWorld AutoReconnect"));
        }

        @Override
        protected void init() {
            super.init();
            int cx = this.width / 2;

            commandField = new TextFieldWidget(this.textRenderer, cx - 100, 60, 200, 20,
                    new LiteralText("Command"));
            commandField.setText(command);
            commandField.setMaxLength(100);
            this.addSelectableChild(commandField);

            intervalField = new TextFieldWidget(this.textRenderer, cx - 100, 100, 200, 20,
                    new LiteralText("Interval (min)"));
            intervalField.setText(String.valueOf(intervalMinutes));
            intervalField.setMaxLength(6);
            this.addSelectableChild(intervalField);

            toggleEnabledButton = new ButtonWidget(cx - 100, 140, 200, 20,
                    new LiteralText(getEnabledText()), btn -> {
                enabled = !enabled;
                btn.setMessage(new LiteralText(getEnabledText()));
            });
            this.addDrawableChild(toggleEnabledButton);

            toggleReconnectButton = new ButtonWidget(cx - 100, 170, 200, 20,
                    new LiteralText(getReconnectText()), btn -> {
                autoReconnect = !autoReconnect;
                btn.setMessage(new LiteralText(getReconnectText()));
            });
            this.addDrawableChild(toggleReconnectButton);

            this.addDrawableChild(new ButtonWidget(cx - 100, 210, 200, 20,
                    new LiteralText("Done"), btn -> {
                command = commandField.getText();
                try {
                    int val = Integer.parseInt(intervalField.getText());
                    if (val > 0) intervalMinutes = val;
                } catch (NumberFormatException ignored) {}
                Config.save();
                this.client.openScreen(null);
            }));
        }

        private String getEnabledText() {
            return "Enabled: " + (enabled ? "ON" : "OFF");
        }

        private String getReconnectText() {
            return "AutoReconnect: " + (autoReconnect ? "ON" : "OFF");
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            this.renderBackground(matrices);
            drawCenteredText(matrices, this.textRenderer, this.title.asString(),
                    this.width / 2, 20, 0xFFFFFF);
            commandField.render(matrices, mouseX, mouseY, delta);
            intervalField.render(matrices, mouseX, mouseY, delta);
            super.render(matrices, mouseX, mouseY, delta);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (keyCode == 256) { // ESC — закрыть без сохранения
                this.client.openScreen(null);
                return true;
            }
            return commandField.keyPressed(keyCode, scanCode, modifiers) ||
                    intervalField.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void tick() {
            commandField.tick();
            intervalField.tick();
        }
    }
}
