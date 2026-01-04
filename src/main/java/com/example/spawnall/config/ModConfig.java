package com.example.spawnall.config;

import com.example.spawnall.UniversalSpawnerMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Config manager untuk Universal Spawner Mod
 * WITH persistent mode state
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(
            FabricLoader.getInstance().getConfigDir().toFile(),
            "spawn-all.json"
    );

    // =====================================================
    // CONFIG FIELDS (Persistent)
    // =====================================================

    // Master toggle - enable/disable custom mode
    // DEFAULT: true (mod installed), tapi lastUsedMode yang tentukan behavior
    public boolean globalEnabled = true;

    // Toggle untuk keybind on/off in-game
    public boolean keybindEnabled = true;

    // NEW: Last used mode (true = CUSTOM, false = VANILLA)
    // DEFAULT: false (VANILLA) - Mod pertama kali dipasang akan pakai vanilla behavior
    // Player harus explicitly enable CUSTOM mode via keybind atau config
    public boolean lastUsedMode = false;

    // Spawn parameters (untuk custom mode)
    public int minSpawnDelay = 40;      // ticks (2 detik)
    public int maxSpawnDelay = 100;     // ticks (5 detik)
    public int spawnCount = 6;          // mobs per cycle
    public int maxNearbyEntities = 12;  // max mobs di sekitar
    public int playerRange = 32;        // blocks
    public int spawnRange = 5;          // blocks

    // =====================================================
    // SINGLETON INSTANCE
    // =====================================================

    private static ModConfig INSTANCE;

    /**
     * Constructor kosong untuk Gson deserialization
     */
    public ModConfig() {
        // Kosong - hindari recursion
    }

    /**
     * Load config dari file atau buat default
     */
    public static ModConfig load() {
        if (INSTANCE != null) return INSTANCE;

        INSTANCE = new ModConfig();

        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    // Copy all fields
                    INSTANCE.globalEnabled = loaded.globalEnabled;
                    INSTANCE.keybindEnabled = loaded.keybindEnabled;
                    INSTANCE.lastUsedMode = loaded.lastUsedMode; // NEW: Load last used mode
                    INSTANCE.minSpawnDelay = loaded.minSpawnDelay;
                    INSTANCE.maxSpawnDelay = loaded.maxSpawnDelay;
                    INSTANCE.spawnCount = loaded.spawnCount;
                    INSTANCE.maxNearbyEntities = loaded.maxNearbyEntities;
                    INSTANCE.playerRange = loaded.playerRange;
                    INSTANCE.spawnRange = loaded.spawnRange;

                    UniversalSpawnerMod.LOGGER.info("✓ Config loaded from file");
                    UniversalSpawnerMod.LOGGER.info("  Last used mode: {}",
                            loaded.lastUsedMode ? "CUSTOM" : "VANILLA");
                }
            } catch (IOException e) {
                UniversalSpawnerMod.LOGGER.error("Failed to load config: {}", e.getMessage());
            }
        } else {
            save();
            UniversalSpawnerMod.LOGGER.info("✓ Created default config file");
        }
        return INSTANCE;
    }

    /**
     * Save config ke file
     */
    public static void save() {
        if (INSTANCE == null) return;
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
            UniversalSpawnerMod.LOGGER.debug("Config saved");
        } catch (IOException e) {
            UniversalSpawnerMod.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    // =====================================================
    // GETTERS & SETTERS - Global Enabled
    // =====================================================

    public static boolean isGlobalEnabled() {
        return load().globalEnabled;
    }

    public static void setGlobalEnabled(boolean value) {
        load().globalEnabled = value;
        save();
    }

    // =====================================================
    // GETTERS & SETTERS - Keybind Enabled
    // =====================================================

    public static boolean isKeybindEnabled() {
        return load().keybindEnabled;
    }

    public static void setKeybindEnabled(boolean value) {
        load().keybindEnabled = value;
        save();
    }

    // =====================================================
    // NEW: GETTERS & SETTERS - Last Used Mode
    // =====================================================

    public static boolean getLastUsedMode() {
        return load().lastUsedMode;
    }

    public static void setLastUsedMode(boolean value) {
        load().lastUsedMode = value;
        save();
        UniversalSpawnerMod.LOGGER.debug("Last used mode saved: {}", value ? "CUSTOM" : "VANILLA");
    }

    // =====================================================
    // GETTERS & SETTERS - Spawn Parameters
    // =====================================================

    public static int getMinSpawnDelay() {
        return load().minSpawnDelay;
    }

    public static void setMinSpawnDelay(int value) {
        load().minSpawnDelay = Math.max(10, Math.min(200, value));
        save();
    }

    public static int getMaxSpawnDelay() {
        return load().maxSpawnDelay;
    }

    public static void setMaxSpawnDelay(int value) {
        load().maxSpawnDelay = Math.max(20, Math.min(400, value));
        save();
    }

    public static int getSpawnCount() {
        return load().spawnCount;
    }

    public static void setSpawnCount(int value) {
        load().spawnCount = Math.max(1, Math.min(10, value));
        save();
    }

    public static int getMaxNearbyEntities() {
        return load().maxNearbyEntities;
    }

    public static void setMaxNearbyEntities(int value) {
        load().maxNearbyEntities = Math.max(4, Math.min(20, value));
        save();
    }

    public static int getPlayerRange() {
        return load().playerRange;
    }

    public static void setPlayerRange(int value) {
        load().playerRange = Math.max(16, Math.min(128, value));
        save();
    }

    public static int getSpawnRange() {
        return load().spawnRange;
    }

    public static void setSpawnRange(int value) {
        load().spawnRange = Math.max(2, Math.min(10, value));
        save();
    }

    public static ModConfig getInstance() {
        return load();
    }
}