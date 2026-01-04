package com.example.spawnall.network;

import com.example.spawnall.UniversalSpawnerMod;
import com.example.spawnall.config.ModConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Network handler untuk client-server communication
 * Compatible dengan Minecraft 1.21.4
 */
public class NetworkHandler {

    // Packet IDs
    public static final Identifier TOGGLE_MODE_ID = Identifier.of(UniversalSpawnerMod.MOD_ID, "toggle_mode");
    public static final Identifier UPDATE_CONFIG_ID = Identifier.of(UniversalSpawnerMod.MOD_ID, "update_config");
    public static final Identifier SYNC_ON_JOIN_ID = Identifier.of(UniversalSpawnerMod.MOD_ID, "sync_on_join");

    /**
     * Register all network packets
     */
    public static void registerPackets() {
        // Register packet types
        PayloadTypeRegistry.playC2S().register(ToggleModePayload.ID, ToggleModePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateConfigPayload.ID, UpdateConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SyncOnJoinPayload.ID, SyncOnJoinPayload.CODEC);

        // =====================================================
        // RECEIVER 1: Toggle Mode
        // =====================================================
        ServerPlayNetworking.registerGlobalReceiver(ToggleModePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                // Toggle mod enabled state
                UniversalSpawnerMod.modEnabled = payload.enabled();

                // NEW: Save last used mode
                ModConfig.setLastUsedMode(payload.enabled());

                // Request spawner update
                UniversalSpawnerMod.requestSpawnerUpdate();

                UniversalSpawnerMod.LOGGER.info("========================================");
                UniversalSpawnerMod.LOGGER.info("🔄 SERVER: Mode toggled by {}",
                        context.player().getName().getString());
                UniversalSpawnerMod.LOGGER.info("  New mode: {}", payload.enabled() ? "CUSTOM" : "VANILLA");
                UniversalSpawnerMod.LOGGER.info("  Spawners to update: {}", UniversalSpawnerMod.getRegistrySize());
                UniversalSpawnerMod.LOGGER.info("  Mode saved to config");
                UniversalSpawnerMod.LOGGER.info("========================================");
            });
        });

        // =====================================================
        // RECEIVER 2: Update Config
        // =====================================================
        ServerPlayNetworking.registerGlobalReceiver(UpdateConfigPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                // Update config values
                ModConfig.setMinSpawnDelay(payload.minSpawnDelay());
                ModConfig.setMaxSpawnDelay(payload.maxSpawnDelay());
                ModConfig.setSpawnCount(payload.spawnCount());
                ModConfig.setMaxNearbyEntities(payload.maxNearbyEntities());
                ModConfig.setPlayerRange(payload.playerRange());
                ModConfig.setSpawnRange(payload.spawnRange());

                // Request spawner update if mod is enabled
                if (UniversalSpawnerMod.isModEnabled()) {
                    UniversalSpawnerMod.requestSpawnerUpdate();

                    UniversalSpawnerMod.LOGGER.info("========================================");
                    UniversalSpawnerMod.LOGGER.info("🔧 SERVER: Config updated by {}",
                            context.player().getName().getString());
                    UniversalSpawnerMod.LOGGER.info("  Min Delay: {} ticks", payload.minSpawnDelay());
                    UniversalSpawnerMod.LOGGER.info("  Max Delay: {} ticks", payload.maxSpawnDelay());
                    UniversalSpawnerMod.LOGGER.info("  Spawn Count: {}", payload.spawnCount());
                    UniversalSpawnerMod.LOGGER.info("  Max Nearby: {}", payload.maxNearbyEntities());
                    UniversalSpawnerMod.LOGGER.info("  Player Range: {}", payload.playerRange());
                    UniversalSpawnerMod.LOGGER.info("  Spawn Range: {}", payload.spawnRange());
                    UniversalSpawnerMod.LOGGER.info("  Updating {} spawner(s)...", UniversalSpawnerMod.getRegistrySize());
                    UniversalSpawnerMod.LOGGER.info("========================================");
                }
            });
        });

        // =====================================================
        // RECEIVER 3: Sync on Join - WITH LAST MODE RESTORE!
        // =====================================================
        ServerPlayNetworking.registerGlobalReceiver(SyncOnJoinPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                // NEW: Restore last used mode instead of always using globalEnabled
                UniversalSpawnerMod.modEnabled = payload.lastUsedMode();

                // Sync config values
                ModConfig.setMinSpawnDelay(payload.minSpawnDelay());
                ModConfig.setMaxSpawnDelay(payload.maxSpawnDelay());
                ModConfig.setSpawnCount(payload.spawnCount());
                ModConfig.setMaxNearbyEntities(payload.maxNearbyEntities());
                ModConfig.setPlayerRange(payload.playerRange());
                ModConfig.setSpawnRange(payload.spawnRange());

                // Save the restored mode
                ModConfig.setLastUsedMode(payload.lastUsedMode());

                // Request spawner update untuk SEMUA spawner (termasuk yang existing)
                UniversalSpawnerMod.requestSpawnerUpdate();

                UniversalSpawnerMod.LOGGER.info("========================================");
                UniversalSpawnerMod.LOGGER.info("🎮 PLAYER JOIN SYNC: {}",
                        context.player().getName().getString());
                UniversalSpawnerMod.LOGGER.info("  Global Enabled: {}", payload.globalEnabled());
                UniversalSpawnerMod.LOGGER.info("  Restored Mode: {}", payload.lastUsedMode() ? "CUSTOM" : "VANILLA");
                UniversalSpawnerMod.LOGGER.info("  Config synced:");
                UniversalSpawnerMod.LOGGER.info("    - Min Delay: {} ticks", payload.minSpawnDelay());
                UniversalSpawnerMod.LOGGER.info("    - Max Delay: {} ticks", payload.maxSpawnDelay());
                UniversalSpawnerMod.LOGGER.info("    - Spawn Count: {}", payload.spawnCount());
                UniversalSpawnerMod.LOGGER.info("    - Max Nearby: {}", payload.maxNearbyEntities());
                UniversalSpawnerMod.LOGGER.info("    - Player Range: {}", payload.playerRange());
                UniversalSpawnerMod.LOGGER.info("    - Spawn Range: {}", payload.spawnRange());
                UniversalSpawnerMod.LOGGER.info("  Restoring your previous settings...");
                UniversalSpawnerMod.LOGGER.info("  Total spawners in registry: {}", UniversalSpawnerMod.getRegistrySize());
                UniversalSpawnerMod.LOGGER.info("========================================");
            });
        });

        UniversalSpawnerMod.LOGGER.info("✓ Network packets registered (with mode persistence)");
    }

    // =====================================================
    // PACKET 1: Toggle Mode (Client → Server)
    // =====================================================
    public record ToggleModePayload(boolean enabled) implements CustomPayload {
        public static final CustomPayload.Id<ToggleModePayload> ID =
                new CustomPayload.Id<>(TOGGLE_MODE_ID);

        public static final PacketCodec<RegistryByteBuf, ToggleModePayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.BOOLEAN, ToggleModePayload::enabled,
                        ToggleModePayload::new
                );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // =====================================================
    // PACKET 2: Update Config (Client → Server)
    // =====================================================
    public record UpdateConfigPayload(
            int minSpawnDelay,
            int maxSpawnDelay,
            int spawnCount,
            int maxNearbyEntities,
            int playerRange,
            int spawnRange
    ) implements CustomPayload {
        public static final CustomPayload.Id<UpdateConfigPayload> ID =
                new CustomPayload.Id<>(UPDATE_CONFIG_ID);

        public static final PacketCodec<RegistryByteBuf, UpdateConfigPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.INTEGER, UpdateConfigPayload::minSpawnDelay,
                        PacketCodecs.INTEGER, UpdateConfigPayload::maxSpawnDelay,
                        PacketCodecs.INTEGER, UpdateConfigPayload::spawnCount,
                        PacketCodecs.INTEGER, UpdateConfigPayload::maxNearbyEntities,
                        PacketCodecs.INTEGER, UpdateConfigPayload::playerRange,
                        PacketCodecs.INTEGER, UpdateConfigPayload::spawnRange,
                        UpdateConfigPayload::new
                );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // =====================================================
    // PACKET 3: Sync on Join - WITH LAST MODE!
    // =====================================================
    public record SyncOnJoinPayload(
            boolean globalEnabled,
            boolean lastUsedMode,  // NEW: Restore last used mode
            int minSpawnDelay,
            int maxSpawnDelay,
            int spawnCount,
            int maxNearbyEntities,
            int playerRange,
            int spawnRange
    ) implements CustomPayload {
        public static final CustomPayload.Id<SyncOnJoinPayload> ID =
                new CustomPayload.Id<>(SYNC_ON_JOIN_ID);

        public static final PacketCodec<RegistryByteBuf, SyncOnJoinPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.BOOLEAN, SyncOnJoinPayload::globalEnabled,
                        PacketCodecs.BOOLEAN, SyncOnJoinPayload::lastUsedMode,
                        PacketCodecs.INTEGER, SyncOnJoinPayload::minSpawnDelay,
                        PacketCodecs.INTEGER, SyncOnJoinPayload::maxSpawnDelay,
                        PacketCodecs.INTEGER, SyncOnJoinPayload::spawnCount,
                        PacketCodecs.INTEGER, SyncOnJoinPayload::maxNearbyEntities,
                        PacketCodecs.INTEGER, SyncOnJoinPayload::playerRange,
                        PacketCodecs.INTEGER, SyncOnJoinPayload::spawnRange,
                        SyncOnJoinPayload::new
                );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}