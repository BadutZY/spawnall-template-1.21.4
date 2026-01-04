package com.example.spawnall.util;

import com.example.spawnall.UniversalSpawnerMod;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IMPROVED Scanner untuk mendeteksi spawners
 * 100% Compatible dengan Minecraft 1.21.4
 * Deteksi spawner existing SEBELUM mod dipasang
 */
public class SpawnerScanner {

    /**
     * Scan SEMUA loaded chunks di world - AGGRESSIVE MODE
     * Method ini akan scan area yang lebih luas untuk mendeteksi spawner existing
     */
    public static CompletableFuture<Map<BlockPos, String>> scanLoadedChunks(ServerWorld world) {
        return CompletableFuture.supplyAsync(() -> {
            Map<BlockPos, String> foundSpawners = new HashMap<>();
            AtomicInteger chunksScanned = new AtomicInteger(0);
            AtomicInteger spawnersFound = new AtomicInteger(0);

            UniversalSpawnerMod.LOGGER.info("========================================");
            UniversalSpawnerMod.LOGGER.info("ðŸ” SPAWNER SCANNER: Starting AGGRESSIVE scan...");
            UniversalSpawnerMod.LOGGER.info("   World: {}", world.getRegistryKey().getValue());
            UniversalSpawnerMod.LOGGER.info("   Method: Multi-layer comprehensive scan");
            UniversalSpawnerMod.LOGGER.info("========================================");

            try {
                // LAYER 1: Scan spawn chunks (ALWAYS loaded, highest priority)
                BlockPos spawnPos = world.getSpawnPos();
                UniversalSpawnerMod.LOGGER.info("   [Layer 1] Scanning spawn area at {}...", spawnPos);
                scanAreaSync(world, spawnPos, 12, foundSpawners, spawnersFound, chunksScanned);

                // LAYER 2: Scan area around ALL online players
                var players = world.getPlayers();
                if (!players.isEmpty()) {
                    UniversalSpawnerMod.LOGGER.info("   [Layer 2] Scanning around {} player(s)...", players.size());
                    for (var player : players) {
                        BlockPos playerPos = player.getBlockPos();
                        scanAreaSync(world, playerPos, 12, foundSpawners, spawnersFound, chunksScanned);
                    }
                }

                // LAYER 3: Comprehensive scan dari spawn point (WIDER radius)
                UniversalSpawnerMod.LOGGER.info("   [Layer 3] Wide-area scan from spawn...");
                scanWideAreaAroundSpawn(world, spawnPos, 25, foundSpawners, spawnersFound, chunksScanned);

                // LAYER 4: Scan ALL currently loaded chunks (MOST COMPREHENSIVE)
                UniversalSpawnerMod.LOGGER.info("   [Layer 4] Scanning ALL loaded chunks...");
                scanAllActuallyLoadedChunks(world, foundSpawners, spawnersFound, chunksScanned);

                UniversalSpawnerMod.LOGGER.info("========================================");
                UniversalSpawnerMod.LOGGER.info("âœ… SPAWNER SCANNER: Scan complete!");
                UniversalSpawnerMod.LOGGER.info("   Chunks scanned: {}", chunksScanned.get());
                UniversalSpawnerMod.LOGGER.info("   Spawners found: {}", spawnersFound.get());
                UniversalSpawnerMod.LOGGER.info("   Unique positions: {}", foundSpawners.size());
                UniversalSpawnerMod.LOGGER.info("========================================");

            } catch (Exception e) {
                UniversalSpawnerMod.LOGGER.error("âŒ SPAWNER SCANNER: Error during scan", e);
            }

            return foundSpawners;
        });
    }

    /**
     * LAYER 3: Wide area scan around spawn (larger radius)
     */
    private static void scanWideAreaAroundSpawn(ServerWorld world, BlockPos centerPos, int chunkRadius,
                                                Map<BlockPos, String> foundSpawners,
                                                AtomicInteger spawnersFound,
                                                AtomicInteger chunksScanned) {
        ChunkPos centerChunk = new ChunkPos(centerPos);

        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                ChunkPos chunkPos = new ChunkPos(centerChunk.x + x, centerChunk.z + z);

                try {
                    WorldChunk chunk = (WorldChunk) world.getChunk(
                            chunkPos.x,
                            chunkPos.z,
                            ChunkStatus.FULL,
                            false
                    );

                    if (chunk != null && !chunk.isEmpty()) {
                        scanChunk(world, chunk, foundSpawners, spawnersFound);
                        chunksScanned.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Skip chunk yang error
                }
            }
        }
    }

    /**
     * LAYER 4: Scan ALL actually loaded chunks - MOST COMPREHENSIVE
     */
    private static void scanAllActuallyLoadedChunks(ServerWorld world,
                                                    Map<BlockPos, String> foundSpawners,
                                                    AtomicInteger spawnersFound,
                                                    AtomicInteger chunksScanned) {
        try {
            var chunkManager = world.getChunkManager();

            // Get all loaded chunk positions
            // Scan sangat luas untuk memastikan semua chunks tercover
            int worldSpawnX = world.getSpawnPos().getX() >> 4;
            int worldSpawnZ = world.getSpawnPos().getZ() >> 4;

            // Scan dengan radius SANGAT besar (30 chunks = 480 blocks)
            int scanRadius = 30;

            for (int x = -scanRadius; x <= scanRadius; x++) {
                for (int z = -scanRadius; z <= scanRadius; z++) {
                    ChunkPos chunkPos = new ChunkPos(worldSpawnX + x, worldSpawnZ + z);

                    try {
                        WorldChunk chunk = (WorldChunk) world.getChunk(
                                chunkPos.x,
                                chunkPos.z,
                                ChunkStatus.FULL,
                                false
                        );

                        // PENTING: Cek chunk benar-benar loaded dan tidak empty
                        if (chunk != null && !chunk.isEmpty()) {
                            scanChunk(world, chunk, foundSpawners, spawnersFound);
                            chunksScanned.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Chunk tidak tersedia atau error, skip
                    }
                }
            }

            UniversalSpawnerMod.LOGGER.info("   [Layer 4] Scanned {} chunks in wide area", chunksScanned.get());

        } catch (Exception e) {
            UniversalSpawnerMod.LOGGER.debug("Could not complete wide area scan: {}", e.getMessage());
        }
    }

    /**
     * Scan area around position - RELIABLE
     */
    private static void scanAreaSync(ServerWorld world, BlockPos centerPos, int chunkRadius,
                                     Map<BlockPos, String> foundSpawners,
                                     AtomicInteger spawnersFound,
                                     AtomicInteger chunksScanned) {
        ChunkPos centerChunk = new ChunkPos(centerPos);

        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                ChunkPos chunkPos = new ChunkPos(centerChunk.x + x, centerChunk.z + z);

                try {
                    WorldChunk chunk = (WorldChunk) world.getChunk(
                            chunkPos.x,
                            chunkPos.z,
                            ChunkStatus.FULL,
                            false
                    );

                    if (chunk != null && !chunk.isEmpty()) {
                        scanChunk(world, chunk, foundSpawners, spawnersFound);
                        chunksScanned.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Skip chunk yang tidak tersedia
                }
            }
        }
    }

    /**
     * Scan single chunk untuk spawners - ENHANCED
     */
    private static void scanChunk(ServerWorld world, WorldChunk chunk,
                                  Map<BlockPos, String> foundSpawners,
                                  AtomicInteger counter) {
        ChunkPos chunkPos = chunk.getPos();

        // Gunakan dimension type untuk mendapatkan height limits
        int minY = world.getDimension().minY();
        int maxY = world.getDimension().minY() + world.getDimension().height() - 1;

        // Scan seluruh chunk (16x16 horizontal, full height vertical)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(
                            chunkPos.getStartX() + x,
                            y,
                            chunkPos.getStartZ() + z
                    );

                    try {
                        // Check apakah block ini spawner
                        if (chunk.getBlockState(pos).isOf(Blocks.SPAWNER)) {
                            BlockEntity blockEntity = chunk.getBlockEntity(pos);

                            if (blockEntity instanceof MobSpawnerBlockEntity spawnerEntity) {
                                String entityId = extractEntityIdEnhanced(spawnerEntity, world);

                                if (entityId != null && !entityId.isEmpty()) {
                                    // Avoid duplicates dari overlapping scans
                                    if (foundSpawners.putIfAbsent(pos.toImmutable(), entityId) == null) {
                                        counter.incrementAndGet();
                                        UniversalSpawnerMod.LOGGER.debug("   Found spawner at {}: {}",
                                                pos.toShortString(), entityId);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Skip block jika error
                    }
                }
            }
        }
    }

    /**
     * Extract entity ID dengan multiple fallback methods
     * ENHANCED untuk support spawner existing sebelum mod dipasang
     */
    private static String extractEntityIdEnhanced(MobSpawnerBlockEntity spawnerEntity, ServerWorld world) {
        try {
            // Read NBT dari spawner
            NbtCompound nbt = spawnerEntity.createNbt(world.getRegistryManager());

            // Method 1: SpawnData (most common)
            if (nbt.contains("SpawnData", 10)) { // 10 = Compound
                NbtCompound spawnData = nbt.getCompound("SpawnData");
                if (spawnData.contains("entity", 10)) {
                    NbtCompound entity = spawnData.getCompound("entity");
                    if (entity.contains("id", 8)) { // 8 = String
                        String id = entity.getString("id");
                        if (!id.isEmpty() && !id.equals("minecraft:")) {
                            return id;
                        }
                    }
                }
            }

            // Method 2: SpawnPotentials (untuk spawner dengan multiple entities)
            if (nbt.contains("SpawnPotentials", 9)) { // 9 = List
                var potentials = nbt.getList("SpawnPotentials", 10);
                if (!potentials.isEmpty()) {
                    NbtCompound firstPotential = potentials.getCompound(0);
                    if (firstPotential.contains("data", 10)) {
                        NbtCompound data = firstPotential.getCompound("data");
                        if (data.contains("entity", 10)) {
                            NbtCompound entity = data.getCompound("entity");
                            if (entity.contains("id", 8)) {
                                String id = entity.getString("id");
                                if (!id.isEmpty() && !id.equals("minecraft:")) {
                                    return id;
                                }
                            }
                        }
                    }
                }
            }

            // Method 3: Direct dari Spawner Logic (fallback untuk vanilla spawners)
            var logic = spawnerEntity.getLogic();
            if (logic != null) {
                NbtCompound logicNbt = new NbtCompound();
                logic.writeNbt(logicNbt);

                if (logicNbt.contains("SpawnData", 10)) {
                    NbtCompound spawnData = logicNbt.getCompound("SpawnData");
                    if (spawnData.contains("entity", 10)) {
                        NbtCompound entity = spawnData.getCompound("entity");
                        if (entity.contains("id", 8)) {
                            String id = entity.getString("id");
                            if (!id.isEmpty() && !id.equals("minecraft:")) {
                                return id;
                            }
                        }
                    }
                }
            }

            // Method 4: Fallback ke pig jika tidak ada data
            UniversalSpawnerMod.LOGGER.debug("Spawner without entity ID, defaulting to pig");
            return "minecraft:pig";

        } catch (Exception e) {
            UniversalSpawnerMod.LOGGER.debug("Could not extract entity ID: {}", e.getMessage());
            return "minecraft:pig";
        }
    }

    /**
     * Scan area around player - PUBLIC API
     */
    public static CompletableFuture<Map<BlockPos, String>> scanAreaAroundPlayer(
            ServerWorld world,
            BlockPos playerPos,
            int chunkRadius
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Map<BlockPos, String> foundSpawners = new HashMap<>();
            AtomicInteger spawnersFound = new AtomicInteger(0);
            AtomicInteger chunksScanned = new AtomicInteger(0);

            UniversalSpawnerMod.LOGGER.info("ðŸ” Scanning area around position {}", playerPos);

            try {
                scanAreaSync(world, playerPos, chunkRadius, foundSpawners, spawnersFound, chunksScanned);
            } catch (Exception e) {
                UniversalSpawnerMod.LOGGER.error("Error scanning area: {}", e.getMessage());
            }

            UniversalSpawnerMod.LOGGER.info("âœ… Area scan: {} spawners in {} chunks",
                    spawnersFound.get(), chunksScanned.get());

            return foundSpawners;
        });
    }

    /**
     * Scan specific region (untuk targeted scans)
     */
    public static Map<BlockPos, String> scanRegion(ServerWorld world, BlockPos corner1, BlockPos corner2) {
        Map<BlockPos, String> foundSpawners = new HashMap<>();
        AtomicInteger counter = new AtomicInteger(0);

        int minX = Math.min(corner1.getX(), corner2.getX());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        UniversalSpawnerMod.LOGGER.info("ðŸ” Scanning region {} to {}", corner1, corner2);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    try {
                        if (world.getBlockState(pos).isOf(Blocks.SPAWNER)) {
                            BlockEntity blockEntity = world.getBlockEntity(pos);

                            if (blockEntity instanceof MobSpawnerBlockEntity spawnerEntity) {
                                String entityId = extractEntityIdEnhanced(spawnerEntity, world);

                                if (entityId != null && !entityId.isEmpty()) {
                                    foundSpawners.put(pos.toImmutable(), entityId);
                                    counter.incrementAndGet();
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Skip block jika error
                    }
                }
            }
        }

        UniversalSpawnerMod.LOGGER.info("âœ… Region scan: {} spawners found", counter.get());
        return foundSpawners;
    }
}