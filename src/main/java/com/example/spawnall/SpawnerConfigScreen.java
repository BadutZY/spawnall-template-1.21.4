package com.example.spawnall;

import com.example.spawnall.config.ModConfig;
import com.example.spawnall.network.NetworkHandler;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Config screen - UNIVERSAL
 * Works in both singleplayer and multiplayer
 */
public class SpawnerConfigScreen {

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .transparentBackground();

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Detect environment
        boolean isMultiplayer = SpawnerClient.isMultiplayer();
        String envInfo = isMultiplayer ?
                "Â§7âš¡ Settings auto-sync to server on join" :
                "Â§7ðŸ  Settings saved locally for singleplayer";

        // =====================================================
        // CATEGORY 1: General Settings
        // =====================================================
        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General Settings"));

        general.addEntry(
                entryBuilder.startTextDescription(
                        Text.literal(envInfo)
                ).build()
        );

        general.addEntry(
                entryBuilder.startTextDescription(
                        Text.literal("     ")
                ).build()
        );

        // Toggle 1: Global Enable WITH AUTO-UPDATE
        general.addEntry(
                entryBuilder.startBooleanToggle(
                                Text.literal("Enable Spawn All Mod"),
                                ModConfig.isGlobalEnabled()
                        )
                        .setDefaultValue(true)
                        .setSaveConsumer(newValue -> {
                            ModConfig.setGlobalEnabled(newValue);

                            if (!newValue) {
                                ModConfig.setKeybindEnabled(false);
                            }

                            UniversalSpawnerMod.modEnabled = newValue;

                            if (isMultiplayer) {
                                // MULTIPLAYER: Send packet to server
                                if (ClientPlayNetworking.canSend(NetworkHandler.ToggleModePayload.ID)) {
                                    ClientPlayNetworking.send(
                                            new NetworkHandler.ToggleModePayload(newValue)
                                    );

                                    UniversalSpawnerMod.LOGGER.info("========================================");
                                    UniversalSpawnerMod.LOGGER.info("ðŸ“¤ [MP] MODE TOGGLED (sent to server): {}",
                                            newValue ? "CUSTOM" : "VANILLA");
                                    UniversalSpawnerMod.LOGGER.info("========================================");
                                }
                            } else {
                                // SINGLEPLAYER: Local update
                                UniversalSpawnerMod.requestSpawnerUpdate();

                                UniversalSpawnerMod.LOGGER.info("========================================");
                                UniversalSpawnerMod.LOGGER.info("ðŸ  [SP] MODE TOGGLED: {}",
                                        newValue ? "CUSTOM" : "VANILLA");
                                UniversalSpawnerMod.LOGGER.info("   Requested update for {} spawner(s)",
                                        UniversalSpawnerMod.getRegistrySize());
                                UniversalSpawnerMod.LOGGER.info("========================================");
                            }
                        })
                        .setTooltip(
                                Text.literal("Â§eToggle between Vanilla and Spawn All behavior"),
                                Text.literal(isMultiplayer ?
                                        "Â§7Changes will sync to server immediately" :
                                        "Â§7Changes saved to config file")
                        )
                        .build()
        );

        // Toggle 2: Keybind Enable
        general.addEntry(
                entryBuilder.startBooleanToggle(
                                Text.literal("Enable In-Game Toggle"),
                                ModConfig.isKeybindEnabled()
                        )
                        .setDefaultValue(true)
                        .setRequirement(() -> ModConfig.isGlobalEnabled())
                        .setSaveConsumer(newValue -> {
                            ModConfig.setKeybindEnabled(newValue);
                            UniversalSpawnerMod.LOGGER.info("Keybind toggle: {}",
                                    newValue ? "ENABLED" : "DISABLED");
                        })
                        .setTooltip(
                                Text.literal("Enable/disable in-game toggle with keyboard/mouse.")
                        )
                        .build()
        );

        general.addEntry(
                entryBuilder.startTextDescription(
                        Text.literal("     ")
                ).build()
        );

        general.addEntry(
                entryBuilder.startTextDescription(
                        Text.literal("Â§7Environment: Â§b" + (isMultiplayer ? "Multiplayer Server" : "Singleplayer"))
                ).build()
        );

        general.addEntry(
                entryBuilder.startTextDescription(
                        Text.literal("Â§7Current Mode: " +
                                (ModConfig.isGlobalEnabled() && UniversalSpawnerMod.modEnabled ?
                                        "Â§aCUSTOM" : "Â§eVANILLA"))
                ).build()
        );

        general.addEntry(
                entryBuilder.startTextDescription(
                        Text.literal("Â§7Registered Spawners: Â§b" +
                                UniversalSpawnerMod.getRegistrySize())
                ).build()
        );

        general.addEntry(
                entryBuilder.startTextDescription(
                        Text.literal("Â§7Custom Settings: " +
                                (ModConfig.isGlobalEnabled() && UniversalSpawnerMod.modEnabled ?
                                        "Â§aENABLED" : "Â§cDISABLED"))
                ).build()
        );

        // =====================================================
        // CATEGORY 2: Spawn Parameters
        // =====================================================
        ConfigCategory spawning = builder.getOrCreateCategory(Text.literal("Spawn Settings"));

        spawning.addEntry(
                entryBuilder.startTextDescription(
                        Text.literal(isMultiplayer ?
                                "Â§6âš¡ Changes will update server spawners automatically" :
                                "Â§6ðŸ  Changes saved locally for this world")
                ).build()
        );

        spawning.addEntry(
                entryBuilder.startTextDescription(
                        Text.literal("     ")
                ).build()
        );

        // Slider 1: Min Spawn Delay
        spawning.addEntry(
                entryBuilder.startIntSlider(
                                Text.literal("Min Spawn Delay (seconds)"),
                                ticksToSeconds(ModConfig.getMinSpawnDelay()),
                                1, 10
                        )
                        .setDefaultValue(2)
                        .setRequirement(() -> ModConfig.isGlobalEnabled() && UniversalSpawnerMod.modEnabled)
                        .setSaveConsumer(seconds -> {
                            int ticks = secondsToTicks(seconds);
                            ModConfig.setMinSpawnDelay(ticks);

                            if (isMultiplayer) {
                                sendConfigUpdatePacket();
                            } else {
                                UniversalSpawnerMod.requestSpawnerUpdate();
                            }
                        })
                        .setTooltip(
                                Text.literal("Minimum delay between spawns"),
                                Text.literal("Â§7Default: 2 seconds"),
                                Text.literal("Â§7Range: 1 - 10 seconds"),
                                Text.literal(" "),
                                Text.literal("Â§cÂ§lONLY active in Spawn All Mode")
                        )
                        .setTextGetter(value -> Text.literal(value + "s"))
                        .build()
        );

        // Slider 2: Max Spawn Delay
        spawning.addEntry(
                entryBuilder.startIntSlider(
                                Text.literal("Max Spawn Delay (seconds)"),
                                ticksToSeconds(ModConfig.getMaxSpawnDelay()),
                                1, 20
                        )
                        .setDefaultValue(5)
                        .setRequirement(() -> ModConfig.isGlobalEnabled() && UniversalSpawnerMod.modEnabled)
                        .setSaveConsumer(seconds -> {
                            int ticks = secondsToTicks(seconds);
                            ModConfig.setMaxSpawnDelay(ticks);

                            if (isMultiplayer) {
                                sendConfigUpdatePacket();
                            } else {
                                UniversalSpawnerMod.requestSpawnerUpdate();
                            }
                        })
                        .setTooltip(
                                Text.literal("Maximum delay between spawns"),
                                Text.literal("Â§7Default: 5 seconds"),
                                Text.literal("Â§7Range: 1 - 20 seconds"),
                                Text.literal(" "),
                                Text.literal("Â§cÂ§lONLY active in Spawn All Mode")
                        )
                        .setTextGetter(value -> Text.literal(value + "s"))
                        .build()
        );

        // Slider 3: Spawn Count
        spawning.addEntry(
                entryBuilder.startIntSlider(
                                Text.literal("Spawn Count (mobs)"),
                                ModConfig.getSpawnCount(),
                                1, 10
                        )
                        .setDefaultValue(6)
                        .setRequirement(() -> ModConfig.isGlobalEnabled() && UniversalSpawnerMod.modEnabled)
                        .setSaveConsumer(value -> {
                            ModConfig.setSpawnCount(value);

                            if (isMultiplayer) {
                                sendConfigUpdatePacket();
                            } else {
                                UniversalSpawnerMod.requestSpawnerUpdate();
                            }
                        })
                        .setTooltip(
                                Text.literal("Number of mobs spawned per cycle"),
                                Text.literal("Â§7Default: 6 mobs"),
                                Text.literal("Â§7Range: 1 - 10 mobs"),
                                Text.literal(" "),
                                Text.literal("Â§cÂ§lONLY active in Spawn All Mode")
                        )
                        .build()
        );

        // Slider 4: Max Nearby Entities
        spawning.addEntry(
                entryBuilder.startIntSlider(
                                Text.literal("Max Nearby Entities"),
                                ModConfig.getMaxNearbyEntities(),
                                4, 20
                        )
                        .setDefaultValue(12)
                        .setRequirement(() -> ModConfig.isGlobalEnabled() && UniversalSpawnerMod.modEnabled)
                        .setSaveConsumer(value -> {
                            ModConfig.setMaxNearbyEntities(value);

                            if (isMultiplayer) {
                                sendConfigUpdatePacket();
                            } else {
                                UniversalSpawnerMod.requestSpawnerUpdate();
                            }
                        })
                        .setTooltip(
                                Text.literal("Maximum mobs allowed near spawner"),
                                Text.literal("Â§7Default: 12 mobs"),
                                Text.literal("Â§7Range: 4 - 20 mobs"),
                                Text.literal(" "),
                                Text.literal("Â§cÂ§lONLY active in Spawn All Mode")
                        )
                        .build()
        );

        // Slider 5: Player Range
        spawning.addEntry(
                entryBuilder.startIntSlider(
                                Text.literal("Player Range (blocks)"),
                                ModConfig.getPlayerRange(),
                                16, 128
                        )
                        .setDefaultValue(32)
                        .setRequirement(() -> ModConfig.isGlobalEnabled() && UniversalSpawnerMod.modEnabled)
                        .setSaveConsumer(value -> {
                            ModConfig.setPlayerRange(value);

                            if (isMultiplayer) {
                                sendConfigUpdatePacket();
                            } else {
                                UniversalSpawnerMod.requestSpawnerUpdate();
                            }
                        })
                        .setTooltip(
                                Text.literal("Distance player must be for spawner to work"),
                                Text.literal("Â§7Default: 32 blocks"),
                                Text.literal("Â§7Range: 16 - 128 blocks"),
                                Text.literal(" "),
                                Text.literal("Â§cÂ§lONLY active in Spawn All Mode")
                        )
                        .build()
        );

        // Slider 6: Spawn Range
        spawning.addEntry(
                entryBuilder.startIntSlider(
                                Text.literal("Spawn Range (blocks)"),
                                ModConfig.getSpawnRange(),
                                2, 10
                        )
                        .setDefaultValue(5)
                        .setRequirement(() -> ModConfig.isGlobalEnabled() && UniversalSpawnerMod.modEnabled)
                        .setSaveConsumer(value -> {
                            ModConfig.setSpawnRange(value);

                            if (isMultiplayer) {
                                sendConfigUpdatePacket();
                            } else {
                                UniversalSpawnerMod.requestSpawnerUpdate();
                            }
                        })
                        .setTooltip(
                                Text.literal("Area around spawner where mobs can spawn"),
                                Text.literal("Â§7Default: 5 blocks"),
                                Text.literal("Â§7Range: 2 - 10 blocks"),
                                Text.literal(" "),
                                Text.literal("Â§cÂ§lONLY active in Spawn All Mode")
                        )
                        .build()
        );

        builder.setSavingRunnable(ModConfig::save);
        return builder.build();
    }

    // =====================================================
    // HELPER: Send config update packet (multiplayer only)
    // =====================================================
    private static void sendConfigUpdatePacket() {
        if (ClientPlayNetworking.canSend(NetworkHandler.UpdateConfigPayload.ID)) {
            ClientPlayNetworking.send(
                    new NetworkHandler.UpdateConfigPayload(
                            ModConfig.getMinSpawnDelay(),
                            ModConfig.getMaxSpawnDelay(),
                            ModConfig.getSpawnCount(),
                            ModConfig.getMaxNearbyEntities(),
                            ModConfig.getPlayerRange(),
                            ModConfig.getSpawnRange()
                    )
            );

            UniversalSpawnerMod.LOGGER.info("ðŸ“¤ [MP] Sent config update packet to server");
        }
    }

    // Helper: Convert ticks to seconds
    private static int ticksToSeconds(int ticks) {
        return Math.max(1, Math.round(ticks / 20.0f));
    }

    // Helper: Convert seconds to ticks
    private static int secondsToTicks(int seconds) {
        return seconds * 20;
    }
}