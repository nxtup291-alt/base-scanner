package com.basescanner.scan;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers the contents of containers that <b>you opened</b>. Minecraft does not
 * send container contents to the client until a container is opened, so this is
 * the only legitimate source for storage content summaries.
 */
public final class ContainerMemory {
    public record ItemCount(String name, int count) {
    }

    public record Snapshot(List<ItemCount> items, int totalItems, int usedSlots, int totalSlots, long seenTick) {
    }

    public record Summary(int knownContainers, int totalItems, List<ItemCount> topItems) {
    }

    private static final int MAX_ENTRIES = 4096;

    private static final Map<BlockPos, Snapshot> SNAPSHOTS = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, Snapshot> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private static BlockPos lastUsedPos;
    private static long lastUsedTick;
    private static long tick;

    private ContainerMemory() {
    }

    public static void clear() {
        SNAPSHOTS.clear();
        lastUsedPos = null;
    }

    /** Called when the local player right-clicks a block. */
    public static void onUseBlock(BlockPos pos) {
        lastUsedPos = pos.toImmutable();
        lastUsedTick = tick;
    }

    public static Snapshot get(BlockPos pos) {
        return SNAPSHOTS.get(pos);
    }

    public static void tick(MinecraftClient client) {
        tick++;
        if (lastUsedPos == null || client.world == null || client.player == null) return;
        if (tick - lastUsedTick > 200) {
            lastUsedPos = null;
            return;
        }
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) return;
        ScreenHandler handler = screen.getScreenHandler();
        if (handler instanceof PlayerScreenHandler) return;

        BlockState state = client.world.getBlockState(lastUsedPos);
        Classification c = BlockClassifier.classify(state);
        boolean storage = c != null && (c.isSkip() ? state.getBlock() instanceof ChestBlock
                : c.category() != null && c.category().isStorage());
        if (!storage) return;

        Map<String, Integer> counts = new HashMap<>();
        int total = 0, used = 0, slots = 0;
        for (Slot slot : handler.slots) {
            if (slot.inventory instanceof PlayerInventory) continue;
            slots++;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            used++;
            total += stack.getCount();
            counts.merge(stack.getName().getString(), stack.getCount(), Integer::sum);
        }
        if (slots == 0) return;

        List<ItemCount> items = new ArrayList<>();
        counts.forEach((k, v) -> items.add(new ItemCount(k, v)));
        items.sort((a, b) -> Integer.compare(b.count(), a.count()));
        Snapshot snap = new Snapshot(items, total, used, slots, tick);
        SNAPSHOTS.put(lastUsedPos, snap);

        // Store under both halves of a double chest so either position finds it.
        if (state.getBlock() instanceof ChestBlock && state.getProperties().contains(Properties.CHEST_TYPE)
                && state.get(Properties.CHEST_TYPE) != ChestType.SINGLE) {
            Direction toOther = ChestBlock.getFacing(state);
            SNAPSHOTS.put(lastUsedPos.offset(toOther), snap);
        }
    }

    /** Aggregates remembered contents for all storage positions in an area. */
    public static Summary summarize(ScanArea area) {
        AreaScanResult r = area.blockResult;
        if (r == null) return new Summary(0, 0, List.of());
        Map<String, Integer> agg = new HashMap<>();
        int known = 0, total = 0;
        for (ScanCategory cat : ScanCategory.values()) {
            if (!cat.isStorage() || cat.entity) continue;
            AreaScanResult.CategoryResult cr = r.get(cat);
            if (cr == null) continue;
            for (AreaScanResult.TypeEntry te : cr.types.values()) {
                for (BlockPos pos : te.positions) {
                    Snapshot s = SNAPSHOTS.get(pos);
                    if (s == null) continue;
                    known++;
                    total += s.totalItems();
                    for (ItemCount ic : s.items()) agg.merge(ic.name(), ic.count(), Integer::sum);
                }
            }
        }
        List<ItemCount> top = new ArrayList<>();
        agg.forEach((k, v) -> top.add(new ItemCount(k, v)));
        top.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return new Summary(known, total, top.size() > 8 ? top.subList(0, 8) : top);
    }
}
