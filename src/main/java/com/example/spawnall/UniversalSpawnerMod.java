package com.example.spawnall;

import com.example.spawnall.config.ModConfig;
import com.example.spawnall.network.NetworkHandler;
import com.example.spawnall.util.SpawnerScanner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UniversalSpawnerMod implements ModInitializer {
    public static final String MOD_ID = "spawn-all";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // In-game toggle state (synchronized across server)
    public static volatile boolean modEnabled = true;

    // Track spawner positions dan entity types (thread-safe untuk multiplayer)
    private static final Map<BlockPos, String> SPAWNER_REGISTRY = new ConcurrentHashMap<>();

    // Flag untuk request update spawners
    private static volatile boolean updateRequested = false;

    // Flag untuk prevent multiple scans per world
    private static final Map<ServerWorld, Boolean> WORLD_SCANNED = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("========================================");
        LOGGER.info("Spawn All Mod - UNIVERSAL VERSION");
        LOGGER.info("  Compatible: Singleplayer + Multiplayer");
        LOGGER.info("  Auto-detect existing spawners: YES");
        LOGGER.info("  Mode persistence: ENABLED");
        LOGGER.info("  Default behavior: VANILLA (first install)");
        LOGGER.info("========================================");

        // Load config dan restore last used mode
        ModConfig.load();
        modEnabled = ModConfig.getLastUsedMode(); // NEW: Use last mode instead of global

        String initialMode = modEnabled ? "CUSTOM" : "VANILLA";
        LOGGER.info("📋 Initial mode: {} {}", initialMode,
                modEnabled ? "(Fast spawning enabled)" : "(Minecraft default)");

        if (!modEnabled) {
            LOGGER.info("💡 Tip: Press K to enable CUSTOM mode for faster spawning");
        }

        // Register network packets (hanya untuk multiplayer)
        NetworkHandler.registerPackets();

        // =====================================================
        // EVENT 0: World Load - SCAN EXISTING SPAWNERS
        // =====================================================
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Scan semua worlds (overworld, nether, end)
            server.getWorlds().forEach(world -> {
                LOGGER.info("========================================");
                LOGGER.info("🌍 World loaded: {}", world.getRegistryKey().getValue());
                LOGGER.info("  Starting spawner detection...");
                LOGGER.info("========================================");

                // Jalankan scan dengan delay untuk memastikan chunks loaded
                server.execute(() -> {
                    try {
                        Thread.sleep(3000); // 3 second delay
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    scanAndRegisterSpawners(world);
                });
            });
        });

        // =====================================================
        // EVENT 0.5: Player Join - RESCAN untuk player baru
        // =====================================================
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            ServerWorld world = player.getServerWorld();

            LOGGER.info("========================================");
            LOGGER.info("👤 Player joined: {}", player.getName().getString());
            LOGGER.info("  Triggering spawner rescan around player...");
            LOGGER.info("========================================");

            // Delay untuk memastikan player fully loaded
            server.execute(() -> {
                try {
                    Thread.sleep(2000); // 2 second delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Scan area around player (lebih targeted)
                BlockPos playerPos = player.getBlockPos();
                SpawnerScanner.scanAreaAroundPlayer(world, playerPos, 15).thenAccept(foundSpawners -> {
                    if (!foundSpawners.isEmpty()) {
                        // Register spawners baru yang ditemukan
                        int newSpawners = 0;
                        for (Map.Entry<BlockPos, String> entry : foundSpawners.entrySet()) {
                            if (SPAWNER_REGISTRY.putIfAbsent(entry.getKey(), entry.getValue()) == null) {
                                newSpawners++;
                            }
                        }

                        if (newSpawners > 0) {
                            LOGGER.info("✓ Found {} new spawner(s) around player", newSpawners);

                            // Update spawners dengan config yang tersimpan (last mode)
                            for (Map.Entry<BlockPos, String> entry : foundSpawners.entrySet()) {
                                BlockPos pos = entry.getKey();
                                String entityId = entry.getValue();

                                if (world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawnerEntity) {
                                    configureSpawner(world, pos, spawnerEntity, entityId);
                                }
                            }
                        }

                        // Send notification to player dengan mode yang tersimpan
                        int totalSpawners = SPAWNER_REGISTRY.size();
                        String mode = isModEnabled() ? "§aCUSTOM" : "§eVANILLA";
                        player.sendMessage(
                                Text.literal("§6[Spawn All] §7Detected " + totalSpawners +
                                        " spawner(s) - Mode: " + mode),
                                false
                        );
                    }
                });
            });
        });

        // =====================================================
        // EVENT 1: Register spawner saat spawn egg digunakan
        // =====================================================
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) {
                return ActionResult.PASS;
            }

            ItemStack heldItem = player.getStackInHand(hand);
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (state.isOf(Blocks.SPAWNER) && heldItem.getItem() instanceof SpawnEggItem spawnEgg) {

                if (world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawnerEntity) {
                    try {
                        EntityType<?> entityType = spawnEgg.getEntityType(world.getRegistryManager(), heldItem);
                        String entityId = EntityType.getId(entityType).toString();

                        // Register spawner (immutable pos)
                        SPAWNER_REGISTRY.put(pos.toImmutable(), entityId);

                        // Configure spawner dengan mode saat ini (last used mode)
                        configureSpawner(world, pos, spawnerEntity, entityId);

                        // Decrement item (kecuali creative)
                        if (!player.getAbilities().creativeMode) {
                            heldItem.decrement(1);
                        }

                        // Send confirmation message to player
                        String modeName = isModEnabled() ? "§aCUSTOM" : "§eVANILLA";
                        player.sendMessage(
                                Text.literal("§6[Spawn All] §fSpawner configured: " + modeName),
                                true
                        );

                        // Detect environment
                        String env = isSingleplayer(world) ? "SINGLEPLAYER" : "MULTIPLAYER";
                        LOGGER.info("✓ Spawner registered at {} by {} [{}] (Mode: {})",
                                pos.toShortString(),
                                player.getName().getString(),
                                env,
                                isModEnabled() ? "CUSTOM" : "VANILLA");

                        return ActionResult.SUCCESS;

                    } catch (Exception e) {
                        LOGGER.error("✗ Error configuring spawner:", e);
                        player.sendMessage(
                                Text.literal("§c[Spawn All] Error configuring spawner!"),
                                false
                        );
                        return ActionResult.FAIL;
                    }
                }
            }

            return ActionResult.PASS;
        });

        // =====================================================
        // EVENT 2: Hapus spawner dari registry saat dihancurkan
        // =====================================================
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClient && state.isOf(Blocks.SPAWNER)) {
                // Hapus dari registry
                String removed = SPAWNER_REGISTRY.remove(pos.toImmutable());
                if (removed != null) {
                    LOGGER.info("🗑 Spawner removed from registry at {} by {} (was: {})",
                            pos.toShortString(),
                            player.getName().getString(),
                            removed);
                }
            }
            return true;
        });

        // =====================================================
        // EVENT 3: SERVER TICK - Update spawners yang di-request
        // =====================================================
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (updateRequested && world instanceof ServerWorld serverWorld) {
                updateRequested = false; // Reset flag

                String env = isSingleplayer(world) ? "SINGLEPLAYER" : "MULTIPLAYER";

                LOGGER.info("========================================");
                LOGGER.info("🔄 SERVER TICK [{}]: Updating spawners...", env);
                LOGGER.info("  World: {}", serverWorld.getRegistryKey().getValue());
                LOGGER.info("  Total spawners: {}", SPAWNER_REGISTRY.size());
                LOGGER.info("  New mode: {}", (isModEnabled() ? "CUSTOM" : "VANILLA"));
                LOGGER.info("========================================");

                int updated = 0;
                int failed = 0;
                int cleaned = 0;

                for (Map.Entry<BlockPos, String> entry : SPAWNER_REGISTRY.entrySet()) {
                    BlockPos pos = entry.getKey();
                    String entityId = entry.getValue();

                    // Cek apakah spawner masih ada
                    if (serverWorld.getBlockState(pos).isOf(Blocks.SPAWNER)) {
                        if (serverWorld.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawnerEntity) {
                            try {
                                configureSpawner(serverWorld, pos, spawnerEntity, entityId);
                                updated++;
                            } catch (Exception e) {
                                LOGGER.error("Failed to update spawner at {}: {}", pos, e.getMessage());
                                failed++;
                            }
                        }
                    } else {
                        // Spawner tidak ada lagi, hapus dari registry
                        SPAWNER_REGISTRY.remove(pos);
                        cleaned++;
                    }
                }

                LOGGER.info("========================================");
                LOGGER.info("✓ SERVER UPDATE COMPLETE [{}]:", env);
                LOGGER.info("  Updated: {}", updated);
                LOGGER.info("  Failed: {}", failed);
                LOGGER.info("  Cleaned: {}", cleaned);
                LOGGER.info("========================================");

                // Broadcast update (hanya di multiplayer)
                if (!isSingleplayer(world) && updated > 0) {
                    serverWorld.getServer().getPlayerManager().broadcast(
                            Text.literal("§6[Spawn All] §7Updated " + updated + " spawner(s) to " +
                                    (isModEnabled() ? "§aCUSTOM" : "§eVANILLA") + " §7mode"),
                            false
                    );
                }
            }
        });

        LOGGER.info("✓ Mod initialized");
        LOGGER.info("  Mode: Universal (Single + Multi)");
        LOGGER.info("  Registry: Thread-safe ConcurrentHashMap");
        LOGGER.info("  Networking: Enabled for multiplayer");
    }

    /**
     * HELPER: Scan dan register semua spawners di world
     */
    private static void scanAndRegisterSpawners(ServerWorld world) {
        // Prevent duplicate scans
        if (WORLD_SCANNED.getOrDefault(world, false)) {
            LOGGER.info("⏭ World already scanned, skipping...");
            return;
        }

        WORLD_SCANNED.put(world, true);

        SpawnerScanner.scanLoadedChunks(world).thenAccept(foundSpawners -> {
            if (!foundSpawners.isEmpty()) {
                // Register semua spawner yang ditemukan
                SPAWNER_REGISTRY.putAll(foundSpawners);

                LOGGER.info("========================================");
                LOGGER.info("✓ EXISTING SPAWNERS DETECTED");
                LOGGER.info("  World: {}", world.getRegistryKey().getValue());
                LOGGER.info("  Total found: {}", foundSpawners.size());
                LOGGER.info("  Applying saved config...");
                LOGGER.info("  Restored Mode: {}", isModEnabled() ? "CUSTOM" : "VANILLA");
                if (!isModEnabled()) {
                    LOGGER.info("  💡 Spawners will use vanilla behavior (press K to enable custom mode)");
                }
                LOGGER.info("========================================");

                // Update semua spawner dengan config yang tersimpan (last mode)
                int successCount = 0;
                for (Map.Entry<BlockPos, String> entry : foundSpawners.entrySet()) {
                    BlockPos pos = entry.getKey();
                    String entityId = entry.getValue();

                    try {
                        if (world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawnerEntity) {
                            configureSpawner(world, pos, spawnerEntity, entityId);
                            successCount++;
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to configure spawner at {}: {}", pos, e.getMessage());
                    }
                }

                LOGGER.info("========================================");
                LOGGER.info("✓ ALL EXISTING SPAWNERS UPDATED");
                LOGGER.info("  Successfully configured: {}/{}", successCount, foundSpawners.size());
                LOGGER.info("  Mode applied: {}", isModEnabled() ? "CUSTOM" : "VANILLA");
                LOGGER.info("  Config source: spawn-all.json (last used mode)");
                LOGGER.info("========================================");
            } else {
                LOGGER.info("⏹ No existing spawners found in loaded chunks");
            }
        });
    }

    /**
     * Configure spawner dengan mode yang aktif
     * WORKS IN BOTH SINGLEPLAYER AND MULTIPLAYER
     */
    public static void configureSpawner(World world, BlockPos pos, MobSpawnerBlockEntity spawnerEntity, String entityId) {
        // Cek mod status (global + in-game toggle) - menggunakan last used mode
        boolean isModEnabled = ModConfig.isGlobalEnabled() && modEnabled;

        String env = isSingleplayer(world) ? "SP" : "MP";
        LOGGER.debug("🔧 [{}] Configuring spawner at {}: {} (Mode: {})",
                env, pos.toShortString(), entityId, (isModEnabled ? "CUSTOM" : "VANILLA"));

        // Buat entity NBT
        NbtCompound entityNbt = new NbtCompound();
        entityNbt.putString("id", entityId);

        // Buat spawner NBT
        NbtCompound spawnerNbt = new NbtCompound();

        // SpawnData
        NbtCompound spawnData = new NbtCompound();
        spawnData.put("entity", entityNbt);

        if (isModEnabled) {
            // ============================================
            // MODE: CUSTOM (MOD ENABLED)
            // ============================================

            // CUSTOM SPAWN RULES
            NbtCompound customSpawnRules = new NbtCompound();

            NbtCompound blockLightLimit = new NbtCompound();
            blockLightLimit.putInt("min_inclusive", 0);
            blockLightLimit.putInt("max_inclusive", 15);
            customSpawnRules.put("block_light_limit", blockLightLimit);

            NbtCompound skyLightLimit = new NbtCompound();
            skyLightLimit.putInt("min_inclusive", 0);
            skyLightLimit.putInt("max_inclusive", 15);
            customSpawnRules.put("sky_light_limit", skyLightLimit);

            spawnData.put("custom_spawn_rules", customSpawnRules);

            // SpawnPotentials dengan custom rules
            NbtList potentials = new NbtList();
            NbtCompound potential = new NbtCompound();
            potential.putInt("weight", 1);

            NbtCompound potentialData = new NbtCompound();
            potentialData.put("entity", entityNbt.copy());
            potentialData.put("custom_spawn_rules", customSpawnRules.copy());
            potential.put("data", potentialData);

            potentials.add(potential);
            spawnerNbt.put("SpawnPotentials", potentials);

            // CUSTOM PARAMETERS (dari config)
            int minDelay = ModConfig.getMinSpawnDelay();
            int maxDelay = ModConfig.getMaxSpawnDelay();
            int spawnCount = ModConfig.getSpawnCount();
            int maxNearby = ModConfig.getMaxNearbyEntities();
            int playerRange = ModConfig.getPlayerRange();
            int spawnRange = ModConfig.getSpawnRange();

            spawnerNbt.putShort("Delay", (short)minDelay);
            spawnerNbt.putShort("MinSpawnDelay", (short)minDelay);
            spawnerNbt.putShort("MaxSpawnDelay", (short)maxDelay);
            spawnerNbt.putShort("SpawnCount", (short)spawnCount);
            spawnerNbt.putShort("MaxNearbyEntities", (short)maxNearby);
            spawnerNbt.putShort("RequiredPlayerRange", (short)playerRange);
            spawnerNbt.putShort("SpawnRange", (short)spawnRange);

            LOGGER.debug("  ⚙ CUSTOM: {}-{}s delay, {} mobs, {} range",
                    minDelay/20.0f, maxDelay/20.0f, spawnCount, playerRange);

        } else {
            // ============================================
            // MODE: VANILLA (MOD DISABLED)
            // ============================================

            // SpawnPotentials vanilla (NO custom rules)
            NbtList potentials = new NbtList();
            NbtCompound potential = new NbtCompound();
            potential.putInt("weight", 1);

            NbtCompound potentialData = new NbtCompound();
            potentialData.put("entity", entityNbt.copy());
            potential.put("data", potentialData);

            potentials.add(potential);
            spawnerNbt.put("SpawnPotentials", potentials);

            // VANILLA PARAMETERS
            spawnerNbt.putShort("Delay", (short)20);
            spawnerNbt.putShort("MinSpawnDelay", (short)200);
            spawnerNbt.putShort("MaxSpawnDelay", (short)800);
            spawnerNbt.putShort("SpawnCount", (short)4);
            spawnerNbt.putShort("MaxNearbyEntities", (short)6);
            spawnerNbt.putShort("RequiredPlayerRange", (short)16);
            spawnerNbt.putShort("SpawnRange", (short)4);

            LOGGER.debug("  ⚙ VANILLA: 10-40s delay, 4 mobs, 16 range");
        }

        spawnerNbt.put("SpawnData", spawnData);

        // Apply NBT ke spawner (thread-safe)
        synchronized (spawnerEntity) {
            spawnerEntity.read(spawnerNbt, world.getRegistryManager());
            spawnerEntity.markDirty();
        }

        // Force block update
        BlockState state = world.getBlockState(pos);
        world.updateListeners(pos, state, state, 3);
    }

    /**
     * Request update untuk semua spawner
     * WORKS IN BOTH SINGLEPLAYER AND MULTIPLAYER
     */
    public static void requestSpawnerUpdate() {
        updateRequested = true;
        LOGGER.info("🔄 Spawner update REQUESTED (will process on next server tick)");
    }

    /**
     * Check if running in singleplayer
     */
    public static boolean isSingleplayer(World world) {
        if (world.isClient) return false;
        return world.getServer() != null && world.getServer().isSingleplayer();
    }

    /**
     * Get registry info (thread-safe copy)
     */
    public static Map<BlockPos, String> getSpawnerRegistry() {
        return new HashMap<>(SPAWNER_REGISTRY);
    }

    public static int getRegistrySize() {
        return SPAWNER_REGISTRY.size();
    }

    public static boolean isModEnabled() {
        return ModConfig.isGlobalEnabled() && modEnabled;
    }
}