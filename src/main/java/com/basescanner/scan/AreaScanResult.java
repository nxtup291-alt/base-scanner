package com.basescanner.scan;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Counts and positions gathered by one block scan or one entity scan of an area. */
public final class AreaScanResult {
    /** Maximum positions remembered per type (counts are always exact). */
    public static final int MAX_POSITIONS_PER_TYPE = 256;

    public static final class TypeEntry {
        public final String name;
        public int count;
        /** For dropped items: total item count. Otherwise equal to count. */
        public long amount;
        public final List<BlockPos> positions = new ArrayList<>();
        public final List<Integer> entityIds = new ArrayList<>();

        TypeEntry(String name) {
            this.name = name;
        }
    }

    public static final class CategoryResult {
        public final ScanCategory category;
        public int total;
        public final Map<String, TypeEntry> types = new LinkedHashMap<>();

        CategoryResult(ScanCategory category) {
            this.category = category;
        }

        /** Types sorted by count, largest first. */
        public List<TypeEntry> sortedTypes() {
            List<TypeEntry> list = new ArrayList<>(types.values());
            list.sort((a, b) -> Integer.compare(b.count, a.count));
            return list;
        }
    }

    public record SignInfo(BlockPos pos, List<String> lines) {
    }

    private final EnumMap<ScanCategory, CategoryResult> categories = new EnumMap<>(ScanCategory.class);
    private final Map<String, Integer> tagCounts = new HashMap<>();
    private final Map<String, Set<String>> tagTypes = new HashMap<>();
    public final List<SignInfo> signs = new ArrayList<>();

    public int totalColumns;
    public int loadedColumns;
    public long blocksExamined;
    public boolean complete;

    public void add(Classification c, BlockPos pos, int entityId, long amount) {
        CategoryResult cr = categories.computeIfAbsent(c.category(), CategoryResult::new);
        cr.total++;
        TypeEntry te = cr.types.computeIfAbsent(c.typeName(), TypeEntry::new);
        te.count++;
        te.amount += amount;
        if (te.positions.size() < MAX_POSITIONS_PER_TYPE) {
            te.positions.add(pos);
            if (entityId >= 0) te.entityIds.add(entityId);
        }
        if (c.scoreTag() != null) {
            tagCounts.merge(c.scoreTag(), 1, Integer::sum);
            tagTypes.computeIfAbsent(c.scoreTag(), k -> new LinkedHashSet<>()).add(c.typeName());
        }
    }

    public int total(ScanCategory c) {
        CategoryResult cr = categories.get(c);
        return cr == null ? 0 : cr.total;
    }

    public CategoryResult get(ScanCategory c) {
        return categories.get(c);
    }

    public int tag(String tag) {
        return tagCounts.getOrDefault(tag, 0);
    }

    /** Distinct type names that carried the given score tag. */
    public Set<String> tagTypes(String tag) {
        Set<String> set = tagTypes.get(tag);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public Collection<CategoryResult> all() {
        return categories.values();
    }

    public int textSigns() {
        return signs.size();
    }

    public int grandTotal() {
        int sum = 0;
        for (CategoryResult cr : categories.values()) sum += cr.total;
        return sum;
    }

    /** Fraction (0..1) of the requested columns that were in loaded chunks. */
    public double coverage() {
        return totalColumns == 0 ? 0 : (double) loadedColumns / totalColumns;
    }
}
