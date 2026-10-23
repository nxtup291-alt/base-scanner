package com.basescanner.scan;

import com.basescanner.config.ConfigManager;
import com.basescanner.config.ModConfig;
import com.basescanner.gui.AreaDetailScreen;
import com.basescanner.score.BaseScorer;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Central client-side scanner.
 * <ul>
 *   <li><b>Discovery</b>: walks the block entities of loaded chunks near the player and turns
 *       storage blocks into clustered scan areas (overlapping areas are merged).</li>
 *   <li><b>Block scans</b>: each area is scanned incrementally with a per-tick block budget.</li>
 *   <li><b>Entity scans</b>: cheap, every 10 ticks per area.</li>
 *   <li><b>Updates</b>: block changes, block entity / chunk loads and a periodic timer mark areas dirty.</li>
 * </ul>
 * Everything runs on the client thread and uses only data the server has sent to the client.
 */
public final class ScanManager {
    private static final ScanManager INSTANCE = new ScanManager();
    private static final int ENTITY_INTERVAL = 10;
    private static final int LOOK_FOCUS_TICKS = 200;

    public static ScanManager get() {
        return INSTANCE;
    }

    private final List<ScanArea> areas = new ArrayList<>();
    private final Set<Long> changedBlocks = new HashSet<>();
    private ClientWorld lastWorld;
    private long tick;
    private long lastDiscoveryTick = Long.MIN_VALUE / 2;
    private boolean discoveryRequested = true;
    private boolean forceRescan;
    private int nextId = 1;

    private int pinnedAreaId = -1;
    private int lookAreaId = -1;
    private long lookUntilTick;
    private BlockPos lastAutoOpenPos;

    private ScanManager() {
    }

    // ------------------------------------------------------------------ public API

    public List<ScanArea> areas() {
        return Collections.unmodifiableList(areas);
    }

    public long tick() {
        return tick;
    }

    public ScanArea byId(int id) {
        for (ScanArea a : areas) if (a.id == id) return a;
        return null;
    }

    public void requestDiscovery() {
        discoveryRequested = true;
    }

    /** Manual "rescan now": rediscovers storage and rescans every area immediately. */
    public void rescanNow() {
        discoveryRequested = true;
        forceRescan = true;
        lastDiscoveryTick = Long.MIN_VALUE / 2;
    }

    /** Called when settings that change area geometry are edited. */
    public void onSettingsChanged() {
        BlockClassifier.clearCache();
        for (ScanArea a : areas) {
            a.job = null;
            a.dirty = true;
        }
        rescanNow();
    }

    /** Called from the client world mixin when the server changes a block. */
    public void onBlockChanged(BlockPos pos, BlockState newState) {
        changedBlocks.add(pos.asLong());
        Classification c = BlockClassifier.classify(newState);
        if (c != null && c.category() != null && c.category().triggersArea) discoveryRequested = true;
    }

    public void onBlockEntityLoaded(BlockEntity be) {
        if (be instanceof Inventory) discoveryRequested = true;
        else {
            Classification c = BlockClassifier.classify(be.getCachedState());
            if (c != null && c.category() != null && c.category().triggersArea) discoveryRequested = true;
        }
    }

    public void reset() {
        areas.clear();
        changedBlocks.clear();
        pinnedAreaId = -1;
        lookAreaId = -1;
        lastAutoOpenPos = null;
        discoveryRequested = true;
        lastDiscoveryTick = Long.MIN_VALUE / 2;
        BlockClassifier.clearCache();
        ScanJob.clearCache();
        ContainerMemory.clear();
        Navigation.clear();
    }

    /** The area shown in the HUD: looked-at storage, then pinned area, then the one you stand in, then the nearest. */
    public ScanArea focusedArea(Vec3d playerPos) {
        if (lookAreaId >= 0 && tick < lookUntilTick) {
            ScanArea a = byId(lookAreaId);
            if (a != null) return a;
        }
        if (pinnedAreaId >= 0) {
            ScanArea a = byId(pinnedAreaId);
            if (a != null) return a;
            pinnedAreaId = -1;
        }
        ScanArea best = null;
        double bestDist = Double.MAX_VALUE;
        for (ScanArea a : areas) {
            if (a.containsXZ(playerPos.x, playerPos.z)) return a;
            double d = a.horizontalDistance(playerPos);
            if (d < bestDist) {
                bestDist = d;
                best = a;
            }
        }
        return best;
    }

    public boolean isPinned() {
        return pinnedAreaId >= 0;
    }

    /** Cycles the pinned area through all areas (nearest first) and back to automatic focus. */
    public void cycleFocus(MinecraftClient client) {
        if (client.player == null) return;
        lookAreaId = -1;
        List<ScanArea> sorted = sortedByDistance(client.player.getPos());
        if (sorted.isEmpty()) {
            pinnedAreaId = -1;
            client.player.sendMessage(Text.literal("No scan areas yet").formatted(Formatting.GRAY), true);
            return;
        }
        int idx = -1;
        for (int i = 0; i < sorted.size(); i++) if (sorted.get(i).id == pinnedAreaId) idx = i;
        if (idx + 1 >= sorted.size()) {
            pinnedAreaId = -1;
            client.player.sendMessage(Text.literal("Scanner focus: automatic").formatted(Formatting.AQUA), true);
        } else {
            pinnedAreaId = sorted.get(idx + 1).id;
            client.player.sendMessage(Text.literal("Scanner focus: area #" + pinnedAreaId
                    + " (" + (idx + 2) + "/" + sorted.size() + ")").formatted(Formatting.AQUA), true);
        }
    }

    public void pin(int areaId) {
        pinnedAreaId = areaId;
        lookAreaId = -1;
    }

    public List<ScanArea> sortedByDistance(Vec3d p) {
        List<ScanArea> list = new ArrayList<>(areas);
        list.sort(Comparator.comparingDouble(a -> a.horizontalDistance(p)));
        return list;
    }

    // ------------------------------------------------------------------ tick

    public void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world != lastWorld) {
            reset();
            lastWorld = world;
        }
        ClientPlayerEntity player = client.player;
        if (world == null || player == null) return;
        tick++;
        ModConfig cfg = ConfigManager.get();

        processChangedBlocks();

        boolean due = tick - lastDiscoveryTick >= cfg.refreshIntervalTicks;
        boolean requestedReady = discoveryRequested && tick - lastDiscoveryTick >= 10;
        if ((cfg.autoScan && (due || requestedReady)) || forceRescan) {
            discover(world, player, cfg);
        }

        scheduleAndRunBlockScans(world, player, cfg);

        if (tick % ENTITY_INTERVAL == 0) {
            for (ScanArea a : areas) scanEntities(world, player, a, cfg);
        }

        updateLookFocus(client, world, cfg);
    }

    private void processChangedBlocks() {
        if (changedBlocks.isEmpty()) return;
        for (long l : changedBlocks) {
            BlockPos pos = BlockPos.fromLong(l);
            for (ScanArea a : areas) {
                if (a.contains(pos)) a.dirty = true;
                if (a.anchors.contains(pos)) discoveryRequested = true; // a storage block was removed
            }
        }
        changedBlocks.clear();
    }

    // ------------------------------------------------------------------ discovery & clustering

    private record Spot(BlockPos pos, boolean withinNewRange) {
    }

    private static final class Cluster {
        int minAX, maxAX, minAZ, maxAZ, minAY, maxAY;
        final List<BlockPos> anchors = new ArrayList<>();
        boolean anyNew;

        Cluster(Spot s) {
            minAX = maxAX = s.pos.getX();
            minAZ = maxAZ = s.pos.getZ();
            minAY = maxAY = s.pos.getY();
            anchors.add(s.pos);
            anyNew = s.withinNewRange;
        }

        void absorb(Spot s) {
            include(s.pos.getX(), s.pos.getY(), s.pos.getZ());
            anchors.add(s.pos);
            anyNew |= s.withinNewRange;
        }

        void absorb(Cluster o) {
            minAX = Math.min(minAX, o.minAX);
            maxAX = Math.max(maxAX, o.maxAX);
            minAZ = Math.min(minAZ, o.minAZ);
            maxAZ = Math.max(maxAZ, o.maxAZ);
            minAY = Math.min(minAY, o.minAY);
            maxAY = Math.max(maxAY, o.maxAY);
            anchors.addAll(o.anchors);
            anyNew |= o.anyNew;
        }

        void include(int x, int y, int z) {
            minAX = Math.min(minAX, x);
            maxAX = Math.max(maxAX, x);
            minAZ = Math.min(minAZ, z);
            maxAZ = Math.max(maxAZ, z);
            minAY = Math.min(minAY, y);
            maxAY = Math.max(maxAY, y);
        }
    }

    /** @return the chunk if it is loaded on the client, otherwise null. */
    public static WorldChunk loadedChunk(ClientWorld world, int cx, int cz) {
        Chunk chunk = world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
        if (chunk instanceof WorldChunk wc && !(chunk instanceof EmptyChunk)) return wc;
        return null;
    }

    private void discover(ClientWorld world, ClientPlayerEntity player, ModConfig cfg) {
        lastDiscoveryTick = tick;
        discoveryRequested = false;
        boolean rescanAll = forceRescan;
        forceRescan = false;

        double px = player.getX(), pz = player.getZ();
        int newRange = cfg.maxScanDistance;
        int keepRange = cfg.maxScanDistance + 32; // hysteresis so areas don't flicker at the edge
        int pcx = MathHelper.floor(px) >> 4, pcz = MathHelper.floor(pz) >> 4;
        int cr = (keepRange >> 4) + 1;

        List<Spot> spots = new ArrayList<>();
        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                WorldChunk chunk = loadedChunk(world, cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getPos();
                    double dx = pos.getX() + 0.5 - px, dz = pos.getZ() + 0.5 - pz;
                    double d2 = dx * dx + dz * dz;
                    if (d2 > (double) keepRange * keepRange) continue;
                    BlockState state = be.getCachedState();
                    Classification c = BlockClassifier.classify(state);
                    if (c == null && be instanceof Inventory) {
                        c = new Classification(ScanCategory.OTHER_STORAGE, state.getBlock().getName().getString(), null);
                    }
                    if (c == null || c.isSkip() || c.category() == null || !c.category().triggersArea) continue;
                    if (!cfg.isEnabled(c.category())) continue;
                    spots.add(new Spot(pos.toImmutable(), d2 <= (double) newRange * newRange));
                }
            }
        }

        List<Cluster> clusters = cluster(spots, cfg);
        reconcile(clusters, world, player, cfg, rescanAll);
    }

    private List<Cluster> cluster(List<Spot> spots, ModConfig cfg) {
        spots.sort(Comparator.comparingInt((Spot s) -> s.pos.getX()).thenComparingInt(s -> s.pos.getZ()));
        int size = cfg.areaSize;
        boolean limited = cfg.verticalMode == ModConfig.VerticalMode.LIMITED;
        int vr = cfg.verticalRange;
        List<Cluster> clusters = new ArrayList<>();

        for (Spot s : spots) {
            int x = s.pos.getX(), y = s.pos.getY(), z = s.pos.getZ();
            if (!cfg.mergeOverlapping) {
                // No merging: attach to an existing area only if the storage is already inside it.
                Cluster host = null;
                for (Cluster c : clusters) {
                    int half = size / 2;
                    int ax = c.anchors.get(0).getX(), az = c.anchors.get(0).getZ(), ay = c.anchors.get(0).getY();
                    boolean inside = x >= ax - half && x < ax - half + size && z >= az - half && z < az - half + size
                            && (!limited || Math.abs(y - ay) <= vr);
                    if (inside) {
                        host = c;
                        break;
                    }
                }
                if (host != null) {
                    host.anchors.add(s.pos);
                    host.anyNew |= s.withinNewRange;
                } else {
                    clusters.add(new Cluster(s));
                }
                continue;
            }

            List<Cluster> overlapping = new ArrayList<>();
            for (Cluster c : clusters) {
                // Two anchor squares of side `size` overlap when the anchors are less than `size` apart.
                boolean ox = x > c.minAX - size && x < c.maxAX + size;
                boolean oz = z > c.minAZ - size && z < c.maxAZ + size;
                boolean oy = !limited || (y >= c.minAY - 2 * vr && y <= c.maxAY + 2 * vr);
                if (ox && oz && oy) overlapping.add(c);
            }
            if (overlapping.isEmpty()) {
                clusters.add(new Cluster(s));
                continue;
            }
            // Try to merge everything that overlaps, as long as the merged area stays under the span cap.
            Cluster merged = new Cluster(s);
            for (Cluster c : overlapping) merged.absorb(c);
            if (span(merged, size) <= cfg.maxMergedSpan) {
                clusters.removeAll(overlapping);
                clusters.add(merged);
                continue;
            }
            // Otherwise join the first overlapping cluster that can still take it, or start a new one.
            boolean placed = false;
            for (Cluster c : overlapping) {
                int spanX = Math.max(c.maxAX, x) - Math.min(c.minAX, x) + size;
                int spanZ = Math.max(c.maxAZ, z) - Math.min(c.minAZ, z) + size;
                if (Math.max(spanX, spanZ) <= cfg.maxMergedSpan) {
                    c.absorb(s);
                    placed = true;
                    break;
                }
            }
            if (!placed) clusters.add(new Cluster(s));
        }
        return clusters;
    }

    private static int span(Cluster c, int size) {
        return Math.max(c.maxAX - c.minAX, c.maxAZ - c.minAZ) + size;
    }

    private void reconcile(List<Cluster> clusters, ClientWorld world, ClientPlayerEntity player, ModConfig cfg,
                           boolean rescanAll) {
        Vec3d p = player.getPos();
        int size = cfg.areaSize, half = size / 2;
        int bottom = world.getBottomY();
        int top = world.getBottomY() + world.getHeight() - 1;
        boolean limited = cfg.verticalMode == ModConfig.VerticalMode.LIMITED;

        clusters.sort(Comparator.comparingDouble(c -> {
            double cx = (c.minAX + c.maxAX) / 2.0 - p.x, cz = (c.minAZ + c.maxAZ) / 2.0 - p.z;
            return cx * cx + cz * cz;
        }));

        List<ScanArea> next = new ArrayList<>();
        Set<ScanArea> used = new HashSet<>();
        for (Cluster c : clusters) {
            if (next.size() >= cfg.maxAreas) break;
            int minX, maxX, minZ, maxZ;
            if (cfg.mergeOverlapping) {
                minX = c.minAX - half;
                maxX = c.maxAX - half + size - 1;
                minZ = c.minAZ - half;
                maxZ = c.maxAZ - half + size - 1;
            } else {
                BlockPos a0 = c.anchors.get(0);
                minX = a0.getX() - half;
                maxX = minX + size - 1;
                minZ = a0.getZ() - half;
                maxZ = minZ + size - 1;
            }
            int minY = limited ? Math.max(bottom, c.minAY - cfg.verticalRange) : bottom;
            int maxY = limited ? Math.min(top, c.maxAY + cfg.verticalRange) : top;

            ScanArea match = null;
            long bestOverlap = 0;
            for (ScanArea a : areas) {
                if (used.contains(a)) continue;
                long ix = Math.min(a.maxX, maxX) - Math.max(a.minX, minX) + 1;
                long iz = Math.min(a.maxZ, maxZ) - Math.max(a.minZ, minZ) + 1;
                boolean yOverlap = Math.min(a.maxY, maxY) >= Math.max(a.minY, minY);
                if (ix <= 0 || iz <= 0 || !yOverlap) continue;
                if (ix * iz > bestOverlap) {
                    bestOverlap = ix * iz;
                    match = a;
                }
            }
            if (match == null && !c.anyNew) continue; // only kept-range storage and no existing area: ignore

            ScanArea area = match;
            if (area == null) {
                area = new ScanArea(nextId++, tick);
                area.setBounds(minX, maxX, minZ, maxZ, minY, maxY);
                area.dirty = true;
            } else {
                used.add(area);
                if (!area.sameBounds(minX, maxX, minZ, maxZ, minY, maxY)) {
                    area.setBounds(minX, maxX, minZ, maxZ, minY, maxY);
                    area.job = null;
                    area.dirty = true;
                }
            }
            if (rescanAll) area.dirty = true;
            area.anchors.clear();
            area.anchors.addAll(c.anchors);
            area.anchorMinY = c.minAY;
            area.anchorMaxY = c.maxAY;
            next.add(area);
        }
        areas.clear();
        areas.addAll(next);
    }

    // ------------------------------------------------------------------ block scans

    private void scheduleAndRunBlockScans(ClientWorld world, ClientPlayerEntity player, ModConfig cfg) {
        if (areas.isEmpty()) return;
        List<ScanArea> order = sortedByDistance(player.getPos());
        for (ScanArea a : order) {
            if (a.job != null && !a.job.matches(a)) a.job = null;
            boolean periodic = cfg.autoScan && tick - a.lastBlockScanTick >= cfg.refreshIntervalTicks;
            if (a.job == null && (a.dirty || periodic)) {
                a.job = new ScanJob(a);
                a.dirty = false;
            }
        }
        int budget = cfg.blocksPerTick;
        for (ScanArea a : order) {
            if (budget <= 0) break;
            ScanJob job = a.job;
            if (job == null) continue;
            budget -= job.step(world, budget, cfg);
            if (job.isDone()) {
                boolean first = a.blockResult == null;
                a.blockResult = job.result();
                a.job = null;
                a.lastBlockScanTick = tick;
                if (a.entityResult == null) scanEntities(world, player, a, cfg);
                rescore(a, cfg);
                Alerts.onBlockScanFinished(a, first, tick);
            }
        }
    }

    private void rescore(ScanArea a, ModConfig cfg) {
        a.score = BaseScorer.compute(a, cfg.weights);
    }

    // ------------------------------------------------------------------ entity scans

    private void scanEntities(ClientWorld world, ClientPlayerEntity self, ScanArea a, ModConfig cfg) {
        Box box = a.box();
        AreaScanResult r = new AreaScanResult();
        for (Entity e : world.getOtherEntities(self, box)) {
            Classification c = EntityClassifier.classify(e);
            if (c == null || !cfg.isEnabled(c.category())) continue;
            long amount = e instanceof ItemEntity item ? item.getStack().getCount() : 1;
            r.add(c, e.getBlockPos(), e.getId(), amount);
            if (c.category() == ScanCategory.PLAYERS) Alerts.onPlayerSeen(a, c.typeName(), tick);
        }
        r.complete = true;
        a.entityResult = r;
        if (a.blockResult != null) rescore(a, cfg);
    }

    // ------------------------------------------------------------------ crosshair focus & auto-open

    private void updateLookFocus(MinecraftClient client, ClientWorld world, ModConfig cfg) {
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            lastAutoOpenPos = null;
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        BlockState state = world.getBlockState(pos);
        Classification c = BlockClassifier.classify(state);
        boolean storage = c != null && (c.isSkip() ? state.getBlock() instanceof ChestBlock
                : c.category() != null && c.category().isStorage());
        if (!storage) {
            lastAutoOpenPos = null;
            return;
        }
        ScanArea area = null;
        for (ScanArea a : areas) {
            if (a.contains(pos)) {
                area = a;
                break;
            }
        }
        if (area == null) return;
        lookAreaId = area.id;
        lookUntilTick = tick + LOOK_FOCUS_TICKS;

        if (cfg.autoOpenOnSelect && !cfg.interfaceHidden && client.currentScreen == null
                && (!cfg.autoOpenRequiresSneak || client.player.isSneaking())
                && !pos.equals(lastAutoOpenPos)) {
            lastAutoOpenPos = pos.toImmutable();
            client.setScreen(new AreaDetailScreen(null, area.id));
        }
    }
}
