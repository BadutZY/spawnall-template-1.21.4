package com.example.spawnall;

import com.example.spawnall.config.ModConfig;
import com.example.spawnall.network.NetworkHandler;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side initializer
 * UNIVERSAL: Works in singleplayer and multiplayer
 */
public class SpawnerClient implements ClientModInitializer, ModMenuApi {

    private static KeyBinding toggleKey;
    private static boolean isMultiplayer = false;

    @Override
    public void onInitializeClient() {
        UniversalSpawnerMod.LOGGER.info("========================================");
        UniversalSpawnerMod.LOGGER.info("Spawn All Client - UNIVERSAL VERSION");
        UniversalSpawnerMod.LOGGER.info("  Compatible: Singleplayer + Multiplayer");
        UniversalSpawnerMod.LOGGER.info("========================================");

        // Load config (client-side)
        ModConfig.load();

        // Register keybind (K key)
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spawn-all.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.spawn-all"
        ));

        // =====================================================
        // EVENT 1: Player Join - Detect Environment & Sync
        // =====================================================
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Detect if multiplayer or singleplayer
            isMultiplayer = !client.isInSingleplayer();

            String env = isMultiplayer ? "MULTIPLAYER" : "SINGLEPLAYER";
            UniversalSpawnerMod.LOGGER.info("========================================");
            UniversalSpawnerMod.LOGGER.info("🌍 Environment: {}", env);
            UniversalSpawnerMod.LOGGER.info("========================================");

            // NEW: Load last used mode dari config
            boolean lastMode = ModConfig.getLastUsedMode();
            UniversalSpawnerMod.LOGGER.info("📋 Restoring last used mode: {}",
                    lastMode ? "CUSTOM" : "VANILLA");

            // Hanya sync ke server jika multiplayer
            if (isMultiplayer) {
                // Tunggu sebentar untuk memastikan connection stable
                client.execute(() -> {
                    try {
                        Thread.sleep(1000); // 1 second delay untuk stabilitas
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // Send sync packet ke server dengan LAST USED MODE
                    if (ClientPlayNetworking.canSend(NetworkHandler.SyncOnJoinPayload.ID)) {
                        ClientPlayNetworking.send(
                                new NetworkHandler.SyncOnJoinPayload(
                                        ModConfig.isGlobalEnabled(),
                                        lastMode, // NEW: Send last used mode
                                        ModConfig.getMinSpawnDelay(),
                                        ModConfig.getMaxSpawnDelay(),
                                        ModConfig.getSpawnCount(),
                                        ModConfig.getMaxNearbyEntities(),
                                        ModConfig.getPlayerRange(),
                                        ModConfig.getSpawnRange()
                                )
                        );

                        UniversalSpawnerMod.LOGGER.info("========================================");
                        UniversalSpawnerMod.LOGGER.info("📤 SENT JOIN SYNC to multiplayer server:");
                        UniversalSpawnerMod.LOGGER.info("  Global Enabled: {}", ModConfig.isGlobalEnabled());
                        UniversalSpawnerMod.LOGGER.info("  Restoring Mode: {}", lastMode ? "CUSTOM" : "VANILLA");
                        UniversalSpawnerMod.LOGGER.info("  Min Delay: {} ticks", ModConfig.getMinSpawnDelay());
                        UniversalSpawnerMod.LOGGER.info("  Max Delay: {} ticks", ModConfig.getMaxSpawnDelay());
                        UniversalSpawnerMod.LOGGER.info("  Spawn Count: {}", ModConfig.getSpawnCount());
                        UniversalSpawnerMod.LOGGER.info("  Max Nearby: {}", ModConfig.getMaxNearbyEntities());
                        UniversalSpawnerMod.LOGGER.info("  Player Range: {}", ModConfig.getPlayerRange());
                        UniversalSpawnerMod.LOGGER.info("  Spawn Range: {}", ModConfig.getSpawnRange());
                        UniversalSpawnerMod.LOGGER.info("  This will restore your previous settings");
                        UniversalSpawnerMod.LOGGER.info("========================================");

                        // Send notification to player
                        if (client.player != null) {
                            String mode = lastMode ? "§aCUSTOM" : "§eVANILLA";
                            client.player.sendMessage(
                                    Text.literal("§6[Spawn All] §fRestored settings: " + mode),
                                    false
                            );

                            // Info about existing spawners
                            client.execute(() -> {
                                try {
                                    Thread.sleep(3000); // Wait for server to finish scanning
                                    int spawnerCount = UniversalSpawnerMod.getRegistrySize();
                                    if (spawnerCount > 0) {
                                        client.player.sendMessage(
                                                Text.literal("§6[Spawn All] §7Detected & configured " +
                                                        spawnerCount + " spawner(s)"),
                                                false
                                        );
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
                        }
                    }
                });
            } else {
                // Singleplayer: Langsung set state dari last used mode
                UniversalSpawnerMod.modEnabled = lastMode;

                UniversalSpawnerMod.LOGGER.info("========================================");
                UniversalSpawnerMod.LOGGER.info("🏠 SINGLEPLAYER mode initialized");
                UniversalSpawnerMod.LOGGER.info("  Config loaded from spawn-all.json");
                UniversalSpawnerMod.LOGGER.info("  Restored Mode: {}", lastMode ? "CUSTOM" : "VANILLA");
                if (!lastMode) {
                    UniversalSpawnerMod.LOGGER.info("  💡 Press K to enable CUSTOM mode");
                }
                UniversalSpawnerMod.LOGGER.info("  Existing spawners will be auto-detected");
                UniversalSpawnerMod.LOGGER.info("========================================");

                // Show info to player about existing spawners after scan completes
                if (client.player != null) {
                    client.execute(() -> {
                        try {
                            Thread.sleep(4000); // Wait for world to finish scanning
                            int spawnerCount = UniversalSpawnerMod.getRegistrySize();

                            String mode = lastMode ? "§aCUSTOM" : "§eVANILLA";

                            if (spawnerCount > 0) {
                                client.player.sendMessage(
                                        Text.literal("§6[Spawn All] §7Detected " + spawnerCount +
                                                " spawner(s) - Mode: " + mode),
                                        false
                                );
                            }

                            // Show tip for first time users (vanilla mode)
                            if (!lastMode) {
                                Thread.sleep(1000);
                                client.player.sendMessage(
                                        Text.literal("§6[Spawn All] §7Press §eK §7to enable fast spawning mode"),
                                        false
                                );
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }
            }
        });

        // =====================================================
        // EVENT 2: Client Tick - Keybind Handler (Universal)
        // =====================================================
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Cek keybind press
            if (ModConfig.isGlobalEnabled() &&
                    ModConfig.isKeybindEnabled() &&
                    toggleKey.wasPressed()) {

                // Toggle in-game state
                UniversalSpawnerMod.modEnabled = !UniversalSpawnerMod.modEnabled;

                // NEW: Save last used mode to config
                ModConfig.setLastUsedMode(UniversalSpawnerMod.modEnabled);

                // Send feedback message
                String mode = UniversalSpawnerMod.modEnabled ? "§aCUSTOM" : "§eVANILLA";
                String description = UniversalSpawnerMod.modEnabled ?
                        "§7(Fast, All-Time)" :
                        "§7(Default Behavior)";

                client.player.sendMessage(
                        Text.literal("§6[Spawn All] §fMode: " + mode + " " + description),
                        true
                );

                if (isMultiplayer) {
                    // MULTIPLAYER: Send packet to server
                    if (ClientPlayNetworking.canSend(NetworkHandler.ToggleModePayload.ID)) {
                        ClientPlayNetworking.send(
                                new NetworkHandler.ToggleModePayload(UniversalSpawnerMod.modEnabled)
                        );

                        client.player.sendMessage(
                                Text.literal("§6[Spawn All] §7Updating spawners on server..."),
                                false
                        );

                        UniversalSpawnerMod.LOGGER.info("📤 [MP] Sent toggle packet to server: Mode = {}",
                                UniversalSpawnerMod.modEnabled ? "CUSTOM" : "VANILLA");
                    } else {
                        client.player.sendMessage(
                                Text.literal("§c[Spawn All] Cannot communicate with server!"),
                                false
                        );
                    }
                } else {
                    // SINGLEPLAYER: Request local update
                    UniversalSpawnerMod.requestSpawnerUpdate();

                    client.player.sendMessage(
                            Text.literal("§6[Spawn All] §7Updating spawners..."),
                            false
                    );

                    UniversalSpawnerMod.LOGGER.info("🏠 [SP] Toggle: Mode = {}",
                            UniversalSpawnerMod.modEnabled ? "CUSTOM" : "VANILLA");
                }
            }
        });

        UniversalSpawnerMod.LOGGER.info("✓ Client initialized");
        UniversalSpawnerMod.LOGGER.info("  Mode: Universal (SP + MP)");
        UniversalSpawnerMod.LOGGER.info("  Keybind: K (toggle)");
    }

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SpawnerConfigScreen::create;
    }

    public static boolean isMultiplayer() {
        return isMultiplayer;
    }
}