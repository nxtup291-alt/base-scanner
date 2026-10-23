package com.basescanner.scan;

import com.basescanner.score.ScoreResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One scan area: the merged 20x20 (configurable) region around one or more storage blocks. */
public final class ScanArea {
    public final int id;
    /** Inclusive block bounds of the region that is scanned. */
    public int minX, maxX, minZ, maxZ, minY, maxY;
    /** Lowest and highest Y of the storage blocks that created this area (used for the outline band). */
    public int anchorMinY, anchorMaxY;
    public final List<BlockPos> anchors = new ArrayList<>();

    public AreaScanResult blockResult;
    public AreaScanResult entityResult;
    public ScoreResult score;
    public ScanJob job;

    public boolean dirty = true;
    public long lastBlockScanTick = Long.MIN_VALUE / 2;
    public final long createdTick;

    // alert bookkeeping
    public boolean announced;
    public boolean highScoreAlerted;
    public final Map<String, Long> playerAlertTicks = new HashMap<>();

    public ScanArea(int id, long createdTick) {
        this.id = id;
        this.createdTick = createdTick;
    }

    public void setBounds(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.minY = minY;
        this.maxY = maxY;
    }

    public boolean sameBounds(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        return this.minX == minX && this.maxX == maxX && this.minZ == minZ && this.maxZ == maxZ
                && this.minY == minY && this.maxY == maxY;
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public double centerX() {
        return (minX + maxX + 1) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) / 2.0;
    }

    public int centerY() {
        return (anchorMinY + anchorMaxY) / 2;
    }

    public boolean containsXZ(double x, double z) {
        return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ
                && pos.getY() >= minY && pos.getY() <= maxY;
    }

    /** Horizontal distance from a point to the nearest edge of the area (0 when inside). */
    public double horizontalDistance(Vec3d p) {
        double dx = Math.max(0, Math.max(minX - p.x, p.x - (maxX + 1)));
        double dz = Math.max(0, Math.max(minZ - p.z, p.z - (maxZ + 1)));
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double distanceToCenter(Vec3d p) {
        double dx = centerX() - p.x;
        double dz = centerZ() - p.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public Box box() {
        return new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    /** Scan progress of the running job in 0..1, or 1 when idle. */
    public double progress() {
        ScanJob j = job;
        return j == null ? 1.0 : j.progress();
    }

    public boolean isScanning() {
        return job != null;
    }

    public AreaScanResult resultFor(ScanCategory c) {
        return c.entity ? entityResult : blockResult;
    }

    public int total(ScanCategory c) {
        AreaScanResult r = resultFor(c);
        return r == null ? 0 : r.total(c);
    }

    public int storageCount() {
        return total(ScanCategory.CHESTS) + total(ScanCategory.SHULKERS) + total(ScanCategory.BARRELS)
                + total(ScanCategory.OTHER_STORAGE);
    }
}
