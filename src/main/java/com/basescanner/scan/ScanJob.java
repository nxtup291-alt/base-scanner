package com.basescanner.scan;

import com.basescanner.config.ModConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Incremental, time-sliced block scan of one area. Columns are visited one by one;
 * empty (all-air) chunk sections are skipped in a single step, and columns in
 * chunks the client has not loaded are counted as "not examined" instead of
 * being guessed at.
 */
public final class ScanJob {
    /** Shared cache: does a block state with an unrecognised block entity hold items? */
    private static final Map<BlockState, Boolean> INVENTORY_CACHE = new IdentityHashMap<>();

    private final int minX, maxX, minZ, maxZ, minY, maxY;
    private final AreaScanResult result = new AreaScanResult();
    private int x, z;
    private boolean done;
    private final long totalColumns;
    private long processedColumns;

    private int cachedChunkX = Integer.MIN_VALUE, cachedChunkZ = Integer.MIN_VALUE;
    private WorldChunk cachedChunk;

    public ScanJob(ScanArea area) {
        this.minX = area.minX;
        this.maxX = area.maxX;
        this.minZ = area.minZ;
        this.maxZ = area.maxZ;
        this.minY = area.minY;
        this.maxY = area.maxY;
        this.x = minX;
        this.z = minZ;
        this.totalColumns = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    public static void clearCache() {
        INVENTORY_CACHE.clear();
    }

    public boolean matches(ScanArea area) {
        return area.sameBounds(minX, maxX, minZ, maxZ, minY, maxY);
    }

    public boolean isDone() {
        return done;
    }

    public AreaScanResult result() {
        return result;
    }

    public double progress() {
        return totalColumns == 0 ? 1 : (double) processedColumns / totalColumns;
    }

    /**
     * Scans until roughly {@code budget} block reads have been spent.
     *
     * @return the budget actually used
     */
    public int step(ClientWorld world, int budget, ModConfig cfg) {
        int used = 0;
        while (!done && used < budget) {
            used += scanColumn(world, x, z, cfg);
            processedColumns++;
            x++;
            if (x > maxX) {
                x = minX;
                z++;
                if (z > maxZ) {
                    done = true;
                    result.complete = true;
                }
            }
        }
        return Math.max(used, 1);
    }

    private WorldChunk chunkAt(ClientWorld world, int cx, int cz) {
        if (cx != cachedChunkX || cz != cachedChunkZ) {
            cachedChunkX = cx;
            cachedChunkZ = cz;
            cachedChunk = ScanManager.loadedChunk(world, cx, cz);
        }
        return cachedChunk;
    }

    private int scanColumn(ClientWorld world, int x, int z, ModConfig cfg) {
        result.totalColumns++;
        WorldChunk chunk = chunkAt(world, x >> 4, z >> 4);
        if (chunk == null) return 1; // not loaded: not examined, lowers confidence
        result.loadedColumns++;

        ChunkSection[] sections = chunk.getSectionArray();
        int cost = 1;
        int lx = x & 15, lz = z & 15;
        int y = minY;
        while (y <= maxY) {
            int sectionTop = ((y >> 4) << 4) + 15;
            int yEnd = Math.min(maxY, sectionTop);
            int idx = chunk.getSectionIndex(y);
            if (idx < 0 || idx >= sections.length) {
                y = yEnd + 1;
                continue;
            }
            ChunkSection section = sections[idx];
            if (section == null || section.isEmpty()) {
                result.blocksExamined += (yEnd - y + 1);
                cost++;
                y = yEnd + 1;
                continue;
            }
            for (int yy = y; yy <= yEnd; yy++) {
                BlockState state = section.getBlockState(lx, yy & 15, lz);
                cost++;
                result.blocksExamined++;
                Classification c = BlockClassifier.classify(state);
                if (c == null) {
                    if (!state.hasBlockEntity()) continue;
                    c = inventoryFallback(world, new BlockPos(x, yy, z), state);
                    if (c == null) continue;
                }
                if (c.isSkip() || !cfg.isEnabled(c.category())) continue;
                BlockPos pos = new BlockPos(x, yy, z);
                result.add(c, pos, -1, 1);
                if (c.category() == ScanCategory.SIGNS) readSign(world, pos);
            }
            y = yEnd + 1;
        }
        return cost;
    }

    /** Containers added by other mods or not covered by the classifier still count as "other storage". */
    static Classification inventoryFallback(ClientWorld world, BlockPos pos, BlockState state) {
        Boolean known = INVENTORY_CACHE.get(state);
        if (known == null) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be == null) return null; // not created yet; decide later
            known = be instanceof Inventory;
            INVENTORY_CACHE.put(state, known);
        }
        if (!known) return null;
        return new Classification(ScanCategory.OTHER_STORAGE, state.getBlock().getName().getString(), null);
    }

    private void readSign(ClientWorld world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
        List<String> lines = new ArrayList<>();
        for (Text t : sign.getFrontText().getMessages(false)) addLine(lines, t);
        for (Text t : sign.getBackText().getMessages(false)) addLine(lines, t);
        if (!lines.isEmpty()) result.signs.add(new AreaScanResult.SignInfo(pos, lines));
    }

    private static void addLine(List<String> lines, Text t) {
        if (t == null) return;
        String s = t.getString().trim();
        if (!s.isEmpty()) lines.add(s);
    }
}
